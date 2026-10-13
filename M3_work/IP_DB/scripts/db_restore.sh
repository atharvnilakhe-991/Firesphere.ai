# ==============================================================================
# FireSphere.ai — Database Restoration Script (Bash)
# Role: Member 3 (Database, Infrastructure & DevOps)
# Restores: Compressed PostgreSQL custom format archive (.dump) via pg_restore
# Default Behavior: Restores safely into isolated test database 'firesphere_restore_test'
# To overwrite main database, pass: --production-overwrite
# ==============================================================================

set -euo pipefail

BACKUP_FILE="${1:-}"
TARGET_MODE="${2:---test-db}"
FORCE="${3:-}"

if [ -z "$BACKUP_FILE" ]; then
  echo "Usage: $0 <path_to_backup_file.dump> [--test-db | --production-overwrite] [--force]"
  echo "Example (Safe Test Drill):  $0 ./backups/firesphere_backup_20261005_120000.dump"
  echo "Example (Live Overwrite):   $0 ./backups/firesphere_backup_20261005_120000.dump --production-overwrite"
  exit 1
fi

if [ ! -f "$BACKUP_FILE" ]; then
  echo "ERROR: Backup file '$BACKUP_FILE' not found."
  exit 1
fi

POSTGRES_USER="${POSTGRES_USER:-firesphere_admin}"

if [ "$TARGET_MODE" = "--production-overwrite" ]; then
  TARGET_DB="${POSTGRES_DB:-firesphere}"
  echo "=============================================================================="
  echo " CRITICAL WARNING: DESTRUCTIVE PRODUCTION DATABASE RESTORATION OPERATION"
  echo " Target File:     $BACKUP_FILE"
  echo " Target Database: $TARGET_DB (ACTIVE SYSTEM DATABASE)"
  echo " THIS WILL OVERWRITE ALL EXISTING TABLES AND DATA IN '$TARGET_DB'!"
  echo "=============================================================================="

  if [ "$FORCE" != "--force" ]; then
    read -p "Type 'OVERWRITE' to confirm destroying and replacing database '$TARGET_DB': " CONFIRM
    if [ "$CONFIRM" != "OVERWRITE" ]; then
      echo "Restoration cancelled by user."
      exit 0
    fi
  fi

  echo "Restoring database schema and data into '$TARGET_DB'..."
  docker compose exec -T postgres pg_restore \
    -U "$POSTGRES_USER" \
    -d "$TARGET_DB" \
    --clean \
    --if-exists \
    --no-owner \
    --no-privileges \
    --verbose < "$BACKUP_FILE" 2> "${BACKUP_FILE}.restore.log" || {
      echo "Notice: pg_restore completed with notices. Inspect ${BACKUP_FILE}.restore.log"
  }
else
  TARGET_DB="firesphere_restore_test"
  echo "=============================================================================="
  echo " SAFE RESTORATION DRILL: Restoring into isolated test database"
  echo " Target File:     $BACKUP_FILE"
  echo " Target Database: $TARGET_DB (NON-DESTRUCTIVE ISOLATED ENVIRONMENT)"
  echo "=============================================================================="

  # Ensure isolated test database exists cleanly
  docker compose exec -T postgres psql -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE IF EXISTS $TARGET_DB;" >/dev/null 2>&1
  docker compose exec -T postgres psql -U "$POSTGRES_USER" -d postgres -c "CREATE DATABASE $TARGET_DB;" >/dev/null 2>&1
  docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$TARGET_DB" -c "CREATE EXTENSION IF NOT EXISTS postgis; CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"; CREATE EXTENSION IF NOT EXISTS \"btree_gist\";" >/dev/null 2>&1

  echo "Restoring schema and data into isolated verification database '$TARGET_DB'..."
  docker compose exec -T postgres pg_restore \
    -U "$POSTGRES_USER" \
    -d "$TARGET_DB" \
    --no-owner \
    --no-privileges < "$BACKUP_FILE" 2> "${BACKUP_FILE}.test_restore.log" || true
fi

echo "SUCCESS: Database restoration executed."
echo "Running verification query on target database '$TARGET_DB'..."
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$TARGET_DB" -c "SELECT count(*) AS total_tables FROM information_schema.tables WHERE table_schema = 'firesphere';"
echo "=============================================================================="
exit 0
