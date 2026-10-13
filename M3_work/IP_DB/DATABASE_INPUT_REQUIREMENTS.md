# FireSphere.ai — Database Input Requirements & Contract Audit Matrix

**Document Purpose**: Enumerates all external inputs, technical specifications, and architectural approvals required before the physical PostgreSQL + PostGIS database schema can be designed, reviewed, and implemented via Flyway migrations.  
**Role**: Member 3 (Database, Infrastructure & DevOps)  
**Status**: ACTIVE — AWAITING INPUTS  
**Date**: 2026-10-05  

---

## A. Required from Project Lead / Architecture

| # | Item | Current Status | Impact on Member 3 Responsibilities |
|---|------|----------------|-------------------------------------|
| A.1 | **Authoritative Data Dictionary** | `MISSING — REQUIRED INPUT` | Cannot define tables, column names, nullability, or types without this master source. |
| A.2 | **Approved Table & Entity Definitions** | `MISSING — REQUIRED INPUT` | Prevents unauthorized synthesis or invention of entities and relations. |
| A.3 | **Approved Reference & Seed Data** | `MISSING — REQUIRED INPUT` | Blocks creation of lookup tables, status codes, default role sets, and initial migration seeds. |
| A.4 | **Tenant / Multi-Tenancy Strategy** | `MISSING — REQUIRED INPUT` | Need explicit architectural decision: shared schema with `tenant_id` column + RLS vs. schema-per-tenant vs. database-per-tenant. |
| A.5 | **Clarification of Unresolved Incident Entities** | `MISSING — REQUIRED INPUT` | Blocks modeling of core incident domain (see [DATABASE_DECISIONS.md](file:///d:/IP%20Folder/DATABASE_DECISIONS.md) ADR-001, ADR-002, ADR-003). |
| A.6 | **Official Business Rules Affecting Persistence** | `MISSING — REQUIRED INPUT` | Cannot set CHECK constraints, cascade deletion behaviors (ON DELETE CASCADE vs. RESTRICT), or state-transition triggers. |

---

## B. Required from Member 1 (Backend Services & Domain Logic)

| # | Item | Current Status | Impact on Member 3 Responsibilities |
|---|------|----------------|-------------------------------------|
| B.1 | **Latest Backend / Domain Specification** | `MISSING — REQUIRED INPUT` | Needed to align database entity boundaries with backend service boundaries and domain models. |
| B.2 | **Entity Lifecycle Requirements** | `MISSING — REQUIRED INPUT` | Required to determine state columns, active/archived flags, and soft-delete (`deleted_at`) patterns. |
| B.3 | **API Read / Write Access Patterns** | `MISSING — REQUIRED INPUT` | Informs query workload ratios, read replica sizing, and connection pool requirements. |
| B.4 | **Tenant Isolation Expectations** | `MISSING — REQUIRED INPUT` | Clarifies whether backend expects application-level tenant filtering or database-level Row-Level Security (RLS) enforcement. |
| B.5 | **Transaction Boundaries & Concurrency Requirements** | `MISSING — REQUIRED INPUT` | Determines transaction isolation levels, locking strategies, and race condition mitigations. |
| B.6 | **Query Patterns Affecting Indexes** | `MISSING — REQUIRED INPUT` | Blocks optimal design of single-column, composite B-tree, partial, or covering indexes. |

---

## C. Required from Member 2 (AI Pipeline, Ingestion & Frontend)

| # | Item | Current Status | Impact on Member 3 Responsibilities |
|---|------|----------------|-------------------------------------|
| C.1 | **Latest AI & Evidence Specification** | `MISSING — REQUIRED INPUT` | Needed to establish data contracts between ingestion pipelines and persistent storage. |
| C.2 | **`incident_evidence` Definitive Definition** | `MISSING — REQUIRED INPUT` | Blocks resolution of duplicate entity flag (ADR-001); distinguishes raw upload tracking vs. annotated evidence records. |
| C.3 | **`incident_ai_analysis` Definitive Definition** | `MISSING — REQUIRED INPUT` | Blocks resolution of ambiguity flag (ADR-002); clarifies whether model outputs are normalized tables vs. JSONB payloads. |
| C.4 | **AI Result Persistence Requirements** | `MISSING — REQUIRED INPUT` | Determines how inference confidence scores, detected features, model version metadata, and bounding boxes are stored. |
| C.5 | **File / Media Metadata Requirements** | `MISSING — REQUIRED INPUT` | Defines schema for storing file keys, MIME types, file sizes, hashes (checksums), and upload timestamps. |
| C.6 | **Object-Storage Contract (MinIO / S3)** | `MISSING — REQUIRED INPUT` | Clarifies bucket structuring conventions, presigned URL generation patterns, and upload/download access controls. |
| C.7 | **Spatial / GIS Query Requirements** | `MISSING — REQUIRED INPUT` | Dictates PostGIS geometry specifications (`GEOMETRY(Point, 4326)`, `GEOMETRY(Polygon, 4326)`), bounding box queries, and spatial `GIST` indexes. |

---

## D. Required for Infrastructure Design

| # | Item | Current Status | Impact on Member 3 Responsibilities |
|---|------|----------------|-------------------------------------|
| D.1 | **PostgreSQL / PostGIS Version Target** | `MISSING — REQUIRED INPUT` | Baseline recommendation: PostgreSQL 16 + PostGIS 3.4. Requires team/platform confirmation. |
| D.2 | **Redis Usage Requirements** | `MISSING — REQUIRED INPUT` | Clarifies caching TTLs, rate-limiting key schemas, and message broker / queue coordination needs (e.g., Celery/BullMQ/Redis Streams). |
| D.3 | **MinIO / S3 Storage Requirements** | `MISSING — REQUIRED INPUT` | Determines local bucket naming, retention policies, lifecycle management, and S3 API compatibility profile. |
| D.4 | **Target Environment Configurations** | `MISSING — REQUIRED INPUT` | Specifications for development, testing, staging, and production network boundaries and resource limits. |
| D.5 | **Backup & PITR Expectations** | `MISSING — REQUIRED INPUT` | Defines Recovery Point Objective (RPO), Recovery Time Objective (RTO), WAL archiving, snapshot cadence, and backup storage targets. |
| D.6 | **CI/CD Requirements** | `MISSING — REQUIRED INPUT` | Pipeline tooling (e.g., GitHub Actions / GitLab CI), automated Flyway migration test fixtures, test runner containers, and linting standards. |

---

## E. Database Design Approvals Required Before Flyway V1

No migration scripts or DDL statements will be drafted or committed until formal sign-off is documented for each of the following design elements:

- [ ] **1. Complete Entity / Table List**: Exhaustive list of approved tables with zero ambiguous or unassigned names. (`MISSING — REQUIRED INPUT`)
- [ ] **2. Primary Key (PK) Strategy**: Explicit standard across all tables (e.g., UUIDv7 vs. UUIDv4 vs. BIGINT IDENTITY). (`MISSING — REQUIRED INPUT`)
- [ ] **3. Foreign Key (FK) Relationships**: Approved cardinality, referential integrity rules, and deletion cascades (`RESTRICT` vs. `CASCADE` vs. `SET NULL`). (`MISSING — REQUIRED INPUT`)
- [ ] **4. Multi-Tenancy Strategy**: Approved tenant key placement (`tenant_id`) and enforcement mechanisms (FK to `tenants` + RLS policies). (`MISSING — REQUIRED INPUT`)
- [ ] **5. Constraints**: Fully articulated NOT NULL, CHECK, and UNIQUE constraints for all domain fields. (`MISSING — REQUIRED INPUT`)
- [ ] **6. Relational Indexes**: Documented justification for all B-Tree indexes matching known query predicates. (`MISSING — REQUIRED INPUT`)
- [ ] **7. Spatial Types & Indexes**: Verified SRID (EPSG:4326) geometry types (`Point`, `Polygon`) with accompanying `GIST` spatial indexes. (`MISSING — REQUIRED INPUT`)
- [ ] **8. Audit & History Strategy**: Approved convention for timestamps (`created_at`, `updated_at`), actor tracking (`created_by`, `updated_by`), and change auditing. (`MISSING — REQUIRED INPUT`)
- [ ] **9. Object-Storage Metadata Model**: Standardized schema for mapping files stored in MinIO/S3 to relational database records. (`MISSING — REQUIRED INPUT`)
- [ ] **10. Seed & Reference Data**: Curated baseline dataset for initial migration insertion (`V2__reference_data.sql`). (`MISSING — REQUIRED INPUT`)

---

## Summary of Active Blockers

1. **Authoritative Data Dictionary**: Not yet provided in the workspace.
2. **Three Flagged Discrepancies**:
   - `ADR-001`: Duplicate `incident_evidence`
   - `ADR-002`: Ambiguous `incident_ai_analysis`
   - `ADR-003`: Incident tables with missing descriptions/definitions
3. **Absence of Cross-Member Interface Specifications**: Member 1 (Backend) and Member 2 (AI/Evidence) interface requirements are pending.
