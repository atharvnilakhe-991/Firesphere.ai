# FireSphere.ai — Database and Persistence Readiness Assessment

**Document Identifier:** `FS-DOC-PHASE1-DATABASE-PERSISTENCE-20261010`  
**Phase:** 1 — Workspace Audit and Integration Design  
**Local Workspace Root:** `C:\Users\athar\OneDrive\Desktop\Firesphere.ai`  
**Date:** 2026-10-10  
**Classification:** Database Schema, JPA Entities, and Data Lifecycle Audit  

---

## 1. Executive Summary

This audit evaluates the database and persistence readiness of FireSphere.ai across **Member 3 (PostgreSQL/Flyway migrations)**, **Member 2 (Spring Boot JPA entities)**, and **Member 4 (AI finding repositories)**.

### Critical Forensic Findings:
1. **The Primary Key Incompatibility (P0 Blocker):** Member 2's JPA domain entities define 128-bit `UUID` primary and foreign keys for every entity (`User`, `Organization`, `NocApplication`, `NocInspection`), whereas Member 3's Flyway migrations (`V1.0.1`–`V1.0.6`) strictly define 64-bit `BIGSERIAL` (`BIGINT`) primary keys. Connecting M2 to M3 immediately throws Hibernate `SchemaManagementException`.
2. **Missing AI Schema (ADR-005 Blocker):** Member 3 explicitly verified in [`DATABASE_AI_SCHEMA_BLOCKER.md`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M3_work/IP_DB/DATABASE_AI_SCHEMA_BLOCKER.md) that `firesphere.ai_insights` and related AI tables **do NOT exist** in any Flyway migration.
3. **M4 Ephemeral Storage Reality:** Member 4's [`finding_repository.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/services/finding_repository.py#L38) and [`repository.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/jobs/repository.py#L37) use `InMemoryFindingRepository` and `InMemoryJobRepository`. **All findings and AI job records are lost upon process restart.** Durable legal compliance storage is unmaterialized.
4. **M2 Stale Flyway Migration:** M2's [`V1__init.sql`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/backend/src/main/resources/db/migration/V1__init.sql) is an empty comment: `"-- Database schema migrations pending Member 3 contract."` M2 tests only pass because H2 auto-creates tables in-memory via `hibernate.ddl-auto: update`.

---

## 2. Persistence Readiness Matrix

