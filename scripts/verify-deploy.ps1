# Verify-and-deploy script for Callsagents
# =========================================
# Usage (from repo root):
#   .\scripts\verify-deploy.ps1              # full: tests -> local smoke -> deploy -> prod smoke
#   .\scripts\verify-deploy.ps1 -SkipDeploy  # verify only (tests + local smoke + prod health)
#
# What it guards (so we stop shipping broken builds):
#   1. Backend unit tests (mvn test) - must pass
#   2. Frontend production build (ng build) - must succeed
#   3. Required Railway variables present (backend + frontend) - aborts with a
#      clear list when missing (this catches GOOGLE_CLIENT_ID-style mistakes)
#   4. Local docker-compose smoke: health, login, calendar providers endpoint,
#      Google /start returns 200 + authorizeUrl, integrations list
#   5. Deploy via `railway up` (uploads CURRENT code - `railway redeploy` reuses
#      the old deployment, which is why fixes silently never made it to prod)
#   6. Post-deploy production smoke: same endpoint checks against Railway
#
# Any failure stops the script BEFORE the deploy. One deploy only.

param(
    [switch]$SkipDeploy
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$backendUrl = 'http://localhost:8080'
$prodBackend = 'https://callsagents-production.up.railway.app'
$prodFrontend = 'https://callsagents-frontend-production.up.railway.app'

# NOTE: V12 renamed the admin email to contact@script-9.com and V19 rotated its
# password. These are the CURRENT seed credentials (see migrations V12/V14/V19).
$adminEmail = 'contact@script-9.com'
$adminPassword = $env:CALLSAGENTS_ADMIN_PASSWORD
if ([string]::IsNullOrWhiteSpace($adminPassword)) {
    throw 'CALLSAGENTS_ADMIN_PASSWORD environment variable is required for the admin login smoke test (rotated by migration V19).'
}

# Variables REQUIRED on the Railway backend service.
$requiredBackendVars = @(
    'JWT_SECRET',
    'ENCRYPTION_KEY',
    'GOOGLE_CLIENT_ID',
    'GOOGLE_CLIENT_SECRET',
    'GOOGLE_REDIRECT_URI',
    'FRONTEND_BASE_URL',
    'RETELL_API_KEY'
)
# Variables REQUIRED on the Railway frontend service.
$requiredFrontendVars = @(
    'BACKEND_HOST',
    'BACKEND_PORT'
)

function Write-Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }
function Write-Ok($msg) { Write-Host "  OK: $msg" -ForegroundColor Green }
function Write-Fail($msg) { Write-Host "  FAIL: $msg" -ForegroundColor Red }

function Assert-ExitOk($ctx) {
    if ($LASTEXITCODE -ne 0) { throw "$ctx failed with exit code $LASTEXITCODE" }
}

function Get-LoginToken([string]$baseUrl) {
    $body = @{ email = $adminEmail; password = $adminPassword } | ConvertTo-Json
    $resp = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/auth/login" -ContentType 'application/json' -Body $body
    return $resp.accessToken
}

function Test-Api([string]$name, [scriptblock]$block) {
    try {
        & $block
        Write-Ok $name
    } catch {
        Write-Fail "$name - $($_.Exception.Message)"
        throw
    }
}

# ---------------------------------------------------------------- 1. Backend tests
Write-Step '1/6 Backend tests (mvn clean test)'
# NOTE: 'clean' is mandatory. Maven incremental compilation with Lombok
# silently produces classes without @Builder/@Getter, failing tests with
# "builder() is undefined" errors. The Railway Dockerfile also uses clean.
Push-Location "$root\backend"
try {
    mvn -q clean test 2>&1 | Out-Null
    Assert-ExitOk 'mvn clean test'
    Write-Ok 'Backend tests passed'
} finally { Pop-Location }

# ---------------------------------------------------------------- 2. Frontend build
Write-Step '2/6 Frontend production build'
Push-Location "$root\frontend"
try {
    npx ng build 2>&1 | Out-Null
    Assert-ExitOk 'ng build'
    Write-Ok 'Angular production build passed'
} finally { Pop-Location }

# ---------------------------------------------------------------- 3. Railway variables
Write-Step '3/6 Railway variables check'

function Get-RailwayVars([string]$service) {
    $raw = railway variables --service $service 2>&1 | Out-String
    return [regex]::Matches($raw, '([A-Z][A-Z0-9_]{2,})') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
}

function Assert-VarsPresent([string]$service, [string[]]$required) {
    $present = Get-RailwayVars $service
    $missing = $required | Where-Object { $_ -notin $present }
    if ($missing.Count -gt 0) {
        throw "Railway service '$service' is MISSING variables: $($missing -join ', '). Fix before deploying."
    }
    Write-Ok "Service '$service' has all required variables ($($required.Count))"
}

Assert-VarsPresent 'callsagents-backend' $requiredBackendVars
Assert-VarsPresent 'callsagents-frontend' $requiredFrontendVars

