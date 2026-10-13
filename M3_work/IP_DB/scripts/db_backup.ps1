# ==============================================================================
# FireSphere.ai — Database Backup Script (PowerShell)
# Role: Member 3 (Database, Infrastructure & DevOps)
# Generates: Compressed PostgreSQL custom format archive (.dump)
# ==============================================================================

[CmdletBinding()]
param(
    [string]$BackupDir = "./backups"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupFile = Join-Path $BackupDir "firesphere_backup_${timestamp}.dump"

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host " FireSphere.ai — Initiating Database Backup (PowerShell)" -ForegroundColor Cyan
Write-Host " Target File: $backupFile" -ForegroundColor Cyan
Write-Host " Timestamp:   $timestamp" -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan

try {
    # Execute pg_dump inside container and redirect output
    $cmd = "docker compose exec -T postgres pg_dump -U firesphere_admin -d firesphere --format=custom --compress=9 --schema=firesphere --schema=public"
    cmd.exe /c "$cmd > `"$backupFile`""
    
    if ($LASTEXITCODE -eq 0 -and (Test-Path $backupFile)) {
        $fileSize = (Get-Item $backupFile).Length
        $sizeMB = [math]::Round($fileSize / 1MB, 2)
        Write-Host "SUCCESS: Backup completed successfully ($sizeMB MB)." -ForegroundColor Green
        Write-Host "Backup location: $backupFile" -ForegroundColor Green
    } else {
        Write-Host "ERROR: pg_dump exited with error code $LASTEXITCODE" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "ERROR: Database backup failed: $_" -ForegroundColor Red
    exit 1
}

Write-Host "==============================================================================" -ForegroundColor Cyan
exit 0