| Business Domain & Entity | Member 3 Database Reality (PostgreSQL) | Member 2 Entity Reality (Spring Boot JPA) | Member 4 Reality (FastAPI) | Persistence Readiness Status | Critical Gaps & Mismatches |
| :--- | :--- | :--- | :--- | :---: | :--- |
| **Organizations & Tenants** | [`firesphere.organizations`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M3_work/IP_DB/database/migrations/V1.0.1__core_tenancy_and_identities.sql) (`id BIGSERIAL`, `code VARCHAR(50)`) | [`Organization.java`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/backend/src/main/java/com/firesphere/backend/organization/entity/Organization.java) (`id UUID`, `code VARCHAR(32)`) | Accepts `tenantId: str` in payload | `BLOCKED` | **Type Incompatibility:** `BIGINT` vs `UUID`. Table exists in M3, but M2 cannot bind without DDL failure. |
| **Users & Roles** | [`firesphere.users`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M3_work/IP_DB/database/migrations/V1.0.1__core_tenancy_and_identities.sql) (`id BIGSERIAL`, `org_id BIGINT`) | [`User.java`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/backend/src/main/java/com/firesphere/backend/auth/entity/User.java) (`id UUID`, `organizationId UUID`) | Contextual `officerId: str` | `BLOCKED` | **Type Incompatibility:** `BIGINT` vs `UUID`. M3 has normalized `roles`/`permissions` tables; M2 stores roles as enum. |
| **NOC Applications** | [`firesphere.noc_applications`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M3_work/IP_DB/database/migrations/V1.0.3__operations_noc_and_inspections.sql) (`id BIGSERIAL`, `org_id BIGINT`) | [`NocApplication.java`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/backend/src/main/java/com/firesphere/backend/noc/entity/NocApplication.java) (`id UUID`) | Not modeled | `BLOCKED` | **Type Incompatibility:** `BIGINT` vs `UUID`. State machine names differ slightly (`PAYMENT_PENDING` vs `SUBMITTED`). |
| **Inspections** | [`firesphere.inspections`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M3_work/IP_DB/database/migrations/V1.0.3__operations_noc_and_inspections.sql) (`id BIGSERIAL`, `org_id BIGINT`) | [`NocInspection.java`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/backend/src/main/java/com/firesphere/backend/inspection/entity/NocInspection.java) (`id UUID`, table `noc_inspections`) | `inspectionId: UUID` mandatory in request | `BLOCKED` | **Table & PK Mismatch:** `firesphere.inspections` (BIGINT) vs `public.noc_inspections` (UUID). |
| **Dynamic Checklists** | [`firesphere.inspection_checklists`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M3_work/IP_DB/database/migrations/V1.0.3__operations_noc_and_inspections.sql) and `inspection_checklist_items` | [`NocChecklist.java`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/backend/src/main/java/com/firesphere/backend/checklist/entity/NocChecklist.java), `ChecklistItem.java`, `ChecklistRule.java` | Not modeled | `BLOCKED` | M2 requires `checklist_rules` table to populate dynamic checklists. M3 does not provide a `checklist_rules` table. |
| **Evidence & Documents** | [`firesphere.building_documents`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M3_work/IP_DB/database/migrations/V1.0.3__operations_noc_and_inspections.sql) and `incident_evidence` | [`NocDocument.java`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/backend/src/main/java/com/firesphere/backend/document/entity/NocDocument.java) (table `noc_documents`) | `evidenceIds: List[UUID]` | `DISCONNECTED` | M2 uses `DummyDocumentStorageService`. Physical S3/MinIO bucket storage is not integrated. |
| **AI Analysis Runs & Jobs** | **DOES NOT EXIST** (ADR-005 Blocker) | [`AiAnalysisRun.java`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/backend/src/main/java/com/firesphere/backend/ai/entity/AiAnalysisRun.java) (table `ai_analysis_runs`) | `InMemoryJobRepository` / `RedisJobRepository` | `MISSING_PERSISTENCE` | No durable table in PostgreSQL. Data lost on M4 restart. M2 entity has no backing DDL script. |
| **CV & VLM Findings** | **DOES NOT EXIST** (`firesphere.ai_insights` missing) | [`AiAnalysisFinding.java`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/backend/src/main/java/com/firesphere/backend/ai/entity/AiAnalysisFinding.java) (table `ai_analysis_findings`) | `InMemoryFindingRepository` | `MISSING_PERSISTENCE` | No durable table in PostgreSQL. Findings disappear if AI service reboots. M2 entity only stores `label` and `confidence`. |
| **Officer Finding Reviews** | **DOES NOT EXIST** | Modeled in workflow transitions; no dedicated finding review entity | [`officer_verification_service.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/services/officer_verification_service.py) (In-memory review ledger) | `MISSING_PERSISTENCE` | Officer review determinations (CONFIRMED, DISMISSED, OVERRIDDEN) stored only in M4 RAM. |
| **Statutory Certificates** | [`firesphere.noc_certificates`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M3_work/IP_DB/database/migrations/V1.0.3__operations_noc_and_inspections.sql) (`id BIGSERIAL`, `qr_code_hash`) | [`NocCertificate.java`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/backend/src/main/java/com/firesphere/backend/certificate/entity/NocCertificate.java) (`id UUID`, SHA-256 checksum) | Not modeled | `BLOCKED` | PK mismatch (`BIGINT` vs `UUID`). PDF generated in M2 via OpenPDF, but storage path is mocked. |
| **Audit Logs** | [`firesphere.audit_logs`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M3_work/IP_DB/database/migrations/V1.0.4__emergency_incidents_fleet_and_audit.sql) (PostgreSQL trigger-backed) | [`SimpleAuditLogger.java`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/backend/src/main/java/com/firesphere/backend/common/audit/SimpleAuditLogger.java) (Stdout logger) | Internal audit history in finding models | `PARTIALLY_IMPLEMENTED` | M2 logs audit events to console; M3 has dedicated database audit triggers. Disconnected. |

---

## 3. Structural Decomposition

### Section A: Existing Schema (Member 3 Migrations)
The following tables are materialized across [`M3_work/IP_DB/database/migrations`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M3_work/IP_DB/database/migrations):
- `V1.0.0`: PostgreSQL extensions (`uuid-ossp`, `postgis`, `pgcrypto`).
- `V1.0.1`: `organizations`, `divisions`, `departments`, `users`, `roles`, `permissions`, `role_permissions`, `user_roles`.
- `V1.0.2`: `zones`, `beats`, `fire_stations`, `hydrants`, `water_sources`, `buildings`.
- `V1.0.3`: `building_contacts`, `building_documents`, `building_fire_equipment`, `building_owners`, `building_risk_profiles`, `noc_application_categories`, `noc_applications`, `noc_application_status_history`, `noc_certificates`, `noc_renewals`, `inspection_checklists`, `inspection_checklist_items`, `inspections`, `inspection_assignments`, `inspection_findings`, `corrective_actions`, `violations`, `violation_actions`.
- `V1.0.4`: `incidents`, `incident_dispatches`, `fire_vehicles`, `incident_vehicles`, `incident_crews`, `incident_timeline`, `incident_casualties`, `incident_reports`, `incident_evidence`, `vehicle_crew`, `vehicle_fitness`, `vehicle_fuel_logs`, `vehicle_location_history`, `warehouses`, `inventory_items`, `inventory_stock`, `training_courses`, `audit_logs`.
- `V1.0.5`: Row-Level Security (`ALTER TABLE ... ENABLE ROW LEVEL SECURITY`) applied across all tenant tables using `app.current_org_id`.
- `V1.0.6`: Secondary foreign key indexes on operational tables.

### Section B: Existing Persistence Implementation
- **Member 2:** Relies entirely on Spring Data JPA repositories (`NocApplicationRepository`, `UserRepository`, `AiAnalysisRunRepository`, etc.). In development/tests, Hibernate generates tables dynamically inside H2 (`application-test.yml`).
- **Member 4:** Uses [`InMemoryFindingRepository`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/services/finding_repository.py#L38) with explicit non-durable classification:
  ```python
  class InMemoryFindingRepository(BaseFindingRepository):
      is_durable: bool = False
      # Ephemeral in-process cache; official durable persistence owned by Spring Boot backend.
  ```

### Section C: Missing Persistence Capabilities
1. **AI Analysis Runs & Findings Table:** PostgreSQL lacks durable storage for AI run metadata, evidence bounding boxes, severity reasoning, and risk scores.
2. **Finding Verification Ledger:** PostgreSQL lacks a table to capture officer review actions (CONFIRM, DISMISS, OVERRIDE, REMARKS).
3. **Dynamic Checklist Rules Table:** PostgreSQL lacks the `checklist_rules` table required by M2's `NocChecklistService`.
4. **Idempotency Records Table:** PostgreSQL lacks the `idempotency_records` table required by M2's `IdempotencyRepository`.
5. **Payment Records Table:** PostgreSQL lacks the `payment_records` table required by M2's `PaymentService`.

---

## 4. Proposed Schema Harmonization (Status: PROPOSED)

To resolve the primary key mismatch and persistent AI gaps without breaking multi-tenancy, the team must approve the following schema harmonization plan:

### 4.1 Resolution of Primary Key Incompatibility
Two distinct options exist:
- **Strategy 1 (Harmonize M3 Schema to UUIDs):** Convert M3's primary keys from `BIGSERIAL` to `UUID DEFAULT gen_random_uuid()`.
  - *Pros:* Matches M1 frontend, M2 backend, M4 AI service, and M5 GIS service (which all use UUIDs natively). Eliminates ID collision across distributed microservices.
  - *Cons:* Requires updating M3's existing V1.0.1–V1.0.4 migration files or issuing a major V2 migration.
- **Strategy 2 (Map M2 Entities to BIGINT):** Convert M2 Java entity `@Id` fields from `UUID` to `Long`.
  - *Pros:* Keeps M3 migrations untouched.
  - *Cons:* Breaks M1 client models, M4 AI service UUID contracts, and M5 GIS services. High refactoring cost across three subsystems.
- **Architectural Recommendation:** **Strategy 1 (Harmonize to UUIDs)**. Modern SaaS architectures and offline-first mobile sync queues (M1 SQLCipher) require UUIDs to generate collision-free keys on mobile devices before remote sync.

### 4.2 Proposed Migration: `V1.0.7__ai_insights_and_reviews.sql`
```sql
-- Migration: V1.0.7__ai_insights_and_reviews.sql
CREATE TABLE IF NOT EXISTS firesphere.ai_analysis_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    inspection_id UUID NOT NULL REFERENCES firesphere.inspections(id) ON DELETE CASCADE,
    run_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    model_name VARCHAR(128) NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    execution_duration_ms NUMERIC(10, 2),
    summary_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES firesphere.users(id)
);

