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

Write-Host "Abriendo túnel SSH a Postgres de Railway en puerto $port..."
# railway connect --tunnel-only necesita el nombre del servicio de BD
$proc = Start-Process -FilePath "railway.exe" `
    -ArgumentList "connect", "postgres", "--tunnel-only", "--port", $port `
    -NoNewWindow -PassThru -RedirectStandardOutput "$env:TEMP\railway_tunnel.log"

Start-Sleep -Seconds 6  # esperar a que el túnel se establezca

# Verificar que el túnel está activo
$tunnelUp = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
if (-not $tunnelUp) {
    Write-Host "ERROR: Túnel no se pudo establecer en puerto $port"
    Write-Host "Log: $(Get-Content "$env:TEMP\railway_tunnel.log" -ErrorAction SilentlyContinue)"
    Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
    exit 1
}
Write-Host "Túnel abierto en localhost:$port"

# Detectar pg_dump (local o via Docker)
$pgDump = Get-Command pg_dump.exe -ErrorAction SilentlyContinue
$useDocker = $false
if (-not $pgDump) {
    $useDocker = $true
    Write-Host "pg_dump local no encontrado, usando Docker..."
}

# Ejecutar pg_dump
Write-Host "Descargando dump de producción..."
if ($useDocker) {
    docker run --rm --network host -v "${backupDir}:/backup" postgres:16-alpine `
        pg_dump -h host.docker.internal -p $port -U postgres -d railway -Fc -f "/backup/callsagents_prod_$timestamp.dump" 2>&1
} else {
    & $pgDump.Source -h localhost -p $port -U postgres -d railway -Fc -f $dumpFile 2>&1
}

$exitCode = $LASTEXITCODE

# Cerrar túnel
Write-Host "Cerrando túnel..."
Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue

if ($exitCode -ne 0) {
    Write-Host "ERROR: pg_dump fallo con codigo $exitCode"
    exit $exitCode
}

if (Test-Path $dumpFile) {
    $sizeMB = [math]::Round((Get-Item $dumpFile).Length / 1MB, 2)
    Write-Host "Backup completado: $dumpFile ($sizeMB MB)"
} else {
    # Si usamos Docker con volumen, buscar en backupDir
    $dockerDump = Join-Path $backupDir "callsagents_prod_$timestamp.dump"
    if (Test-Path $dockerDump) {
        $sizeMB = [math]::Round((Get-Item $dockerDump).Length / 1MB, 2)
        Write-Host "Backup completado: $dockerDump ($sizeMB MB)"
    } else {
        Write-Host "ERROR: dump no encontrado tras pg_dump"
        exit 1
    }
}
