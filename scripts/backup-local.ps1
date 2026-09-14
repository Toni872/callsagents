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

$pgDump = Get-Command pg_dump.exe -ErrorAction SilentlyContinue
if (-not $pgDump) {
    Write-Host "ERROR: pg_dump.exe no encontrado. Instala PostgreSQL (psql) y añadelo al PATH."
    Write-Host "       https://www.postgresql.org/download/windows/"
    exit 1
}

Write-Host "Respaldando BD local (localhost:5433/callsagents)..."
& $pgDump.Source -h localhost -p 5433 -U callsagents -d callsagents -Fc -f $dumpFile 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: pg_dump fallo con codigo $LASTEXITCODE"
    exit $LASTEXITCODE
}

$sizeMB = [math]::Round((Get-Item $dumpFile).Length / 1MB, 2)
Write-Host "Backup completado: $dumpFile ($sizeMB MB)"
