# callsagents/scripts/backup-local.ps1
# Backup local de la BD de desarrollo (PostgreSQL en Docker, localhost:5433).
# No sustituye backups de producción. Usar para recuperación rápida del entorno local.
#
# Uso: .\scripts\backup-local.ps1
# Salida: dump en ../backups/callsagents_dev_YYYYMMDD_HHmmss.dump

$ErrorActionPreference = "Stop"

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupDir = Join-Path $PSScriptRoot "..\backups"
$dumpFile  = Join-Path $backupDir "callsagents_dev_$timestamp.dump"

if (-not (Test-Path $backupDir)) {
    New-Item -ItemType Directory -Path $backupDir | Out-Null
    Write-Host "Directorio de backups creado: $backupDir"
}

$container = "callsagents-postgres"
$running = docker ps --format "{{.Names}}" | Select-String -Pattern "^$container$"
if (-not $running) {
    Write-Host "ERROR: contenedor '$container' no esta corriendo."
    Write-Host "       Arrancalo primero (docker compose up -d) y reintenta."
    exit 1
}

Write-Host "Respaldando BD local (localhost:5433/callsagents)..."
docker exec $container pg_dump -h localhost -p 5432 -U callsagents -d callsagents -Fc -f /tmp/callsagents.dump
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: pg_dump dentro del contenedor fallo con codigo $LASTEXITCODE"
    exit $LASTEXITCODE
}

docker cp "$($container):/tmp/callsagents.dump" $dumpFile | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: docker cp fallo con codigo $LASTEXITCODE"
    exit $LASTEXITCODE
}
docker exec $container rm -f /tmp/callsagents.dump

$sizeMB = [math]::Round((Get-Item $dumpFile).Length / 1MB, 2)
Write-Host "Backup completado: $dumpFile ($sizeMB MB)"