# FireSphere.ai — Database Backup & Disaster Recovery Guide

**Role:** Member 3 (Database, Infrastructure & DevOps)  
**Target Engine:** PostgreSQL 16+ with PostGIS 3.4+  
**Backup Format:** PostgreSQL Custom Format Archive (`.dump`, compression level 9)

---

## 1. Overview & Strategy

FireSphere.ai database backup and recovery operations follow a two-tier strategy:
1. **Point-in-Time Recovery (PITR) & Logical Snapshots:** Periodic logical backups taken using PostgreSQL-native `pg_dump` targeting the `firesphere` and `public` schemas.
2. **Object Storage Replication:** Completed backup archives are mirrored to the dedicated MinIO / S3 backup bucket (`firesphere-backups`).

---

## 2. Creating a Database Backup

### Using Bash (Linux / macOS / WSL)
```bash
# Creates a compressed snapshot in ./backups/firesphere_backup_YYYYMMDD_HHMMSS.dump
bash scripts/db_backup.sh
```

### Using PowerShell (Windows Native)
```powershell
# Creates a compressed snapshot in ./backups/firesphere_backup_YYYYMMDD_HHMMSS.dump
.\scripts\db_backup.ps1
```

### Output & Artifacts
- **Destination Directory:** `./backups/`
- **File Format:** PostgreSQL custom archive format (`.dump`). This format includes table metadata, constraints, PostGIS spatial data, and indexes in a gzip-compressed binary format that supports multithreaded restoration and selective table recovery.
- **Log File:** `./backups/firesphere_backup_<timestamp>.dump.log`

---

## 3. Restoring a Database Backup

### Safe Restoration Drill (Default Mode — Isolated Environment)
By default, the restore scripts restore into an isolated verification database (`firesphere_restore_test`) without touching active development data:
```bash
# Bash (Linux / macOS / WSL)
bash scripts/db_restore.sh ./backups/firesphere_backup_20261005_120000.dump

# PowerShell (Windows)
.\scripts\db_restore.ps1 -BackupFile ./backups/firesphere_backup_20261005_120000.dump
```

---

### Overwriting Live Database (Administrative / Disaster Recovery Mode)
> [!CAUTION]
> **DESTRUCTIVE OPERATION WARNING:**  
> Overwriting the live development/staging database replaces all data in `firesphere`. This requires explicit CLI flags and interactive typed confirmation:

```bash
# Bash (Prompts to type 'OVERWRITE')
bash scripts/db_restore.sh ./backups/firesphere_backup_20261005_120000.dump --production-overwrite

# Bash Non-Interactive (Automation)
bash scripts/db_restore.sh ./backups/firesphere_backup_20261005_120000.dump --production-overwrite --force
```

```powershell
# PowerShell (Prompts to type 'OVERWRITE')
.\scripts\db_restore.ps1 -BackupFile ./backups/firesphere_backup_20261005_120000.dump -ProductionOverwrite

# PowerShell Non-Interactive (Automation)
.\scripts\db_restore.ps1 -BackupFile ./backups/firesphere_backup_20261005_120000.dump -ProductionOverwrite -Force
```

---

## 4. Post-Restore Verification Checklist

After running a restoration script, verify database integrity with these queries:

```bash
docker compose exec -it postgres psql -U firesphere_admin -d firesphere
```

1. **Verify Table Count:**
   ```sql
   SELECT count(*) FROM information_schema.tables WHERE table_schema = 'firesphere';
   ```
2. **Verify PostGIS Extension:**
   ```sql
   SELECT PostGIS_Version();
   ```
3. **Verify Spatial Tables & GIST Indexes:**
   ```sql
   SELECT tablename, indexname FROM pg_indexes WHERE schemaname = 'firesphere' AND indexname LIKE '%location%';
   ```
4. **Verify Tenant Organizations:**
   ```sql
   SELECT id, code, name, status FROM firesphere.organizations;
   ```

---

## 5. Production Disaster Recovery Runbook (Target Architecture)

For staging and cloud production environments:
1. **Continuous WAL Archiving:** PostgreSQL `archive_mode = on` with Write-Ahead Logs pushed to an encrypted S3 bucket every 60 seconds.
2. **Daily Automated Full Snapshots:** Cron job executing `db_backup.sh` at 02:00 UTC daily, uploading to S3 with Object Lock (WORM - Write Once Read Many) enabled for 30-day compliance retention.
3. **Recovery Time Objective (RTO):** Under 15 minutes to re-provision container stack and restore snapshot.
4. **Recovery Point Objective (RPO):** Under 60 seconds via WAL replay.
