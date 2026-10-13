# FireSphere.ai — Local Development Environment Setup Guide

**Role:** Member 3 (Database, Infrastructure & DevOps)  
**Target Audience:** All Team Members (M1 Frontend/Mobile, M2 Backend, M4 AI/ML, M5 Operations/GIS)  
**Estimated Setup Time:** Under 5 minutes

---

## 1. Prerequisites

Before starting, ensure your local workstation has:
1. **Docker Desktop** (version 24.0+ or Docker Engine with Docker Compose v2).
2. **Git** for repository version control.
3. *(Optional)* Native PostgreSQL client (`psql`), Redis CLI (`redis-cli`), or AWS CLI for direct host interaction.

---

## 2. Quickstart (One-Command Setup)

Follow these 4 simple steps to stand up the complete local infrastructure:

### Step 1: Copy Environment Configuration
```bash
# On Linux/macOS
cp .env.example .env

# On Windows PowerShell
Copy-Item .env.example .env
```

### Step 2: Start Infrastructure Stack
```bash
docker compose up -d
```
*This starts:*
- **PostgreSQL 16 + PostGIS 3.4** (Port `5432`)
- **Redis 7** (Port `6379`)
- **MinIO S3 Storage** (Port `9000` API, Port `9001` Web Console)
- **MinIO Bucket Initializer** (creates default buckets: `firesphere-documents`, `firesphere-photos`, `firesphere-evidence`, `firesphere-backups`)

### Step 3: Run Database Migrations
```bash
# On Linux/macOS
bash scripts/run_migrations.sh

# On Windows PowerShell
.\scripts\run_migrations.ps1

# Or directly via Docker Compose
docker compose run --rm flyway migrate
```

### Step 4: Verify Infrastructure Health
```bash
# On Linux/macOS
bash scripts/db_healthcheck.sh

# On Windows PowerShell
.\scripts\db_healthcheck.ps1
```

---

## 3. Service Connection Details

### PostgreSQL 16 + PostGIS 3.4
- **Host:** `localhost` (from host machine) or `postgres` (within Docker network)
- **Port:** `5432`
- **Database:** `firesphere`
- **Default Schema:** `firesphere`
- **Username:** `firesphere_admin`
- **Password:** `postgres_dev_password_change_me`
- **JDBC Connection URL:** `jdbc:postgresql://localhost:5432/firesphere`
- **Interactive Shell:**
  ```bash
  docker compose exec -it postgres psql -U firesphere_admin -d firesphere
  ```

### Redis 7 (Cache, Rate Limiting & Queue Coordination)
- **Host:** `localhost` (from host machine) or `redis` (within Docker network)
- **Port:** `6379`
- **Password:** `redis_dev_password_change_me`
- **Connection URI:** `redis://:redis_dev_password_change_me@localhost:6379/0`
- **Interactive CLI:**
  ```bash
  docker compose exec -it redis redis-cli -a redis_dev_password_change_me
  ```

### MinIO (S3-Compatible Object Storage)
- **S3 API Endpoint:** `http://localhost:9000` (within Docker: `http://minio:9000`)
- **Web Console URL:** [http://localhost:9001](http://localhost:9001)
- **Root User / Access Key:** `minio_dev_admin`
- **Root Password / Secret Key:** `minio_dev_password_change_me`
- **Pre-Created Buckets:**
  - `firesphere-documents`: Inspection PDF reports, architectural drawings, NOC files.
  - `firesphere-photos`: Geotagged inspection and building photos.
  - `firesphere-evidence`: Emergency incident audio recordings, video extracts, and drone footage.
  - `firesphere-backups`: Compressed database snapshots.

---

## 4. Development Operations & Maintenance

### Stopping the Stack
```bash
# Stop containers without removing persistent data
docker compose stop

# Restart stopped containers
docker compose start
```

### Resetting Development Database (Clean Slate)
To wipe all local tables, data, and cache and re-run migrations from scratch:
```bash
# WARNING: This deletes all data in the postgres_data, redis_data, and minio_data volumes!
docker compose down -v

# Re-launch and re-migrate
docker compose up -d
docker compose run --rm flyway migrate
```

### Running Validation Tests
```bash
# On Linux/macOS
bash scripts/validate_foundation.sh

# On Windows PowerShell
.\scripts\validate_foundation.ps1
```
