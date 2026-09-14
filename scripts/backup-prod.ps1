# callsagents/scripts/backup-prod.ps1
# Backup de la BD de Railway (producción) via túnel SSH.
# Requiere: railway CLI autenticado, pg_dump en PATH (o Docker).
#
# Uso:  .\scripts\backup-prod.ps1
# Salida: dump en ../backups/callsagents_prod_YYYYMMDD_HHmmss.dump
#
# Railway no incluye Database Backups en plan Hobby.
# Este script abre un túnel SSH al servicio Postgres de Railway y ejecuta pg_dump.

$ErrorActionPreference = "Stop"

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupDir = Join-Path $PSScriptRoot "..\backups"
$dumpFile  = Join-Path $backupDir "callsagents_prod_$timestamp.dump"

if (-not (Test-Path $backupDir)) {
    New-Item -ItemType Directory -Path $backupDir | Out-Null
    Write-Host "Directorio de backups creado: $backupDir"
}

$port = 15432  # puerto fijo para el túnel local
$railwayProjectId     = "5d126558-22c4-4955-b7b4-a57e6a61baf9"   # proyecto renewed-reverence
$railwayEnvironmentId = "d56cb7b3-96a9-4c39-99ac-ef46ae0ee66e"   # production
$railwayServiceId     = "09c8085c-f5fa-4adc-a50f-73677665acef"   # servicio Postgres

Write-Host "Abriendo túnel SSH a Postgres de Railway en puerto $port..."
$job = Start-Job -ScriptBlock {
    param($p, $projId, $envId, $svcId)
    $env:RAILWAY_PROJECT_ID     = $projId
    $env:RAILWAY_ENVIRONMENT_ID = $envId
    $env:RAILWAY_SERVICE_ID     = $svcId
    railway connect Postgres --tunnel-only --port $p
} -ArgumentList $port, $railwayProjectId, $railwayEnvironmentId, $railwayServiceId

try {
    # Esperar a que el túnel se establezca
    $tunnelUp = $false
    for ($i = 0; $i -lt 20; $i++) {
        Start-Sleep -Seconds 2
        try {
            $conn = Get-NetTCPConnection -LocalPort $port -ErrorAction Stop
            if ($conn) { $tunnelUp = $true; break }
        } catch {}
    }

    if (-not $tunnelUp) {
        Write-Host "ERROR: Túnel no se pudo establecer en puerto $port"
        $jobOutput = Receive-Job $job -Keep 2>&1
        Write-Host "Detalle del túnel: $jobOutput"
        exit 1
    }
    Write-Host "Túnel abierto en localhost:$port"

    # Detectar pg_dump (local o via Docker)
    $pgDump = Get-Command pg_dump.exe -ErrorAction SilentlyContinue
    $useDocker = $false
    if (-not $pgDump) {
        $useDocker = $true
        Write-Host "pg_dump local no encontrado, usando Docker (imagen postgres:18-alpine)..."
    }

    # Ejecutar pg_dump
    Write-Host "Descargando dump de producción..."
    if ($useDocker) {
        $containerName = "callsagents-pgdump-$timestamp"
        docker run --rm --name $containerName -v "${backupDir}:/backup" postgres:18-alpine `
            pg_dump -h host.docker.internal -p $port -U postgres -d railway -Fc -f "/backup/callsagents_prod_$timestamp.dump" 2>&1
        $exitCode = $LASTEXITCODE
    } else {
        & $pgDump.Source -h localhost -p $port -U postgres -d railway -Fc -f $dumpFile 2>&1
        $exitCode = $LASTEXITCODE
    }

    if ($exitCode -ne 0) {
        Write-Host "ERROR: pg_dump fallo con codigo $exitCode"
        $dockerDump = Join-Path $backupDir "callsagents_prod_$timestamp.dump"
        if (Test-Path $dockerDump) { Remove-Item $dockerDump -ErrorAction SilentlyContinue }
        exit $exitCode
    }

    if (Test-Path $dumpFile) {
        $sizeMB = [math]::Round((Get-Item $dumpFile).Length / 1MB, 2)
        Write-Host "Backup completado: $dumpFile ($sizeMB MB)"
    } else {
        $dockerDump = Join-Path $backupDir "callsagents_prod_$timestamp.dump"
        if (Test-Path $dockerDump) {
            $sizeMB = [math]::Round((Get-Item $dockerDump).Length / 1MB, 2)
            Write-Host "Backup completado: $dockerDump ($sizeMB MB)"
        } else {
            Write-Host "ERROR: dump no encontrado tras pg_dump"
            exit 1
        }
    }
}
finally {
    # Cerrar túnel siempre
    Write-Host "Cerrando túnel..."
    Stop-Job $job -ErrorAction SilentlyContinue | Out-Null
    Remove-Job $job -Force -ErrorAction SilentlyContinue | Out-Null
}