# ---------------------------------------------------------------- 4. Local smoke test
Write-Step '4/6 Local docker-compose smoke test'

docker compose up --build -d 2>&1 | Out-Null
Assert-ExitOk 'docker compose up --build -d'

# Wait for backend health (healthcheck may take up to 60s+ on first boot)
$healthy = $false
for ($i = 0; $i -lt 30; $i++) {
    try {
        $h = Invoke-RestMethod -Uri "$backendUrl/api/health" -TimeoutSec 5
        if ($null -ne $h) { $healthy = $true; break }
    } catch { Start-Sleep -Seconds 5 }
}
if (-not $healthy) { throw 'Local backend did not become healthy within 150s' }
Write-Ok 'Local backend healthy'

$token = Get-LoginToken $backendUrl
Write-Ok "Login as $adminEmail"

Test-Api 'GET /api/calendar/providers' {
    $providers = Invoke-RestMethod -Uri "$backendUrl/api/calendar/providers" -Headers @{ Authorization = "Bearer $token" }
    $google = $providers | Where-Object { $_.provider -eq 'GOOGLE' }
    if (-not $google -or -not $google.configured) {
        throw 'GOOGLE provider reported NOT configured locally - check .env GOOGLE_CLIENT_ID/SECRET'
    }
}

Test-Api 'GET /api/calendar/integrations' {
    Invoke-RestMethod -Uri "$backendUrl/api/calendar/integrations" -Headers @{ Authorization = "Bearer $token" } | Out-Null
}

Test-Api 'GET /api/calendar/integrations/google/start returns 200 + authorizeUrl' {
    $start = Invoke-RestMethod -Uri "$backendUrl/api/calendar/integrations/google/start" -Headers @{ Authorization = "Bearer $token" }
    if (-not $start.authorizeUrl -or $start.authorizeUrl -notmatch 'accounts.google.com') {
        throw 'authorizeUrl missing or invalid'
    }
    $redirect = [regex]::Match($start.authorizeUrl, 'redirect_uri=([^&]+)').Groups[1].Value
    Write-Ok "authorizeUrl built (redirect_uri=$redirect)"
}

# ---------------------------------------------------------------- 5. Deploy
if ($SkipDeploy) {
    Write-Step '5/6 SKIPPED (verification only)'
} else {
    Write-Step '5/6 Deploying to Railway (railway up - current code)'
    railway up --service callsagents-backend --detach 2>&1 | Out-Null
    Assert-ExitOk 'railway up backend'
    Write-Ok 'Backend deploy submitted'
    railway up --service callsagents-frontend --detach 2>&1 | Out-Null
    Assert-ExitOk 'railway up frontend'
    Write-Ok 'Frontend deploy submitted'

    # Wait for backend to come back online
    Start-Sleep -Seconds 90
    $prodUp = $false
    for ($i = 0; $i -lt 40; $i++) {
        try {
            $status = railway status 2>&1 | Out-String
            if ($status -match 'Building') { Start-Sleep -Seconds 15; continue }
            $h = Invoke-RestMethod -Uri "$prodBackend/api/health" -TimeoutSec 10
            $prodUp = $true; break
        } catch { Start-Sleep -Seconds 15 }
    }
    if (-not $prodUp) { throw 'Production backend did not become reachable after deploy' }
    Write-Ok 'Production backend reachable'
}

# ---------------------------------------------------------------- 6. Production smoke test
Write-Step '6/6 Production smoke test'

if (-not $SkipDeploy) {
    $prodToken = Get-LoginToken $prodBackend
    Write-Ok "Login as $adminEmail"

    Test-Api 'GET /api/calendar/providers (prod)' {
        $providers = Invoke-RestMethod -Uri "$prodBackend/api/calendar/providers" -Headers @{ Authorization = "Bearer $prodToken" }
        $google = $providers | Where-Object { $_.provider -eq 'GOOGLE' }
        if (-not $google -or -not $google.configured) {
            throw 'GOOGLE provider NOT configured in production'
        }
    }

    Test-Api 'GET /api/calendar/integrations/google/start (prod) returns 200' {
        $start = Invoke-RestMethod -Uri "$prodBackend/api/calendar/integrations/google/start" -Headers @{ Authorization = "Bearer $prodToken" }
        if (-not $start.authorizeUrl -or $start.authorizeUrl -notmatch 'accounts.google.com') {
            throw 'authorizeUrl missing or invalid'
        }
        $redirect = [regex]::Match($start.authorizeUrl, 'redirect_uri=([^&]+)').Groups[1].Value
        Write-Ok "redirect_uri=$redirect"
    }
}

Write-Host "`n==============================================" -ForegroundColor Green
Write-Host 'VERIFY + DEPLOY COMPLETED - all checks passed' -ForegroundColor Green
Write-Host '==============================================' -ForegroundColor Green