CREATE TABLE IF NOT EXISTS firesphere.ai_findings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES firesphere.ai_analysis_runs(id) ON DELETE CASCADE,
    org_id UUID NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    inspection_id UUID NOT NULL REFERENCES firesphere.inspections(id) ON DELETE CASCADE,
    evidence_id UUID NOT NULL,
    finding_type VARCHAR(64) NOT NULL,
    source_model VARCHAR(32) NOT NULL,
    confidence NUMERIC(5, 4) NOT NULL,
    confidence_level VARCHAR(16) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    operational_risk_score INTEGER,
    explanation TEXT NOT NULL,
    bounding_box_json JSONB,
    verification_guidance_json JSONB,
    validation_status VARCHAR(32) NOT NULL DEFAULT 'VALIDATED',
    conflict_status VARCHAR(32) NOT NULL DEFAULT 'NO_CONFLICT',
    review_status VARCHAR(32) NOT NULL DEFAULT 'UNREVIEWED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS firesphere.ai_officer_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    finding_id UUID NOT NULL REFERENCES firesphere.ai_findings(id) ON DELETE CASCADE,
    org_id UUID NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    officer_id UUID NOT NULL REFERENCES firesphere.users(id),
    decision VARCHAR(32) NOT NULL, -- OFFICER_CONFIRMED, OFFICER_DISMISSED, OVERRIDDEN
    officer_remarks TEXT,
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Row-Level Security
ALTER TABLE firesphere.ai_analysis_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE firesphere.ai_findings ENABLE ROW LEVEL SECURITY;
ALTER TABLE firesphere.ai_officer_reviews ENABLE ROW LEVEL SECURITY;
```
