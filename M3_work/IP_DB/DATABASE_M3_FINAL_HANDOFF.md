# FireSphere.ai — Member 3 Final Handoff & Contract-Integration Readiness

**Author:** Member 3 — Database, Infrastructure & DevOps  
**Date:** October 2026  
**Milestone:** Final M3 Independent Implementation Handoff  
**Status:** **HANDOFF READY — WAITING FOR CONTRACT INTEGRATION**

---

## 1. M3 Scope Completion

Member 3 has completed the independent implementation scope for the database and infrastructure foundation of FireSphere.ai. The physical stack is fully operational, hardened, reproducible, and ready for cross-member contract integration.

### Delivered Responsibilities:
- **PostgreSQL 16.2 & PostGIS 3.4:** Core relational and geospatial database running on Alpine container with spatial reference system WGS 84 (`SRID 4326`).
- **Flyway Database Migrations:** Versioned, immutable, forward-only migrations from `V1.0.0` through `V1.0.6`, plus repeatable reference seeds (`R__01`).
- **50-Table M3 Schema Boundary:** Exactly 50 business tables implemented under the `firesphere` schema, fully constrained and normalized.
- **PK, FK & Domain Constraints:** 100% surrogate primary key coverage (`BIGSERIAL` or composite junction PKs), foreign keys with cascading/nullification rules, and domain check constraints.
- **Universal External Identifiers:** 50/50 domain tables equipped with public UUIDv4 columns (`uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE`) for secure, non-enumerable API exposure.
- **Multi-Tenant Row-Level Security (RLS):** Kernel-enforced RLS policies across 47 tables with fail-closed security and super-admin bypass mechanisms.
- **Audit Hardening (CDC):** Automated database triggers capturing user context, client IP, before/after JSONB snapshots, column change diffs (`changed_fields TEXT[]`), credential redaction, and an immutability trigger preventing tampering.
- **Spatial Infrastructure:** PostGIS geometry types (`Point`, `Polygon`), 100% GiST spatial index coverage, and a high-performance k-Nearest Neighbor (KNN) stored function (`find_nearest_operational_hydrants`).
- **Redis 7 Alpine:** Operational transient caching, rate-limiting, and ephemeral coordination store with persistent volume and password protection.
- **MinIO S3-Compatible Storage:** Local multi-bucket S3 object storage with automated bucket provisioning (`mc`) and private bucket isolation.
- **Docker Compose Foundation:** Single-command local infrastructure orchestration (`postgres`, `redis`, `minio`, `minio-init`, `flyway`) with healthcheck probes and isolated networking.
- **Backup & Restore Tooling:** Automated backup generation and safety-hardened restore scripts targeting isolated verification environments (`firesphere_restore_test`) by default.
- **Database Test Harness:** 5 automated SQL test suites covering extensions, spatial indexing, relational integrity, audit triggers, and row-level security.
- **Continuous Integration (CI):** GitHub Actions workflow executing complete Flyway migration and all 5 verification test suites on every pull request.
- **Secondary FK Index Optimization:** 7 targeted B-tree indexes added in `V1.0.6` to accelerate joins, cascading checks, and tenant filtering.

---

## 2. Current Migration Chain

