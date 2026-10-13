# FireSphere.ai — Phase 3A: Phase 3B Implementation & Verification Plan

**Author**: Senior Database Architect, Spring Boot Persistence Engineer, AI Service Integration Engineer  
**Status**: APPROVED DESIGN / AUDIT REPORT  
**Date**: October 2026  
**Phase**: Phase 3A (Database and Persistence Integration Audit)  

---

## 1. Executive Summary

Phase 3A provided a complete audit of the M3 database migrations (`V1.0.0`–`V1.0.6`), M2 JPA entities and Spring Boot configuration, and M4 AI service storage and repository boundaries.

This implementation plan details the **dependency-ordered sequence of implementation tasks for Phase 3B**, the complete **test verification matrix**, and the **first three safe tasks** to execute when Phase 3B begins.

---

## 2. Phase 3B Dependency-Ordered Implementation Roadmap

Phase 3B implementation must follow a strict sequential DAG (Directed Acyclic Graph) to prevent broken foreign keys, runtime type cast exceptions, or unmigrated schema states.

```mermaid
graph TD
    subgraph Track 1: Database Migration (M3)
        T1["Task 1: Flyway V1.0.7 Migration Script<br/>(Create inspection_evidence, ai_analysis_runs, ai_findings, ai_officer_reviews, ai_finding_audits)"]
        T2["Task 2: Migration Verification in PostgreSQL<br/>(Apply on clean & upgraded DB, verify RLS & spatial indexes)"]
    end

    subgraph Track 2: Backend Persistence & Schema Alignment (M2)
        T3["Task 3: Spring Boot JDBC & Schema Configuration<br/>(Set default_schema=firesphere, update application.properties)"]
        T4["Task 4: M2 Dual-Key Entity Alignment<br/>(Map internal id BIGINT + canonical uuid UUID across JPA entities)"]
        T5["Task 5: M2 AI & Evidence JPA Entities & Repositories<br/>(Create NocInspectionEvidence, AiAnalysisRun, AiFinding entities)"]
        T6["Task 6: Decouple M2 Transaction Boundaries<br/>(Refactor AiOrchestrationService to two-phase transaction pattern)"]
    end

    subgraph Track 3: Evidence Storage & Resolution (M2 + MinIO)
        T7["Task 7: MinIO S3 Object Storage Client in M2<br/>(Replace DummyDocumentStorageService with S3StorageService)"]
        T8["Task 8: Inspection Evidence Upload Controller<br/>(Add POST /api/v1/inspections/{id}/evidence with SHA-256 validation)"]
        T9["Task 9: Real Evidence Resolution in AI Client<br/>(Resolve evidence UUIDs from DB instead of synthetic test stubs)"]
    end

    subgraph Track 4: End-to-End Verification
        T10["Task 10: Integration Test Execution & Validation<br/>(Execute TestContainers / live PostgreSQL + MinIO + M4 end-to-end tests)"]
    end

    T1 --> T2
    T2 --> T3
    T3 --> T4
    T4 --> T5
    T2 --> T5
    T5 --> T6
    T5 --> T7
    T7 --> T8
    T8 --> T9
    T6 --> T9
    T9 --> T10
```

---

## 3. Detailed Phase 3B Task Specifications

### Task 1: Flyway Migration `V1.0.7__create_inspection_evidence_and_ai_tables.sql` (M3)
- **Component**: `M3_work/IP_DB/database/migrations/`
- **Actions**:
  1. Author `V1.0.7__create_inspection_evidence_and_ai_tables.sql`.
  2. Create `firesphere.inspection_evidence` with `id BIGSERIAL`, `uuid UUID UNIQUE`, foreign keys to `inspections(id)` and `organizations(id)`.
  3. Create `firesphere.ai_analysis_runs`, `firesphere.ai_findings`, `firesphere.ai_officer_reviews`, and `firesphere.ai_finding_audits`.
  4. Enforce check constraints: `chk_finding_statutory_advisory (is_advisory = TRUE)` and `chk_finding_confidence (confidence BETWEEN 0.0 AND 1.0)`.
  5. Enable Row-Level Security (RLS) on all five new tables with `firesphere.current_org_id()`.
- **Pre-requisites**: None. Builds cleanly on `V1.0.6`.
- **Approval Needed**: Member 3 (DB Lead) & Member 4 (AI Lead).

