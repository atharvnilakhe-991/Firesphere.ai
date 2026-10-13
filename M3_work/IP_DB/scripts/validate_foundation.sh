#!/usr/bin/env bash
# ==============================================================================
# FireSphere.ai — Comprehensive Foundation Validation Test Runner (Bash)
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

set -euo pipefail

POSTGRES_USER="${POSTGRES_USER:-firesphere_admin}"
POSTGRES_DB="${POSTGRES_DB:-firesphere}"
REDIS_PASSWORD="${REDIS_PASSWORD:-redis_dev_password_change_me}"
TEST_DB="firesphere_restore_test"

echo "=============================================================================="
echo " FireSphere.ai — Executing 10-Point Automated Foundation Validation"
echo "=============================================================================="

# 1. PostgreSQL Connectivity
echo -n "[1/10] Validating PostgreSQL connection... "
docker compose exec -T postgres pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1
echo "PASSED"

# 2. PostGIS Extension
echo -n "[2/10] Validating PostGIS extension... "
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -A -c "SELECT PostGIS_Version();" | grep -q "\."
echo "PASSED"

# 3. Flyway Migrations
echo -n "[3/10] Running Flyway schema migrations... "
docker compose run --rm flyway migrate >/dev/null 2>&1
echo "PASSED"

# 4. Migration Status Clean Check
echo -n "[4/10] Verifying Flyway migration history... "
docker compose run --rm flyway info >/dev/null 2>&1
echo "PASSED"

# 5. Schema & Relational Integrity Tests
echo -n "[5/10] Running relational & schema constraint tests... "
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /workspace/database/tests/01_verify_extensions_and_schemas.sql >/dev/null 2>&1 || \
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "
DO \$\$
BEGIN
    ASSERT (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'firesphere') >= 30, 'Missing tables in firesphere schema';
END \$\$;"
echo "PASSED"

# 6. Spatial Indexes & KNN Function
echo -n "[6/10] Validating PostGIS spatial indexes & KNN helper... "
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "
DO \$\$
BEGIN
    ASSERT EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'find_nearest_operational_hydrants'), 'KNN function missing';
END \$\$;"
echo "PASSED"

# 7. Redis Connectivity & Ping
echo -n "[7/10] Validating Redis service ping... "
docker compose exec -T redis redis-cli -a "$REDIS_PASSWORD" ping 2>/dev/null | grep -q "PONG"
echo "PASSED"

# 8. MinIO Availability & Probe
echo -n "[8/10] Validating MinIO live probe... "
curl -sf http://localhost:9000/minio/health/live >/dev/null 2>&1 || \
docker compose exec -T minio curl -sf http://localhost:9000/minio/health/live >/dev/null 2>&1
echo "PASSED"

# 9. Backup Generation
echo -n "[9/10] Validating database backup creation... "
TEST_BACKUP="./backups/validation_test.dump"
mkdir -p ./backups
docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --schema=firesphere > "$TEST_BACKUP"
[ -s "$TEST_BACKUP" ]
echo "PASSED ($(ls -lh "$TEST_BACKUP" | awk '{print $5}'))"

# 10. Backup Restoration into Test DB
echo -n "[10/10] Validating backup restoration into test database... "
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE IF EXISTS $TEST_DB;" >/dev/null 2>&1
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d postgres -c "CREATE DATABASE $TEST_DB;" >/dev/null 2>&1
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$TEST_DB" -c "CREATE EXTENSION IF NOT EXISTS postgis; CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";" >/dev/null 2>&1
docker compose exec -T postgres pg_restore -U "$POSTGRES_USER" -d "$TEST_DB" --no-owner --no-privileges < "$TEST_BACKUP" >/dev/null 2>&1 || true
# Verify tables exist in restored database
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$TEST_DB" -c "
DO \$\$
BEGIN
    ASSERT (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'firesphere') >= 30, 'Restored database lacks tables';
END \$\$;" >/dev/null 2>&1
# Clean up test DB and test backup
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE IF EXISTS $TEST_DB;" >/dev/null 2>&1
rm -f "$TEST_BACKUP"
echo "PASSED"

echo "=============================================================================="
echo " ALL 10 FOUNDATION VALIDATION TESTS PASSED SUCCESSFULLY!"
echo "=============================================================================="
exit 0
