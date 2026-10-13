# FireSphere.ai — Member 3: Database, Infrastructure & DevOps

## Mission
Own the authoritative physical database and reproducible infrastructure foundation.

## Owns
- PostgreSQL + PostGIS
- Data Dictionary reconciliation
- PK/FK/constraints/indexes
- spatial indexes
- Flyway migrations
- approved seed/reference data
- tenant database controls where designed
- Redis infrastructure
- MinIO/S3 development storage
- Docker/dev environments
- CI/CD foundation
- backup/restore procedure
- database health/performance

## Critical Rule
Other members do not directly alter the shared schema. Schema changes are requested, reviewed and implemented by M3.

## Data Dictionary Flags
Resolve before final migrations:
- duplicate `incident_evidence`
- ambiguous `incident_ai_analysis`
- incident tables with missing descriptions

Do not invent meanings.

## Database Principles
Use normalized PostgreSQL structures, explicit constraints, appropriate indexes, tenant keys and PostGIS geometry types such as `GEOMETRY(Point,4326)` and `GEOMETRY(Polygon,4326)` where approved.

## Migration Flow
```text
Requirement → Schema Proposal → Data Dictionary Check
→ Impact Analysis → Flyway Migration → Migration Test
→ Backend Mapping → Integration Test
```

Never manually alter production schema outside versioned migrations.

## Redis
Use for cache, rate limiting and approved background-job coordination. Never as authoritative persistence.

## Object Storage
MinIO in development; S3-compatible managed storage in production. PostgreSQL stores metadata and object keys. Use authorization and signed URLs.

## Environments
Maintain separate development, testing, staging and production configuration and credentials. No secrets in repository.

## CI/CD
Build, tests, migration validation, static/security checks where configured, controlled deployment.

## Backup
Automated backups, PITR where supported, object versioning/durability, restore testing and DR documentation.

## Definition of Done
Schema reviewed; migration added; constraints/indexes/tenant isolation considered; backend mapping communicated; tests pass; no secrets; documentation updated.

## Antigravity Rule
Read existing migrations, schema, Data Dictionary and Database MD. Never invent tables/columns or blindly replace an existing schema. Every change uses Flyway. Stop on conflicts.
