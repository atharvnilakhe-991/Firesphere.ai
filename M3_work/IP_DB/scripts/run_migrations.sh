#!/usr/bin/env bash
# ==============================================================================
# FireSphere.ai — Flyway Migration Runner Script (Bash)
# Role: Member 3 (Database, Infrastructure & DevOps)
# ==============================================================================

set -euo pipefail

ACTION="${1:-migrate}"

echo "=============================================================================="
echo " FireSphere.ai — Running Flyway Migration: $ACTION"
echo "=============================================================================="

# Use Dockerized Flyway to ensure exact version compatibility across all developers
docker compose run --rm flyway "$ACTION"

echo "=============================================================================="
echo " Flyway $ACTION execution finished."
echo "=============================================================================="