### Task 2: Database Migration Verification & Test Suite Execution (M3)
- **Component**: `M3_work/IP_DB/database/tests/`
- **Actions**:
  1. Author `06_verify_ai_and_evidence_schema.sql`.
  2. Execute migration against a clean local PostgreSQL instance (`validate_foundation.ps1` / `.sh`).
  3. Test schema upgrade path from existing `V1.0.6` state.
  4. Verify foreign key constraints, RLS policies, and index presence.
- **Pre-requisites**: Task 1.

### Task 3: Spring Boot JDBC & Schema Configuration Adaptation (M2)
- **Component**: `M2_work/Firesphere m2/backend/src/main/resources/application.properties`
- **Actions**:
  1. Add `currentSchema=firesphere` to JDBC connection URL.
  2. Add `spring.jpa.properties.hibernate.default_schema=firesphere`.
  3. Set `spring.jpa.hibernate.ddl-auto=validate` to enforce strict schema adherence without destructive auto-generation.
- **Pre-requisites**: Task 2.

### Task 4: M2 Dual-Key Entity Alignment (M2)
- **Component**: `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/*/entity/`
- **Actions**:
  1. Refactor 13 existing JPA entities to Strategy C (Dual-Key Architecture).
  2. Change `@Id private UUID id` to `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id`.
  3. Add `@Column(name = "uuid", nullable = false, updatable = false) private UUID uuid`.
  4. Preserve all public REST DTOs and external contracts to use `UUID` exclusively.
- **Pre-requisites**: Task 3.

### Task 5: M2 AI & Evidence JPA Entities and Repositories (M2)
- **Component**: `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/entity/` & `inspection/entity/`
- **Actions**:
  1. Create `NocInspectionEvidence.java` and `NocInspectionEvidenceRepository.java`.
  2. Update `AiAnalysisRun.java` and `AiAnalysisFinding.java` to map to `firesphere.ai_analysis_runs` and `firesphere.ai_findings`.
  3. Create `AiOfficerReview.java` and `AiFindingAudit.java`.
  4. Implement tenant-aware repository queries (`findAllByInspectionIdAndOrganizationId`).
- **Pre-requisites**: Tasks 2, 4.

### Task 6: Decouple M2 Transaction Boundaries (M2)
- **Component**: `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/service/AiOrchestrationService.java`
- **Actions**:
  1. Remove `@Transactional` from `requestAnalysis()`.
  2. Split persistence into Transaction A (record creation) and Transaction B (result persistence).
  3. Keep the HTTP call `aiClient.analyze()` strictly non-transactional with bounded socket timeout (10s).
  4. Implement failure catch block to persist `FAILED` status with audit log upon network timeout.
- **Pre-requisites**: Task 5.

### Task 7: MinIO S3 Object Storage Client in M2 (M2)
- **Component**: `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/document/service/`
- **Actions**:
  1. Add AWS SDK S3 / MinIO client dependency to `pom.xml`.
  2. Implement `S3DocumentStorageService` supporting tenant-scoped keys (`tenants/{orgId}/evidence/{evidenceUuid}.jpg`).
  3. Compute SHA-256 checksum on stream ingest.
  4. Provide fallback mock configuration for disconnected local test runs.
- **Pre-requisites**: Task 5.

### Task 8: Inspection Evidence Upload Controller & Service (M2)
- **Component**: `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/inspection/controller/`
- **Actions**:
  1. Create `InspectionEvidenceController.java`:
     - `POST /api/v1/inspections/{id}/evidence`: Uploads photo, validates officer role, computes SHA-256, writes to S3, inserts DB record.
     - `GET /api/v1/inspections/{id}/evidence`: Lists evidence items with UUIDs and metadata.
  2. Enforce image format validation (JPEG/PNG magic bytes) and 10MB file limit.
- **Pre-requisites**: Tasks 5, 7.

### Task 9: Real Evidence Resolution in AI Client Pipeline (M2)
- **Component**: `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/service/AiOrchestrationService.java`
- **Actions**:
  1. Replace ad-hoc synthetic UUID generation with database lookup:
     `List<UUID> evidenceUuids = evidenceRepository.findUuidsByInspectionId(inspectionId);`
  2. Reject analysis requests if zero evidence photos are registered for the inspection.
  3. Pass genuine `evidenceUuids` in `ExternalAiRequest` to M4.
- **Pre-requisites**: Tasks 6, 8.

### Task 10: End-to-End Persistence & Integration Test Execution
- **Component**: All workspace test suites.
- **Actions**:
  1. Execute M3 SQL verification scripts (`01`–`06`).
  2. Execute M2 Spring Boot integration tests with TestContainers PostgreSQL.
  3. Execute M4 pytest suites verifying live evidence retrieval and job execution.
  4. Perform end-to-end flow: Upload photo -> Resolve evidence -> Trigger AI -> Store findings -> Officer review.
