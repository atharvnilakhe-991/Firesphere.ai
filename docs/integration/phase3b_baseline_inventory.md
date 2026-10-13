# FireSphere.ai — Phase 3B: Pre-Implementation Baseline Inventory

**Author**: Senior Database Architect, Spring Boot Persistence Engineer, PostgreSQL/PostGIS Engineer, AI Service Integration Engineer  
**Status**: BASELINE INVENTORY COMPLETE  
**Date**: October 2026  
**Phase**: Phase 3B (Database and Evidence Integration)  

---

## 1. Actual Relevant Files

### Member 2 (Spring Boot Backend — `M2_work/Firesphere m2/backend`)
- **Configuration**:
  - `src/main/resources/application.properties` (JDBC URL: `jdbc:postgresql://localhost:5432/firesphere_db`, user: `postgres`, lacking `currentSchema=firesphere`, lacking `hibernate.default_schema`)
  - `src/main/resources/application.yml` (AI client URL: `http://localhost:8000`, auth header config)
- **Entities & Repositories**:
  - `com/firesphere/backend/noc/entity/NocApplication.java` & `repository/NocApplicationRepository.java`
  - `com/firesphere/backend/inspection/entity/NocInspection.java` & `repository/NocInspectionRepository.java`
  - `com/firesphere/backend/document/entity/NocDocument.java` & `repository/NocDocumentRepository.java`
  - `com/firesphere/backend/checklist/entity/NocChecklist.java` & `repository/NocChecklistRepository.java`
  - `com/firesphere/backend/certificate/entity/NocCertificate.java` & `repository/NocCertificateRepository.java`
  - `com/firesphere/backend/payment/entity/PaymentRecord.java` & `repository/PaymentRepository.java`
  - `com/firesphere/backend/auth/entity/User.java` & `repository/UserRepository.java`
  - `com/firesphere/backend/organization/entity/Organization.java` & `repository/OrganizationRepository.java`
  - `com/firesphere/backend/common/idempotency/entity/IdempotencyRecord.java` & `repository/IdempotencyRepository.java`
  - `com/firesphere/backend/ai/entity/AiAnalysisRun.java` & `repository/AiAnalysisRunRepository.java`
  - `com/firesphere/backend/ai/entity/AiAnalysisFinding.java`
- **Services & Clients**:
  - `com/firesphere/backend/ai/service/AiOrchestrationService.java`
  - `com/firesphere/backend/ai/client/HttpAiAnalysisClient.java`
  - `com/firesphere/backend/document/service/DummyDocumentStorageService.java`
  - `com/firesphere/backend/document/service/DocumentStorageService.java`
  - `com/firesphere/backend/document/service/NocDocumentService.java`
  - `com/firesphere/backend/inspection/service/NocInspectionService.java`
  - `com/firesphere/backend/common/security/TenantContext.java`
  - `com/firesphere/backend/common/audit/SimpleAuditLogger.java`

### Member 3 (PostgreSQL Database & Migrations — `M3_work/IP_DB`)
- **Flyway Migrations**:
  - `database/migrations/V1.0.0__init_schemas_and_extensions.sql` (Creates extensions `postgis`, `pgcrypto`, `uuid-ossp`; schemas `firesphere`, `gis`, `audit`, `billing`)
  - `database/migrations/V1.0.1__identity_and_access_control.sql` (Tables: `organizations`, `divisions`, `departments`, `users`, `roles`, `user_roles`)
  - `database/migrations/V1.0.2__reference_data_and_facilities.sql` (Tables: `building_types`, `occupancy_types`, `fire_stations`)
  - `database/migrations/V1.0.3__workflow_and_inspections.sql` (Tables: `applications`, `inspections`, `checklists`, `violations`)
  - `database/migrations/V1.0.4__documents_and_incidents.sql` (Tables: `building_documents`, `incidents`, `incident_evidence`)
  - `database/migrations/V1.0.5__enable_row_level_security.sql` (RLS policies on 44 tables, session helper `firesphere.current_org_id()`)
  - `database/migrations/V1.0.6__spatial_tables_and_indexes.sql` (PostGIS spatial layers)
- **Documentation & Tests**:
  - `DATABASE_AI_SCHEMA_BLOCKER.md` (Explicitly records blocker awaiting M2/M4 schema)
  - `database/tests/01_verify_extensions_and_schemas.sql` to `05_verify_row_level_security.sql`

### Member 4 (AI Service — `ai-service`)
- `app/api/v1/inspection_analysis.py` (FastAPI route `POST /internal/ai/inspection-analysis`)
- `app/schemas/inspection.py` & `app/schemas/findings.py` (Pydantic request/response schemas)
- `app/storage/evidence_client.py` (`EvidenceClient` with `fetch_evidence_bytes` and SHA-256 verification)
- `app/storage/s3_storage.py` (`S3StorageProvider` connecting to MinIO/S3)
- `app/intelligence/evidence_validator.py`
- `tests/test_m2_m4_contract_specification.py`

---

## 2. Current Database Schema Findings

1. **Schema Encapsulation**: All tables exist in schema `firesphere.`, not `public`.
2. **Dual-Key Pattern in M3**: Core domain tables declare `id BIGSERIAL PRIMARY KEY` AND `uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE`.
3. **Foreign Keys in M3**: Relational joins enforce integer foreign keys (`REFERENCES firesphere.applications(id)`).
4. **Missing AI Structures in M3**: Zero tables exist for AI analysis runs, AI findings, or officer reviews.
5. **Missing Inspection Evidence in M3**: Zero tables exist for multi-photo field inspection evidence. Only `building_documents` (architectural plans) and `incident_evidence` (emergency dispatch media) exist.

