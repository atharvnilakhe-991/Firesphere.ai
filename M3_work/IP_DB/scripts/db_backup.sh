#!/usr/bin/env bash
# ==============================================================================
# FireSphere.ai — Database Backup Script (Bash)
# Role: Member 3 (Database, Infrastructure & DevOps)
# Generates: Compressed PostgreSQL custom format archive (.dump)
# ==============================================================================

set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-./backups}"
mkdir -p "$BACKUP_DIR"

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILENAME="firesphere_backup_${TIMESTAMP}.dump"
BACKUP_PATH="${BACKUP_DIR}/${BACKUP_FILENAME}"

POSTGRES_USER="${POSTGRES_USER:-firesphere_admin}"
POSTGRES_DB="${POSTGRES_DB:-firesphere}"

echo "=============================================================================="
echo " FireSphere.ai — Initiating Database Backup"
echo " Target File: $BACKUP_PATH"
echo " Timestamp:   $TIMESTAMP"
echo "=============================================================================="

# Execute pg_dump inside container
docker compose exec -T postgres pg_dump \
  -U "$POSTGRES_USER" \
  -d "$POSTGRES_DB" \
  --format=custom \
  --compress=9 \
  --schema=firesphere \
  --schema=public \
  --verbose > "$BACKUP_PATH" 2> "${BACKUP_PATH}.log" || {
    echo "ERROR: Database backup failed. Inspect log: ${BACKUP_PATH}.log"
    exit 1
}

# Verify backup size
BACKUP_SIZE=$(ls -lh "$BACKUP_PATH" | awk '{print $5}')
echo "SUCCESS: Backup completed successfully ($BACKUP_SIZE)."
echo "Backup location: $BACKUP_PATH"
echo "=============================================================================="
exit 0
