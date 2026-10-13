# Member 3 — Database + Infrastructure + DevOps Implementation Plan

## Ownership
PostgreSQL 16/PostGIS, Flyway, Redis, MinIO/S3, tenant isolation, audit infrastructure, backups, Docker, CI/CD and observability.

## Already completed/implemented scope to verify
- [x] PostgreSQL 16 + PostGIS
- [x] production database structure
- [x] PK/FK/constraints/indexes
- [x] UUID public IDs
- [x] spatial infrastructure
- [x] nearest-hydrant query
- [x] tenant RLS/fail-closed context
- [x] audit/change tracking and credential redaction
- [x] Flyway V1.0.0–V1.0.6
- [x] Dockerized PostgreSQL/PostGIS
- [x] Redis/MinIO persistence and health checks
- [x] backup/restore scripts/tests
- [x] DB validation tests/CI

## Remaining verification
- [ ] M2 HikariCP tenant context integration
- [ ] final AI persistence contract
- [ ] cross-member integration support

## Infrastructure checklist
### Database
- [ ] migration repeatability
- [ ] FK/constraint/index verification
- [ ] RLS tests
- [ ] cross-tenant isolation tests
- [ ] audit tests
- [ ] spatial tests

### Redis
- [ ] connection config
- [ ] persistence policy
- [ ] TTL/key namespace
- [ ] health checks
- [ ] failure behavior

### MinIO/S3
- [ ] bucket strategy
- [ ] tenant-prefixed storage
- [ ] evidence/document storage
- [ ] checksums
- [ ] access controls
- [ ] retention policy

### Backup/DR
- [ ] automated DB backup
- [ ] retention
- [ ] restore test
- [ ] object-storage backup strategy
- [ ] recovery documentation

### Docker/CI/CD
- [ ] Postgres/Redis/MinIO containers
- [ ] persistent volumes
- [ ] health checks
- [ ] environment configuration
- [ ] GitHub Actions
- [ ] build/test/migration/security checks
- [ ] deployment and rollback

### Observability/security
- [ ] structured logs
- [ ] metrics
- [ ] service health/readiness
- [ ] monitoring dashboards
- [ ] alerts
- [ ] secrets externalized
- [ ] least privilege
- [ ] HTTPS
- [ ] audit logging

## Handoff
- [ ] ERD
- [ ] migration list
- [ ] setup commands
- [ ] DB/Redis/MinIO variables
- [ ] backup/restore commands
- [ ] health-check commands
- [ ] known limitations
- [ ] completion report
