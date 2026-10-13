# ==============================================================================
# FireSphere.ai — Multi-Service Infrastructure Healthcheck Script (PowerShell)
# Role: Member 3 (Database, Infrastructure & DevOps)
# Verifies: PostgreSQL 16+, PostGIS 3.4+, Redis 7, MinIO S3 API
# ==============================================================================

[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host " FireSphere.ai — Checking Local Infrastructure Health (PowerShell)..." -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan

# 1. PostgreSQL Check
Write-Host -NoNewline "[1/4] Checking PostgreSQL container status... "
try {
    $pgCheck = docker compose exec -T postgres pg_isready -U firesphere_admin -d firesphere
    if ($LASTEXITCODE -eq 0) {
        Write-Host "OK ($pgCheck)" -ForegroundColor Green
    } else {
        Write-Host "FAILED" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "ERROR: Could not execute pg_isready inside container: $_" -ForegroundColor Red
    exit 1
}

# 2. PostGIS Extension Check
Write-Host -NoNewline "[2/4] Checking PostGIS extension in database... "
try {
    $postgisVer = docker compose exec -T postgres psql -U firesphere_admin -d firesphere -t -A -c "SELECT PostGIS_Version();"
    if ($LASTEXITCODE -eq 0 -and $postgisVer -match "\d+\.\d+") {
        Write-Host "OK (PostGIS $postgisVer)" -ForegroundColor Green
    } else {
        Write-Host "FAILED" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "ERROR: Failed to query PostGIS: $_" -ForegroundColor Red
    exit 1
}

# 3. Redis Ping Check
Write-Host -NoNewline "[3/4] Checking Redis service ping... "
try {
    $redisPing = docker compose exec -T redis redis-cli -a redis_dev_password_change_me ping
    if ($redisPing -match "PONG") {
        Write-Host "OK (PONG received)" -ForegroundColor Green
    } else {
        Write-Host "FAILED ($redisPing)" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "ERROR: Redis check failed: $_" -ForegroundColor Red
    exit 1
}

# 4. MinIO S3 API Health Check
Write-Host -NoNewline "[4/4] Checking MinIO object storage live probe... "
try {
    $response = Invoke-WebRequest -Uri "http://localhost:9000/minio/health/live" -UseBasicParsing -TimeoutSec 5
    if ($response.StatusCode -eq 200) {
        Write-Host "OK (HTTP 200)" -ForegroundColor Green
    } else {
        Write-Host "FAILED (HTTP $($response.StatusCode))" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "ERROR: MinIO endpoint unreachable: $_" -ForegroundColor Red
    exit 1
}

Write-Host "==============================================================================" -ForegroundColor Green
Write-Host " ALL INFRASTRUCTURE SERVICES HEALTHY & READY!" -ForegroundColor Green
Write-Host "==============================================================================" -ForegroundColor Green
exit 0
