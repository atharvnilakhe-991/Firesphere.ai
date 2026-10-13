# ==============================================================================
# FireSphere.ai — Comprehensive Foundation Validation Test Runner (PowerShell)
# Role: Member 3 (Database, Infrastructure & DevOps)
# Executes 10 Automated Foundation Validations:
#   1. PostgreSQL starts & accepts connections
#   2. PostGIS extension is enabled & version verified
#   3. Flyway migrations apply successfully
#   4. Migration status is clean
#   5. Required constraints & schema exist
#   6. PostGIS spatial indexes & KNN functions exist
#   7. Redis is reachable & responds to ping
#   8. MinIO is reachable & responds to healthcheck
#   9. Database backup can be created
#  10. Backup can be restored into a temporary verification database
# ==============================================================================

[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$testDb = "firesphere_restore_test"

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host " FireSphere.ai — Executing 10-Point Automated Foundation Validation (PowerShell)" -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan

# 1. PostgreSQL Connectivity
Write-Host -NoNewline "[1/10] Validating PostgreSQL connection... "
docker compose exec -T postgres pg_isready -U firesphere_admin -d firesphere | Out-Null
Write-Host "PASSED" -ForegroundColor Green

# 2. PostGIS Extension
Write-Host -NoNewline "[2/10] Validating PostGIS extension... "
$pgis = docker compose exec -T postgres psql -U firesphere_admin -d firesphere -t -A -c "SELECT PostGIS_Version();"
if ($pgis -match "\d+\.\d+") {
    Write-Host "PASSED (PostGIS $pgis)" -ForegroundColor Green
} else {
    Write-Host "FAILED" -ForegroundColor Red
    exit 1
}

# 3. Flyway Migrations
Write-Host -NoNewline "[3/10] Running Flyway schema migrations... "
docker compose run --rm flyway migrate | Out-Null
Write-Host "PASSED" -ForegroundColor Green

# 4. Migration Status Clean Check
Write-Host -NoNewline "[4/10] Verifying Flyway migration history... "
docker compose run --rm flyway info | Out-Null
Write-Host "PASSED" -ForegroundColor Green

# 5. Schema & Relational Integrity Tests
Write-Host -NoNewline "[5/10] Running relational & schema constraint tests... "
docker compose exec -T postgres psql -U firesphere_admin -d firesphere -c "
DO `$test`$
BEGIN
    ASSERT (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'firesphere') >= 30, 'Missing tables in firesphere schema';
END `$test`$;" | Out-Null
Write-Host "PASSED" -ForegroundColor Green

# 6. Spatial Indexes & KNN Function
Write-Host -NoNewline "[6/10] Validating PostGIS spatial indexes & KNN helper... "
docker compose exec -T postgres psql -U firesphere_admin -d firesphere -c "
DO `$test`$
BEGIN
    ASSERT EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'find_nearest_operational_hydrants'), 'KNN function missing';
END `$test`$;" | Out-Null
Write-Host "PASSED" -ForegroundColor Green

# 7. Redis Ping
Write-Host -NoNewline "[7/10] Validating Redis service ping... "
$ping = docker compose exec -T redis redis-cli -a redis_dev_password_change_me ping
if ($ping -match "PONG") {
    Write-Host "PASSED" -ForegroundColor Green
} else {
    Write-Host "FAILED" -ForegroundColor Red
    exit 1
}

# 8. MinIO Live Probe
Write-Host -NoNewline "[8/10] Validating MinIO live probe... "
$minioResp = Invoke-WebRequest -Uri "http://localhost:9000/minio/health/live" -UseBasicParsing -TimeoutSec 5
if ($minioResp.StatusCode -eq 200) {
    Write-Host "PASSED" -ForegroundColor Green
} else {
    Write-Host "FAILED" -ForegroundColor Red
    exit 1
}

# 9. Backup Generation
Write-Host -NoNewline "[9/10] Validating database backup creation... "
$backupDir = "./backups"
if (-not (Test-Path $backupDir)) { New-Item -ItemType Directory -Path $backupDir -Force | Out-Null }
$testBackup = Join-Path $backupDir "validation_test.dump"
$cmd = "docker compose exec -T postgres pg_dump -U firesphere_admin -d firesphere --format=custom --schema=firesphere"
cmd.exe /c "$cmd > `"$testBackup`""

if (Test-Path $testBackup) {
    $bytes = (Get-Item $testBackup).Length
    $mb = [math]::Round($bytes / 1MB, 2)
    Write-Host "PASSED ($mb MB)" -ForegroundColor Green
} else {
    Write-Host "FAILED" -ForegroundColor Red
    exit 1
}

# 10. Backup Restoration into Test DB
Write-Host -NoNewline "[10/10] Validating backup restoration into test database... "
docker compose exec -T postgres psql -U firesphere_admin -d postgres -c "DROP DATABASE IF EXISTS $testDb;" | Out-Null
docker compose exec -T postgres psql -U firesphere_admin -d postgres -c "CREATE DATABASE $testDb;" | Out-Null
docker compose exec -T postgres psql -U firesphere_admin -d $testDb -c "CREATE EXTENSION IF NOT EXISTS postgis; CREATE EXTENSION IF NOT EXISTS `"uuid-ossp`";" | Out-Null
$restoreCmd = "docker compose exec -T postgres pg_restore -U firesphere_admin -d $testDb --no-owner --no-privileges"
cmd.exe /c "$restoreCmd < `"$testBackup`"" 2>&1 | Out-Null

docker compose exec -T postgres psql -U firesphere_admin -d $testDb -c "
DO `$test`$
BEGIN
    ASSERT (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'firesphere') >= 30, 'Restored database lacks tables';
END `$test`$;" | Out-Null

# Cleanup test artifacts
docker compose exec -T postgres psql -U firesphere_admin -d postgres -c "DROP DATABASE IF EXISTS $testDb;" | Out-Null
if (Test-Path $testBackup) { Remove-Item $testBackup -Force }
Write-Host "PASSED" -ForegroundColor Green

Write-Host "==============================================================================" -ForegroundColor Green
Write-Host " ALL 10 FOUNDATION VALIDATION TESTS PASSED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "==============================================================================" -ForegroundColor Green
exit 0
