#!/usr/bin/env bash
# ==============================================================================
# FireSphere.ai — Multi-Service Infrastructure Healthcheck Script (Bash)
# Role: Member 3 (Database, Infrastructure & DevOps)
# Verifies: PostgreSQL 16+, PostGIS 3.4+, Redis 7, MinIO S3 API
# ==============================================================================

set -euo pipefail

# Load environment if .env exists
if [ -f .env ]; then
  # export variables from .env
  set -a
  source .env
  set +a
fi

POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
POSTGRES_PORT="${POSTGRES_EXTERNAL_PORT:-5432}"
POSTGRES_USER="${POSTGRES_USER:-firesphere_admin}"
POSTGRES_DB="${POSTGRES_DB:-firesphere}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-postgres_dev_password_change_me}"

REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_EXTERNAL_PORT:-6379}"
REDIS_PASSWORD="${REDIS_PASSWORD:-redis_dev_password_change_me}"

MINIO_HOST="${MINIO_HOST:-localhost}"
MINIO_PORT="${MINIO_EXTERNAL_PORT:-9000}"

echo "=============================================================================="
echo " FireSphere.ai — Checking Local Infrastructure Health..."
echo "=============================================================================="

# 1. Check PostgreSQL Connectivity & Version
echo -n "[1/4] Checking PostgreSQL connectivity ($POSTGRES_HOST:$POSTGRES_PORT)... "
if command -v psql >/dev/null 2>&1; then
  PGPASSWORD="$POSTGRES_PASSWORD" psql -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT 1;" >/dev/null 2>&1 || {
    echo "FAILED"
    echo "ERROR: Could not connect to PostgreSQL on $POSTGRES_HOST:$POSTGRES_PORT."
    exit 1
  }
  PG_VERSION=$(PGPASSWORD="$POSTGRES_PASSWORD" psql -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -A -c "SHOW server_version;")
  echo "OK (PostgreSQL $PG_VERSION)"
else
  # Use docker compose exec if psql is not installed on host
  docker compose exec -T postgres pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1 || {
    echo "FAILED"
    echo "ERROR: PostgreSQL is not ready inside container."
    exit 1
  }
  echo "OK (via Docker container)"
fi

# 2. Check PostGIS Extension
echo -n "[2/4] Checking PostGIS extension status... "
if command -v psql >/dev/null 2>&1; then
  POSTGIS_VER=$(PGPASSWORD="$POSTGRES_PASSWORD" psql -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -A -c "SELECT PostGIS_Full_Version();" 2>/dev/null || echo "")
  if [ -z "$POSTGIS_VER" ]; then
    echo "FAILED"
    echo "ERROR: PostGIS extension is not installed or enabled in database $POSTGRES_DB."
    exit 1
  fi
  echo "OK"
else
  docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -A -c "SELECT PostGIS_Version();" >/dev/null 2>&1 || {
    echo "FAILED"
    echo "ERROR: PostGIS extension is not enabled."
    exit 1
  }
  echo "OK (via Docker container)"
fi

# 3. Check Redis Connectivity & Auth
echo -n "[3/4] Checking Redis service ($REDIS_HOST:$REDIS_PORT)... "
if command -v redis-cli >/dev/null 2>&1; then
  REDIS_PING=$(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" -a "$REDIS_PASSWORD" ping 2>/dev/null || echo "")
  if [ "$REDIS_PING" != "PONG" ]; then
    echo "FAILED"
    echo "ERROR: Redis ping failed on $REDIS_HOST:$REDIS_PORT."
    exit 1
  fi
  echo "OK (PONG received)"
else
  docker compose exec -T redis redis-cli -a "$REDIS_PASSWORD" ping 2>/dev/null | grep -q "PONG" || {
    echo "FAILED"
    echo "ERROR: Redis is not responding."
    exit 1
  }
  echo "OK (via Docker container)"
fi

# 4. Check MinIO S3 API Health
echo -n "[4/4] Checking MinIO object storage (http://$MINIO_HOST:$MINIO_PORT/minio/health/live)... "
if command -v curl >/dev/null 2>&1; then
  HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "http://$MINIO_HOST:$MINIO_PORT/minio/health/live" || echo "000")
  if [ "$HTTP_STATUS" -ne 200 ]; then
    echo "FAILED (HTTP $HTTP_STATUS)"
    echo "ERROR: MinIO healthcheck failed on http://$MINIO_HOST:$MINIO_PORT."
    exit 1
  fi
  echo "OK (HTTP 200)"
else
  docker compose exec -T minio curl -f http://localhost:9000/minio/health/live >/dev/null 2>&1 || {
    echo "FAILED"
    echo "ERROR: MinIO is not healthy inside container."
    exit 1
  }
  echo "OK (via Docker container)"
fi

echo "=============================================================================="
echo " ALL INFRASTRUCTURE SERVICES HEALTHY & READY!"
echo "=============================================================================="
exit 0
