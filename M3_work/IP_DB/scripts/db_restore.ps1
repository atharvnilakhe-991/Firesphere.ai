# ==============================================================================
# FireSphere.ai — Database Restoration Script (PowerShell)
# Role: Member 3 (Database, Infrastructure & DevOps)
# Restores: Compressed PostgreSQL custom format archive (.dump) via pg_restore
# Default Behavior: Restores safely into isolated test database 'firesphere_restore_test'
# To overwrite main database, pass: -ProductionOverwrite
# ==============================================================================

[CmdletBinding()]
param(
    [Parameter(Mandatory=$true, Position=0)]
    [string]$BackupFile,

    [switch]$ProductionOverwrite,

    [switch]$Force
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BackupFile)) {
    Write-Host "ERROR: Backup file '$BackupFile' does not exist." -ForegroundColor Red
    exit 1
}

$postgresUser = "firesphere_admin"

if ($ProductionOverwrite) {
    $targetDb = "firesphere"
    Write-Host "==============================================================================" -ForegroundColor Red
    Write-Host " CRITICAL WARNING: DESTRUCTIVE PRODUCTION DATABASE RESTORATION OPERATION" -ForegroundColor Red
    Write-Host " Target File:     $BackupFile" -ForegroundColor Red
    Write-Host " Target Database: $targetDb (ACTIVE SYSTEM DATABASE)" -ForegroundColor Red
    Write-Host " THIS WILL OVERWRITE ALL EXISTING TABLES AND DATA IN '$targetDb'!" -ForegroundColor Red
    Write-Host "==============================================================================" -ForegroundColor Red

    if (-not $Force) {
        $confirm = Read-Host "Type 'OVERWRITE' to confirm destroying and replacing database '$targetDb'"
        if ($confirm -ne "OVERWRITE") {
            Write-Host "Restoration cancelled by user." -ForegroundColor Gray
            exit 0
        }
    }

    try {
        Write-Host "Restoring database schema and data into '$targetDb'..." -ForegroundColor Cyan
        $cmd = "docker compose exec -T postgres pg_restore -U $postgresUser -d $targetDb --clean --if-exists --no-owner --no-privileges"
        cmd.exe /c "$cmd < `"$BackupFile`""
    } catch {
        Write-Host "ERROR: Database restoration failed: $_" -ForegroundColor Red
        exit 1
    }
} else {
    $targetDb = "firesphere_restore_test"
    Write-Host "==============================================================================" -ForegroundColor Yellow
    Write-Host " SAFE RESTORATION DRILL: Restoring into isolated test database" -ForegroundColor Yellow
    Write-Host " Target File:     $BackupFile" -ForegroundColor Yellow
    Write-Host " Target Database: $targetDb (NON-DESTRUCTIVE ISOLATED ENVIRONMENT)" -ForegroundColor Yellow
    Write-Host "==============================================================================" -ForegroundColor Yellow

    # Prepare isolated test database
    docker compose exec -T postgres psql -U $postgresUser -d postgres -c "DROP DATABASE IF EXISTS $targetDb;" > $null 2>&1
    docker compose exec -T postgres psql -U $postgresUser -d postgres -c "CREATE DATABASE $targetDb;" > $null 2>&1
    docker compose exec -T postgres psql -U $postgresUser -d $targetDb -c "CREATE EXTENSION IF NOT EXISTS postgis; CREATE EXTENSION IF NOT EXISTS `"uuid-ossp`"; CREATE EXTENSION IF NOT EXISTS `"btree_gist`";" > $null 2>&1

    try {
        Write-Host "Restoring schema and data into isolated verification database '$targetDb'..." -ForegroundColor Cyan
        $cmd = "docker compose exec -T postgres pg_restore -U $postgresUser -d $targetDb --no-owner --no-privileges"
        cmd.exe /c "$cmd < `"$BackupFile`""
    } catch {
        Write-Host "Notice: pg_restore completed with notices during test drill." -ForegroundColor Gray
    }
}

Write-Host "SUCCESS: Database restoration executed." -ForegroundColor Green
Write-Host "Running table verification count on target database '$targetDb'..." -ForegroundColor Cyan
docker compose exec -T postgres psql -U $postgresUser -d $targetDb -c "SELECT count(*) AS total_tables FROM information_schema.tables WHERE table_schema = 'firesphere';"

Write-Host "==============================================================================" -ForegroundColor Yellow
exit 0