- **Pre-requisites**: Tasks 1 through 9.

---

## 4. Required Phase 3B Testing Matrix

| Test Suite Category | Target Component | Specific Verification Checks | Expected Outcome |
| :--- | :--- | :--- | :--- |
| **1. Migration Validation** | M3 Flyway | Flyway `validate` command against `V1.0.0`–`V1.0.7` checksums. | Migration scripts are syntax-error free and checksum-valid. |
| **2. Clean DB Migration** | M3 PostgreSQL | Run `run_migrations.ps1` on an empty PostgreSQL database. | All 49 tables, 5 schemas, PostGIS extensions initialized. |
| **3. Upgrade Migration** | M3 PostgreSQL | Apply `V1.0.7` on top of existing `V1.0.6` schema. | Zero data loss, new tables created, constraints active. |
| **4. JPA-Schema Compatibility** | M2 Spring Boot | Set `hibernate.ddl-auto=validate` during Spring Boot context start. | Context starts with zero `SchemaManagementException`. |
| **5. Dual-Key Mapping** | M2 Repositories | Create, fetch, and update entities using `Long id` and `UUID uuid`. | Primary keys auto-increment; UUIDs remain unique and queryable. |
| **6. Foreign Key Integrity** | M2 / M3 | Insert `ai_findings` with non-existent `run_id` or `inspection_id`. | Throws `DataIntegrityViolationException` / PostgreSQL FK error. |
| **7. Evidence Resolution** | M2 Service | Upload photo -> query `findUuidsByInspectionId()`. | Returns exact `UUID` corresponding to persisted photo. |
| **8. AI Finding Persistence** | M2 Service | Mock M4 response with 2 findings -> save to DB -> query repository. | Findings persisted, linked to run, `is_advisory=true`. |
| **9. Officer Review Persistence** | M2 Service | Officer submits review -> check `ai_officer_reviews` & audit log. | Review recorded with officer ID; finding state updated. |
| **10. Restart Durability** | M4 / M2 | Submit job -> restart M4 container -> fetch run from M2. | M2 returns durable run record regardless of M4 cache restart. |
| **11. Duplicate Submissions** | M2 Service | Submit same photo twice to same inspection. | SHA-256 deduplication returns existing evidence record. |
| **12. Multi-Tenant Isolation** | M2 / M3 | Query Organization A evidence using Organization B credentials. | Returns `403 Forbidden` / 0 results via RLS. |
| **13. Transaction Decoupling** | M2 Service | Force M4 timeout (delay > 10s) during `requestAnalysis()`. | DB connection not blocked; run record marked `FAILED`. |
| **14. M2–M4 Contract Match** | M2 HTTP Client | Send `ExternalAiRequest` to M4 endpoint. | `200 OK` with valid `ExternalAiResponse` matching Phase 2 schema. |

---

## 5. The First Three Safe Implementation Tasks for Phase 3B

When Phase 3B is formally authorized, execution MUST begin with these three safe, non-destructive, dependency-isolated tasks:

### Task 1: Author Flyway Migration `V1.0.7` (Draft Script Only)
- **Description**: Create `M3_work/IP_DB/database/migrations/V1.0.7__create_inspection_evidence_and_ai_tables.sql` containing the DDL for `inspection_evidence`, `ai_analysis_runs`, `ai_findings`, `ai_officer_reviews`, and `ai_finding_audits`.
- **Safety**: Authored as an offline file. Does not alter any live database or running service until executed.

### Task 2: Configure M2 Schema and JDBC Properties (M2 Config Update)
- **Description**: Update `M2_work/Firesphere m2/backend/src/main/resources/application.properties` to set `currentSchema=firesphere` and `spring.jpa.properties.hibernate.default_schema=firesphere`, while keeping `ddl-auto=validate`.
- **Safety**: Establishes schema namespacing for Spring Boot. Fully reversible and safe.

### Task 3: Author M2 Dual-Key Base Entity Mapping & AI Entities (M2 Entity Model)
- **Description**: Create the JPA entities `NocInspectionEvidence.java`, `AiAnalysisRun.java`, `AiFinding.java`, and `AiOfficerReview.java` mapped to the `firesphere.` schema with `id BIGINT` PK and `uuid UUID` public key.
- **Safety**: Compiles within the Java type system; does not execute database writes until wired to active controllers.
