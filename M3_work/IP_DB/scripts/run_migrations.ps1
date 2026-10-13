# ==============================================================================
# FireSphere.ai — Flyway Migration Runner Script (PowerShell)
# Role: Member 3 (Database, Infrastructure & DevOps)
# ==============================================================================

[CmdletBinding()]
param(
    [string]$Action = "migrate"
)

$ErrorActionPreference = "Stop"

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host " FireSphere.ai — Running Flyway Migration: $Action (PowerShell)" -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan

docker compose run --rm flyway $Action

Write-Host "==============================================================================" -ForegroundColor Green
Write-Host " Flyway $Action execution finished." -ForegroundColor Green
Write-Host "==============================================================================" -ForegroundColor Green