---

## 3. Existing Identifier Conventions

- **M3 Database**:
  - Internal: `BIGSERIAL` / `BIGINT` (64-bit integer)
  - Public: `UUID` with `gen_random_uuid()`
- **M2 Backend**:
  - Internal & Public: M2 currently declares `@Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id` across all entities.
  - Foreign keys are stored as raw `UUID` values (`private UUID applicationId`), incompatible with M3's `BIGINT` foreign keys.
- **M4 AI Service**:
  - API schemas expect `UUID` for `inspectionId` and `List[UUID]` for `evidenceIds`.
  - Ephemeral cache dictionaries key jobs by `UUID`.

---

## 4. Existing Evidence Workflow

- **Citizen Document Upload**: Implemented via `NocDocumentService.uploadDocument(applicationId, documentType, file)` storing metadata in `noc_documents`.
- **Storage Implementation**: M2 uses `DummyDocumentStorageService` returning `"mock-storage-ref/" + UUID + "/" + filename`. No physical S3/MinIO client is configured in M2.
- **Field Inspection Evidence**: Completely absent in M2. There is no controller, entity, or repository for field officers to upload photos attached to an inspection.
- **AI Contract Transmission**: In Phase 2 tests, M2 sent synthetic random UUIDs to M4 because real persisted inspection evidence IDs could not be resolved.

---

## 5. Existing AI Persistence Implementation

- **M2**: `AiAnalysisRun` and `AiAnalysisFinding` exist in code but target `public.ai_analysis_runs`, which does not exist in M3. Runs are not durably saved in PostgreSQL.
- **M4**: `InMemoryJobRepository` and `InMemoryFindingRepository` store results in Python memory dictionaries. They are wiped upon container restart. Direct database writes from M4 are intentionally prohibited.

---

## 6. Current Transaction Boundaries

- **M2 Anti-Pattern**: `AiOrchestrationService.requestAnalysis()` is marked `@Transactional`.
- This causes the HikariCP database connection to remain checked out while `aiClient.analyze()` executes a blocking HTTP request (taking 5 to 30 seconds).
- If M4 times out, the outer Spring transaction rolls back, erasing the `PROCESSING` run record and destroying audit history.

---

## 7. Current Authentication and Tenant-Isolation Mechanisms

- **M2 Authentication**: Server-side JWT validation in Spring Security filter chain populates `TenantContext`:
  `TenantContext.getCurrentUserId()` and `TenantContext.getCurrentOrganizationId()`.
- **M2-M4 Authentication**: Mutual authentication via `X-AI-Service-Token` validated with constant-time HMAC comparison.
- **PostgreSQL RLS**: Migration `V1.0.5` defines RLS policies using `current_setting('app.current_org_id', TRUE)`. However, M2 does not execute `SET LOCAL app.current_org_id` on checkout, and connects as superuser `postgres` (bypassing RLS). Tenant isolation in M2 currently relies entirely on application-level filtering (`WHERE organizationId = :orgId`).

---

## 8. Existing Tests and Baseline Status

- **M4 (FastAPI)**:
  - `pytest tests/test_m2_m4_contract_specification.py`: **7 passed in 0.26s**.
  - Verified working in local Python 3.13 environment.
- **M2 (Spring Boot)**:
  - Java 26 environment available (`java version "26.0.2.1"`, `javac 26.0.2.1`).
  - Pre-built fat JAR `backend-0.0.1-SNAPSHOT.jar` present in `target/`.
  - Direct `mvn` command is not on system PATH; compilation and testing must be verified via Java tooling or project build scripts.
- **M3 (PostgreSQL)**:
  - Database verification SQL scripts (`01` through `05`) present in `database/tests/`.
  - Local PostgreSQL port 5432 is currently closed/stopped.

---

## 9. Specific Implementation Gaps to Address in Phase 3B

1. **Gap 1 (Schema)**: Create Flyway migration `V1.0.7` with tables for `inspection_evidence`, `ai_analysis_runs`, `ai_findings`, `ai_officer_reviews`, and `ai_finding_audits`.
2. **Gap 2 (Dual-Key Alignment)**: Map M2 entities to schema `firesphere.`, maintaining `id Long` (PK) and `uuid UUID` (canonical public key), preserving public REST APIs.
3. **Gap 3 (Evidence Model & Service)**: Implement `NocInspectionEvidence` entity, repository, and service in M2.
4. **Gap 4 (Storage Client)**: Implement production S3/MinIO evidence storage service in M2 writing to `tenants/{tenantId}/evidence/{evidenceId}.jpg` with SHA-256 computation.
5. **Gap 5 (Evidence Resolution)**: Wire M2's `AiOrchestrationService` to resolve genuine persisted evidence UUIDs instead of synthetic stubs.
6. **Gap 6 (Two-Phase Transactions)**: Decouple M2 transaction boundaries so that HTTP calls to M4 execute outside active database transactions.
7. **Gap 7 (Durable AI Persistence)**: Durably persist analysis runs, advisory findings (`is_advisory = true`), and officer reviews in PostgreSQL.
8. **Gap 8 (Security & RLS)**: Ensure tenant context propagation and least-privilege security safeguards.