The Flyway migration chain in [database/migrations/](file:///d:/IP%20Folder/database/migrations) is strictly version-ordered, forward-only, and idempotent:

| Version | Migration File | Purpose & Responsibilities | Tables Created |
| :--- | :--- | :--- | :---: |
| **`V1.0.0`** | `V1.0.0__core_extensions_and_helpers.sql` | Installs database extensions (`uuid-ossp`, `postgis`, `btree_gist`, `pgcrypto`), creates the `firesphere` schema, installs the `update_updated_at_column()` trigger helper, and creates session tenant context helpers (`set_tenant_context()`, `current_org_id()`). | 0 |
| **`V1.0.1`** | `V1.0.1__core_tenancy_and_identities.sql` | Implements multi-tenant core entities and RBAC: `organizations`, `divisions`, `departments`, `users`, `roles`, `permissions`, `role_permissions`, `user_roles`. Resolves department/user circular dependency via deferred FK constraint. | 8 |
| **`V1.0.2`** | `V1.0.2__spatial_infrastructure_and_locations.sql` | Implements PostGIS spatial entities (`zones`, `beats`, `fire_stations`, `hydrants`, `water_sources`, `buildings`), GiST indexes, and the sub-5ms KNN stored function `find_nearest_operational_hydrants()`. | 6 |
| **`V1.0.3`** | `V1.0.3__operations_noc_and_inspections.sql` | Implements building sub-entities, Fire NOC subsystem, NBC inspection checklists, inspection workflows, and violation enforcement: 18 tables. | 18 |
| **`V1.0.4`** | `V1.0.4__emergency_incidents_fleet_and_audit.sql` | Implements 112 emergency incidents, incident evidence raw media metadata, fire fleet apparatus, logistics inventory, training courses, and the `audit_logs` CDC table with `firesphere_audit_trigger()`: 18 tables. | 18 |
| **`V1.0.5`** | `V1.0.5__enable_row_level_security.sql` | P0 Hardening: Activates Row-Level Security (`ENABLE ROW LEVEL SECURITY`) and installs `tenant_isolation_policy` across 47 tables; updates audit triggers to capture `app.current_user_id` and compute `changed_fields TEXT[]`; installs audit immutability trigger `trg_protect_audit_logs`. | 0 |
| **`V1.0.6`** | `V1.0.6__add_secondary_foreign_key_indexes.sql` | P1 Optimization: Adds 7 supplementary B-tree indexes on un-indexed foreign key and tenant filtering columns. | 0 |
| **`R__01`** | `R__01_seed_reference_data.sql` | Repeatable seed migration in [database/seeds/](file:///d:/IP%20Folder/database/seeds): Seeds municipal tenant (`PMC-FIRE`), 5 system roles, 9 core permissions, standard NOC categories, NBC checklist items, and initial courses. Idempotent via `ON CONFLICT DO NOTHING / UPDATE`. | 0 |

---

## 3. Current Schema Boundary

> **Current M3 production database scope contains exactly 50 business/domain tables.**

This 50-table boundary represents the currently implemented, verified, and controlled database schema in schema `firesphere`. 

### Explicit Boundary Statement:
- The larger 85-table or 91-table scope mentioned in earlier high-level source documents (`FireSphere_Database_Documentation_v2.xml`) has **NOT** been silently adopted.
- The 6 candidate tables from Appendix A lacking column data dictionaries (`inventory_categories`, `notification_templates`, `payments`, `purchase_order_items`, `purchase_orders`, `vendors`) have **NOT** been created.
- The AI persistence table (`incident_ai_analysis` or `ai_insights`) has **NOT** been created.
- Any expansion beyond these 50 tables requires explicit cross-member contract approval and formal change-control processing.

---

## 4. Tenant / RLS Contract

Multi-tenancy isolation is enforced at the database kernel level through PostgreSQL Row-Level Security (RLS). Backend integration (Member 2) must adhere to the following contract:

### 4.1 Required Session Context Variables
When an application worker borrows a connection from the pool to execute an authenticated request, it must set the session context variables:

```sql
-- Inject tenant context and super-admin flag
SELECT firesphere.set_tenant_context(:org_id, :is_super_admin);

-- Inject authenticated user context for audit tracking
SELECT set_config('app.current_user_id', :user_id, FALSE);
```

### 4.2 Fail-Closed Isolation Behavior
- If `app.current_org_id` is empty, unset, or invalid, all tenant-scoped queries evaluate to `FALSE`.
- Queries return **0 rows** for reads and throw violations or affect **0 rows** on writes.
- Application code will never see cross-tenant records by accident.

### 4.3 Super-Admin Behavior
- If `app.is_super_admin` is explicitly set to `'true'`, the `firesphere.is_super_admin()` helper function returns `TRUE`.
- RLS policies evaluate `org_id = firesphere.current_org_id() OR firesphere.is_super_admin()`, granting visibility across all tenant records for authorized cross-tenant administrative tasks.

### 4.4 RLS Policy Coverage
- **44 direct tenant tables:** Enforced via `org_id = firesphere.current_org_id() OR firesphere.is_super_admin()`.
- **1 root table (`organizations`):** Enforced via `id = firesphere.current_org_id() OR firesphere.is_super_admin()`.
- **2 junction/subordinate tables:**
  - `user_roles`: Protected via join to `users.org_id`.
  - `inspection_checklist_items`: Protected via join to `inspection_checklists.org_id`.
- **3 global shared taxonomy tables:** `permissions`, `role_permissions`, `noc_application_categories` remain open (shared platform lookup tables without tenant isolation).

### 4.5 Connection-Pooling & HikariCP Requirement (Backend Responsibility)
> [!CAUTION]
> **CRITICAL CONNECTION POOL SAFETY REQUIREMENT (M2 BACKEND):**  
> Database session variables set via `set_config(..., FALSE)` persist across queries on that pooled physical connection.  
> When using connection poolers like HikariCP in Spring Boot, the backend must ensure:
> 1. Tenant context is initialized on every request / checkout.
> 2. Session context is **cleared upon returning the connection to the pool** or in a servlet/web filter `finally` block:
> ```sql
> SELECT set_config('app.current_org_id', '', FALSE);
> SELECT set_config('app.is_super_admin', 'false', FALSE);
> SELECT set_config('app.current_user_id', '', FALSE);
> ```
> Failure to clear context risks accidental context leakage across subsequent requests sharing the pooled connection.

---

## 5. Audit Contract

The Change Data Capture (CDC) audit subsystem provides immutable, non-repudiable audit logging for high-value business entities.

### 5.1 Captured Entities
`firesphere_audit_trigger()` is actively attached to:
1. `firesphere.buildings`
2. `firesphere.noc_applications`
3. `firesphere.inspections`
4. `firesphere.violations`
5. `firesphere.incidents`

### 5.2 Recorded Audit Log Structure (`firesphere.audit_logs`)
Each mutation produces an immutable row in `firesphere.audit_logs`:
- `id BIGSERIAL PK`
- `table_name VARCHAR(100) NOT NULL`
- `record_id BIGINT NOT NULL`
- `action VARCHAR(10) NOT NULL` (`INSERT`, `UPDATE`, `DELETE`)
- `old_data JSONB` (null on INSERT)
- `new_data JSONB` (null on DELETE)
- `changed_fields TEXT[]` (array of column names modified on UPDATE)
- `user_id BIGINT` (populated from session `app.current_user_id`)
- `client_ip VARCHAR(45)` (populated from session `app.client_ip` if set, or client connection)
- `org_id BIGINT` (tenant context from row or session)
- `created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`

### 5.3 Diffing & Credential Redaction Behavior
- On `UPDATE` actions, the trigger dynamically calculates which columns changed and stores their names in `changed_fields TEXT[]`.
- Sensitive authentication credentials (specifically `password_hash`) are explicitly redacted and excluded from change diff recording to prevent password leakage into audit trails.

### 5.4 Audit Tamper Protection
- An immutability trigger `trg_protect_audit_logs` is attached to `firesphere.audit_logs`.
- Any direct attempt to `UPDATE`, `DELETE`, or `TRUNCATE` records in `firesphere.audit_logs` is immediately aborted with a PostgreSQL exception: `"Audit logs are immutable and cannot be updated, deleted, or truncated."`
- Modification privileges (`UPDATE`, `DELETE`, `TRUNCATE`) are explicitly revoked from `PUBLIC`.

### 5.5 Application Obligation
To achieve full audit traceability, backend services must supply `app.current_user_id` on every transactional database operation.

---

## 6. File Storage Contract

File storage is decoupled from the relational database using an S3-compatible object storage architecture.

### 6.1 Storage Environment
- **Development / Local Testing:** MinIO running in Docker Compose (`minio:9000` API, `localhost:9001` console).
- **Production Architecture:** S3-compatible managed object store (AWS S3, Cloudflare R2, or Ceph).
- **Provisioned Buckets:**
  1. `firesphere-documents`: Building architectural plans, occupancy certificates, NOC legal filings.
  2. `firesphere-photos`: Building photos, fire safety equipment photos, inspection site images.
  3. `firesphere-evidence`: Incident recordings, field evidence, responder media.
  4. `firesphere-backups`: Nightly database custom dump archives.
- **Bucket Privacy:** Buckets are provisioned with `mc anonymous set none` (strictly private, zero unrestricted public access).

### 6.2 Relational Metadata vs Binary Content
- The database **NEVER** stores binary file blobs.
- Database tables store file metadata and S3 object keys:
  - `firesphere.building_documents`: `(file_name, file_path, file_size_bytes, mime_type, file_hash_sha256)`
  - `firesphere.incident_evidence`: `(evidence_type, file_path, file_hash_sha256, mime_type, location Point)`
- Where `file_path` contains the relative S3 object key (e.g. `evidence/org_1/incident_42/uuid.jpg`).

### 6.3 Pre-Signed URL Protocol
- Clients (M1 Mobile/Web) upload and download media via short-lived pre-signed S3 URLs (PUT / GET, 15-minute TTL) issued by backend services (M2).
- Large media payloads never pass directly through the Spring Boot application memory or database connection.

---

## 7. Redis Contract

Redis 7 Alpine is deployed as a high-performance in-memory cache and transient coordination layer.

### 7.1 Approved Redis Responsibilities
1. **Response Caching:** Ephemeral caching of static lookup taxonomies (NBC occupancy codes, NOC categories, role permissions).
2. **Rate Limiting:** Sliding-window rate limit counters for public API endpoints and mobile auth attempts.
3. **Session & Token Invalidation:** Distributed denylist for revoked JWT access tokens and temporary MFA verification nonces.
4. **Transient Work Queues:** Ephemeral job coordination and pub/sub notification dispatch between services.

### 7.2 Non-Authoritative System of Record
- **CRITICAL RULE:** Redis is **NOT** the authoritative persistence store.
- Any data written to Redis must be recreateable from PostgreSQL or acceptable to lose upon cache eviction/restart.
- The Redis instance is configured with `maxmemory 256mb` and eviction policy `allkeys-lru`.

---

## 8. Backup / Restore Contract

A robust, reproducible backup and restore procedure is engineered and verified.

### 8.1 Backup Mechanism
- Managed by [scripts/db_backup.sh](file:///d:/IP%20Folder/scripts/db_backup.sh) and [scripts/db_backup.ps1](file:///d:/IP%20Folder/scripts/db_backup.ps1).
- Uses `pg_dump` with custom compressed format (`-Fc --compress=9`).
- Backs up both `firesphere` and `public` schemas.
- Produces timestamped archives in `./backups/firesphere_backup_<TIMESTAMP>.dump`.

### 8.2 Safe Restore Drill Architecture
- Managed by [scripts/db_restore.sh](file:///d:/IP%20Folder/scripts/db_restore.sh) and [scripts/db_restore.ps1](file:///d:/IP%20Folder/scripts/db_restore.ps1).
- **Default Target:** To prevent catastrophic operational accidents, restore scripts restore into an isolated test verification database (`firesphere_restore_test`) by default.
- **Production Overwrite Protection:** Overwriting the active system database (`firesphere`) requires:
  1. Passing the explicit `--production-overwrite` (Bash) or `-ProductionOverwrite` (PowerShell) flag.
  2. Typing the confirmation word `"OVERWRITE"` interactively (unless `--force` is provided).

### 8.3 CI Validation
- Database creation, extension loading, migration execution, and verification suites are validated automatically on GitHub Actions CI ([.github/workflows/db-validation.yml](file:///d:/IP%20Folder/.github/workflows/db-validation.yml)).

### 8.4 Production Safety Requirements & Remaining Limitations
- In production, local custom dumps must be replicated to offsite cloud storage (`firesphere-backups` S3 bucket).
- Continuous Point-in-Time Recovery (PITR) will require enabling Write-Ahead Log (WAL) archiving in production cloud deployment.

---

## 9. Index Optimization

Migration `V1.0.6__add_secondary_foreign_key_indexes.sql` implemented exactly 7 secondary B-tree indexes identified during the forensic schema audit:

1. `idx_dispatches_station_id` on `firesphere.incident_dispatches(dispatched_station_id)`
   - Accelerates turnout and dispatch history queries filtered or joined by fire station.
2. `idx_dispatches_user_id` on `firesphere.incident_dispatches(dispatched_by_user_id)`
   - Accelerates dispatcher lookup and operational officer audit queries.
3. `idx_veh_fitness_user_id` on `firesphere.vehicle_fitness(inspected_by_user_id)`
   - Accelerates vehicle fitness certification logs and maintenance auditing by inspector.
4. `idx_veh_fuel_user_id` on `firesphere.vehicle_fuel_logs(recorded_by_user_id)`
   - Accelerates fuel log queries, cost reconciliation, and operator logging history.
5. `idx_training_courses_org_id` on `firesphere.training_courses(org_id)`
   - Accelerates multi-tenant training course catalog lookups and RLS tenant filtering.
6. `idx_building_equipment_org_id` on `firesphere.building_fire_equipment(org_id)`
   - Accelerates tenant-wide life safety equipment compliance audits and RLS evaluation without requiring full joins to `buildings`.
7. `idx_building_risk_org_id` on `firesphere.building_risk_profiles(org_id)`
   - Accelerates municipal high-risk building prioritization rankings and tenant-scoped analytics dashboards.

*Note: No further indexes have been added. Total indexes across the 50 tables stand at 85 B-tree indexes + 8 GiST spatial indexes = 93 total indexes.*

---

## 10. Integration Dependencies Owned by Other Members

Clear separation of responsibilities must be maintained across all five project members:

### M2 — Backend Engineering
- **Connection & Session Management:** Implement HikariCP connection checkout/checkin interceptor executing `set_tenant_context()` and session clearing upon completion.
- **JPA / Hibernate Entity Mapping:** Map existing 50 tables in Spring Boot. Set `spring.jpa.hibernate.ddl-auto=validate`. **Never** use `ddl-auto=update`.
- **Pre-Signed URL Generation:** Issue authenticated pre-signed PUT/GET URLs for MinIO/S3 file operations.
- **Transaction Handling:** Ensure transactions set `app.current_user_id` before inserting or updating audited tables.

### M4 — AI / ML & Computer Vision
- **Unresolved AI Persistence Contract:** The database currently does **NOT** contain an `ai_insights` or `incident_ai_analysis` table.
- **No Speculative Schema:** M3 will not author speculative AI tables without an approved contract.
- **Requirement:** Member 4 and Member 2 must agree upon and submit a formal schema contract for AI inference outputs (specifying fields, confidence scores, bounding box format, and model versioning). Once signed off, M3 will generate the corresponding migration.
- **Media Ingestion:** Member 4 services can read raw evidence media from MinIO buckets using the object keys stored in `incident_evidence.file_path`.

### M1 — Frontend & Mobile Engineering
- **Zero Direct Database Access:** Web and mobile applications must **NEVER** connect directly to PostgreSQL (port 5432) or Redis (port 6379). All data access is mediated through Backend REST/WebSocket APIs.
- **Direct S3 Streaming:** Stream photos and video evidence directly to MinIO via pre-signed URLs obtained from Backend APIs.
- **Client UUID Generation:** Mobile offline sync must generate UUIDv4 identifiers locally to allow offline record creation without primary key collisions.

### M5 — Operations, GIS & Integrations
- **Spatial Stored Function Usage:** Utilize `firesphere.find_nearest_operational_hydrants(:org_id, :lng, :lat, :limit)` for high-performance hydrant locator queries.
- **Spatial Operators:** Use PostGIS SQL spatial operators (`ST_Contains`, `ST_DWithin`, KNN `<->`) for station turnout jurisdictions and route geofencing.
- **Apparatus Telemetry:** Stream vehicle GPS updates into `firesphere.vehicle_location_history` using WGS 84 (`SRID 4326`) coordinates.

---

## 11. Open Decisions / Blockers

The following Architectural Decision Records (ADRs) reflect implemented assumptions that remain formally open awaiting multi-stakeholder sign-off:

| ADR ID | Decision Title | Implemented Architecture in Repository | Formal Approval Status | Required Approvers |
| :--- | :--- | :--- | :---: | :--- |
| **ADR-004** | Target Schema Scope (85 vs 91 Tables) | Implemented clean baseline of 50 core tables; 6 Appendix A tables omitted due to missing column specifications. | `UNRESOLVED` | Platform Architect & Project Lead |
| **ADR-005** | Incident AI Persistence (`incident_ai_analysis`) | Completely blocked; zero speculative tables created. Documented in [DATABASE_AI_SCHEMA_BLOCKER.md](file:///d:/IP%20Folder/DATABASE_AI_SCHEMA_BLOCKER.md). | `BLOCKED` | Member 4 (AI Lead) & Platform Architect |
| **ADR-006** | Tenant Key Standard (`organization_id UUID` vs `org_id BIGINT`) | Dual-key pattern implemented: `org_id BIGINT` for internal relational FKs and RLS; `uuid UUID` for public exposure. | `UNRESOLVED` | Platform Architect & Member 2 |
| **ADR-007** | Department / User Circular FK Deadlock | Implemented `DEFERRABLE INITIALLY DEFERRED` constraint on `departments.head_user_id` and nullable `users.department_id`. | `UNRESOLVED` | Member 2 & Platform Architect |
| **ADR-008** | `incident_evidence` Scope & Semantics | Implemented strictly as a raw media store (MinIO S3 key, MIME, SHA-256 hash, GPS location). | `UNRESOLVED` | Member 4 & Member 2 |
| **ADR-009** | SSO Entity Canonical Naming | Implemented canonical name `firesphere.sso_identities`. | `UNRESOLVED` | Member 2 & Platform Architect |
| **ADR-010** | Audit Log Table Canonical Naming | Implemented canonical name `firesphere.audit_logs`. | `UNRESOLVED` | Member 2 & Platform Architect |
| **ADR-011** | Universal Public External UUIDv4 Standard | Implemented `uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE` on all 50 tables. | `UNRESOLVED` | Platform Architect & Member 2 |
| **ADR-012** | PostGIS Spatial Conventions & Geometry Types | Implemented SRID 4326 (WGS 84), `Point`/`Polygon` geometries, and GiST indexes across all 7 spatial tables. | `UNRESOLVED` | Member 5 & Member 2 |
| **ADR-013** | Clean Break vs Legacy Data Migration | Implemented pristine schema with clean reference seed data (`PMC-FIRE`); dirty legacy MariaDB exports quarantined. | `UNRESOLVED` | Project Lead & Member 2 |

---

## 12. M3 Change-Control Rule

> **Future schema changes must enter through an approved requirement/contract, Data Dictionary review where applicable, impact analysis, Flyway migration, migration testing, and integration testing.**

> **Other members must not directly modify the shared production schema outside the M3 migration process.**

Any schema modifications requested by Members 1, 2, 4, or 5 must be submitted as a formal change request to Member 3 as defined in [DATABASE_MIGRATION_GUIDE.md](file:///d:/IP%20Folder/DATABASE_MIGRATION_GUIDE.md).

---

## 13. Handoff Checklist

### M3 Completed Deliverables:
- [x] Schema implemented (50 domain tables in `firesphere` schema)
- [x] Flyway migrations complete and ordered (`V1.0.0` through `V1.0.6`)
- [x] Constraints, PKs, FKs, and cascade behaviors enforced
- [x] Universal UUIDv4 public identifiers on all 50 tables
- [x] Indexes implemented (93 total: 85 B-tree + 8 GiST spatial)
- [x] Row-Level Security active across 47 tables with fail-closed security
- [x] Audit CDC triggers, diff calculation, and tamper protection trigger installed
- [x] Spatial PostGIS infrastructure (SRID 4326, GiST indexes, KNN function)
- [x] Redis 7 Alpine infrastructure configured and healthchecked
- [x] MinIO S3-compatible storage with 4 provisioned buckets
- [x] Backup and safe isolated restore drill scripts verified
- [x] CI/CD pipeline and 5 automated database test suites
- [x] V1.0.6 secondary foreign key index optimizations applied
- [x] Authoritative documentation, contracts, and migration guides updated

### Pending Downstream Cross-Member Integration:
- [ ] M2 backend integration (HikariCP session context, JPA validation, REST endpoints)
- [ ] M4 AI persistence contract specification and sign-off
- [ ] Cross-member integration testing (M1 + M2 + M4 + M5 against M3 stack)
- [ ] Final system-wide end-to-end release validation

---

## 14. M3 Stop Point

> **M3 independent schema/infrastructure implementation is complete for the currently approved 50-table scope. Further database changes require either an approved contract change, an identified defect, or an explicit integration requirement.**
