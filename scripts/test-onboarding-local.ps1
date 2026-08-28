# Test simplificado - solo API, sin docker compose
$BASE = "http://localhost:8080/api"

Write-Host "=== Test Onboarding Wizard ===" -ForegroundColor Cyan

# 1. Health check
Write-Host "`n[1] Health check..." -ForegroundColor Yellow
try {
    $h = Invoke-RestMethod -Uri "http://localhost:8080/api/health" -Method Get
    Write-Host "  OK: $h" -ForegroundColor Green
} catch {
    Write-Host "  FALLO: Backend no responde en localhost:8080" -ForegroundColor Red
    Write-Host "  Ejecuta: docker compose up -d" -ForegroundColor Yellow
    exit 1
}

# 2. Login
Write-Host "`n[2] Login..." -ForegroundColor Yellow
try {
    $adminPassword = $env:CALLSAGENTS_ADMIN_PASSWORD
    if ([string]::IsNullOrWhiteSpace($adminPassword)) {
        throw 'CALLSAGENTS_ADMIN_PASSWORD environment variable is required (rotated by migration V19).'
    }
    $body = @{ email = "contact@script-9.com"; password = $adminPassword } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$BASE/auth/login" -Method Post -ContentType "application/json" -Body $body
    $tok = $r.accessToken
    Write-Host "  OK: Token obtenido" -ForegroundColor Green
} catch {
    Write-Host "  FALLO: Login" -ForegroundColor Red
    exit 1
}
$h2 = @{ "Authorization" = "Bearer $tok" }

# 3. Obtener profile
Write-Host "`n[3] Obteniendo business profile..." -ForegroundColor Yellow
try {
    $p = Invoke-RestMethod -Uri "$BASE/business/profile" -Method Get -Headers $h2
    $uid = $p.data.id
    Write-Host "  OK: Profile existe (userId: $uid)" -ForegroundColor Green
    Write-Host "  Company: $($p.data.companyName)" -ForegroundColor White
    Write-Host "  Bot: $($p.data.botName)" -ForegroundColor White
    Write-Host "  WhatsApp: $($p.data.whatsappNumber)" -ForegroundColor White
} catch {
    Write-Host "  No hay profile. Creando..." -ForegroundColor Yellow
    $body = @{
        companyName = "Script9"
        industry = "marketing"
        services = "Social media, branding, web"
        botName = "Naiara"
        tone = "friendly"
        greeting = "Hola! Soy Naiara de Script9. En que puedo ayudarte?"
        chatColor = "#25D366"
        whatsappNumber = "34687723287"
    } | ConvertTo-Json
    $p = Invoke-RestMethod -Uri "$BASE/business/profile" -Method Post -Headers $h2 -Body $body
    $uid = $p.data.id
    Write-Host "  OK: Profile creado (userId: $uid)" -ForegroundColor Green
}

# 4. Chat con businessId
Write-Host "`n[4] Chat con businessId..." -ForegroundColor Yellow
$body = @{ sessionId = "test-$(Get-Random)"; message = "Hola"; businessId = $uid } | ConvertTo-Json
try {
    $c = Invoke-RestMethod -Uri "http://localhost:8080/api/chat/message" -Method Post -ContentType "application/json" -Body $body
    Write-Host "  OK: $($c.response)" -ForegroundColor Green
} catch {
    Write-Host "  FALLO: $_" -ForegroundColor Red
}

# 5. Widget config publico
Write-Host "`n[5] Widget config (publico)..." -ForegroundColor Yellow
try {
    $w = Invoke-RestMethod -Uri "$BASE/business/profile/widget-config/$uid" -Method Get
    Write-Host "  OK: Bot=$($w.data.botName) Color=$($w.data.chatColor)" -ForegroundColor Green
} catch {
    Write-Host "  FALLO: $_" -ForegroundColor Red
}

# 6. Abrir widget
Write-Host "`n[6] Abriendo widget..." -ForegroundColor Yellow
$url = "http://localhost:80/widget?businessId=$uid"
Start-Process $url
Write-Host "  OK: $url" -ForegroundColor Green

Write-Host "`n=== TODO LISTO ===" -ForegroundColor Cyan
Write-Host "  userId: $uid" -ForegroundColor White
Write-Host "  widget: $url" -ForegroundColor White
