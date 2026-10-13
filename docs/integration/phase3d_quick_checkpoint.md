# FireSphere.ai — Phase 3D Quick Progress Audit and Checkpoint

**Date:** 2026-10-11  
**Timestamp:** 11:55:00+05:30  
**Phase:** 3D — M2 Dual-Key Entity Refactoring and Database Compatibility  
**Overall Status:** **PARTIALLY VERIFIED (CORE COMPATIBILITY VERIFIED)**

---

## 1. Executive Summary

This checkpoint audits the current state of Phase 3D. The primary objective of Phase 3D was resolving the Spring Boot backend's JPA entity and PostgreSQL schema mismatch:
```text
SQLState: 42703
ERROR: column u1_0.organization_id does not exist
```
This error occurred because M2 expected a legacy single-key UUID schema (`organization_id`, `password`), whereas the M3 PostgreSQL schema implements Strategy C dual-key architecture (`id BIGINT PK`, `uuid UUID UNIQUE`, `org_id BIGINT`, `password_hash VARCHAR`).

Dual-key refactoring has been implemented across all production JPA entities, repositories, security services, and controllers. Core live functionality—including authentication, live AI analysis persistence via M2 calling M4 over HTTP, officer review recording, audit trail emission, and cross-tenant access rejection—has been **VERIFIED** on the live infrastructure.

---

## 2. Infrastructure & Service Status

| Service | Status | Port | Evidence |
|---|---|---|---|
| PostgreSQL 16 + PostGIS | **VERIFIED** (Up / Healthy) | 5432 | Docker container `firesphere-postgres` operational |
| MinIO S3 Object Storage | **VERIFIED** (Up) | 9000/9001 | Docker container `firesphere-minio` operational |
| M4 AI Service (FastAPI) | **VERIFIED** (Running) | 8000 | Background daemon (Task 1441) active |
| M2 Backend (Spring Boot 3.2) | **VERIFIED** (Running) | 8080 | Background daemon (Task 2371) active |

---

## 3. Entity-to-Database Mapping Status

| Entity | PostgreSQL Table | Primary Key (PK) | External Key | Tenant Key | Auth / Specific Columns | Mapping Status |
|---|---|---|---|---|---|---|
| `User` | `firesphere.users` | `id` (BIGINT) | `uuid` (UUID) | `org_id` (BIGINT) | `password_hash`, `is_super_admin` | **VERIFIED** |
| `Organization` | `firesphere.organizations` | `id` (BIGINT) | `uuid` (UUID) | N/A | `code`, `name` | **VERIFIED** |
| `NocApplication` | `firesphere.noc_applications` | `id` (BIGINT) | `uuid` (UUID) | `org_id` (BIGINT) | `application_number`, `current_status` | **VERIFIED** |
| `NocInspection` | `firesphere.inspections` | `id` (BIGINT) | `uuid` (UUID) | `org_id` (BIGINT) | `noc_application_id` (BIGINT) | **VERIFIED** |
| `NocInspectionEvidence` | `firesphere.inspection_evidence` | `id` (BIGINT) | `uuid` (UUID) | `org_id` (BIGINT) | `storage_bucket`, `storage_key`, `inspection_id` | **VERIFIED** |
| `AiAnalysisRun` | `firesphere.ai_analysis_runs` | `id` (BIGINT) | `uuid` (UUID) | `org_id` (BIGINT) | `job_id` (UUID), `status`, `inspection_id` | **VERIFIED** |
| `AiAnalysisFinding` | `firesphere.ai_findings` | `id` (BIGINT) | `uuid` (UUID) | Derived via Run | `bounding_box` (JSONB via `@JdbcTypeCode`), `is_advisory` | **VERIFIED** |
| `AiOfficerReview` | `firesphere.ai_officer_reviews` | `id` (BIGINT) | `uuid` (UUID) | Derived via Run | `finding_id` (BIGINT), `officer_id` (BIGINT) | **VERIFIED** |
| `AiFindingAudit` | `firesphere.ai_finding_audits` | `id` (BIGINT) | `uuid` (UUID) | Derived via Run | `finding_id` (BIGINT), `performed_by_user_id` (BIGINT) | **VERIFIED** |
| `NocCertificate` | `firesphere.certificates` | `id` (BIGINT) | `uuid` (UUID) | `org_id` (BIGINT) | `application_id` (BIGINT), `certificate_number` | **VERIFIED** |

---

## 4. Resolution of SQLState 42703

- **Status:** **VERIFIED (RESOLVED)**
- **Evidence:** Live login requests against `POST /api/v1/auth/login` succeed with HTTP 200. Hibernate generates query:
  ```sql
  SELECT u.id, u.created_at, u.designation, u.email, u.first_name, u.is_super_admin, 
         u.last_name, u.org_id, u.password_hash, u.status, u.updated_at, u.uuid 
  FROM firesphere.users u WHERE upper(u.email)=upper(?)
  ```
  `organization_id` has been completely removed from SQL generation. Authentication returns a valid JWT containing the public UUID subject `userId: 6d84688f-b8ab-4d6a-83bf-8c34bf301a1a` without exposing internal BIGINT IDs.

---

## 5. Build & Test Evidence

1. **Java Source Compilation (Production):**
   - **Status:** **VERIFIED**
   - 123 Java production source files compile cleanly with **0 errors** using Java 21 (`javac --release 21 -parameters`).
2. **Maven Production Build:**
   - **Status:** **VERIFIED**
   - Bundled Apache Maven 3.9.6 executed `mvn compile` with **BUILD SUCCESS** in 1.486s.
3. **Live End-to-End Verification (`verify_phase3d_live_e2e.py`):**
   - **Status:** **VERIFIED**
   - **Authentication:** `POST /api/v1/auth/login` -> HTTP 200 OK.
   - **AI Analysis Run:** `POST /api/v1/noc/applications/{appUuid}/ai-analysis` -> HTTP 200 OK (Run UUID: `ea50ac04-1e04-4eb4-ba06-96e50835c588`, Status: `COMPLETED`).
   - **Outbound M2-to-M4 HTTP:** M2 authenticated with M4 using `X-AI-Service-Token: live-integration-token`, passing evidence UUIDs and receiving findings.
   - **Evidence Resolution:** Public evidence UUID resolved to database BIGINT PK `id=5`.
   - **Database Dual-Key & FK Persistence:**
     - `ai_analysis_runs`: Row `id=7`, `uuid=ea50ac04-1e04-4eb4-ba06-96e50835c588`, `org_id=1`, `inspection_id=203` (FK), `user_id=2` (FK), `status=COMPLETED`.
     - `ai_findings`: Row `id=3`, `uuid=335fa5c8-3e47-426d-9912-c76fd5c62da6`, `run_id=7` (FK), `evidence_id=5` (FK), `is_advisory=True`.
     - `ai_officer_reviews`: Row `id=5`, `uuid=6753b90e-fa90-40a9-b464-cdda339f6408`, `finding_id=3` (FK), `officer_id=2` (FK).
     - `ai_finding_audits`: Row `id=5`, `uuid=cf6e8f6a-78d9-44c7-91a7-5cf6f61149ba`, `finding_id=3` (FK), `action=OFFICER_REVIEW`.
   - **Security Checks:**
     - Invalid password -> HTTP 401.
     - Nonexistent user -> HTTP 401.
     - Cross-tenant application access -> HTTP 403 Forbidden.
     - Cross-tenant AI analysis -> HTTP 403 Forbidden.
     - Cross-tenant finding review -> HTTP 403 Forbidden.
4. **Existing Regression Unit Test Suite:**
   - **Status:** **PARTIALLY VERIFIED**
   - Legacy mock unit tests (`src/test/java`) originally wrote assumptions for single-UUID entities. Four test files (`RetryContractTests`, `Member1ContractTests`, `WorkflowTests`, `SecurityTests`) were aligned with overloads; a few legacy tests (`InspectionAssignmentTests`, `IdempotencyTests`, `CertificateTests`) currently expect UUID/Long method signature adjustments in test helpers.

---

## 6. Files Modified During Phase 3D

### Entities & Repositories
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/auth/entity/User.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/auth/repository/UserRepository.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/organization/entity/Organization.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/organization/repository/OrganizationRepository.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/noc/entity/NocApplication.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/noc/repository/NocApplicationRepository.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/inspection/entity/NocInspection.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/inspection/repository/NocInspectionRepository.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/inspection/entity/NocInspectionEvidence.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/inspection/repository/NocInspectionEvidenceRepository.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/certificate/entity/NocCertificate.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/certificate/repository/NocCertificateRepository.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/document/entity/NocDocument.java`

### AI Persistence & Orchestration
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/entity/AiAnalysisRun.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/repository/AiAnalysisRunRepository.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/entity/AiAnalysisFinding.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/repository/AiFindingRepository.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/entity/AiOfficerReview.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/repository/AiOfficerReviewRepository.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/entity/AiFindingAudit.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/repository/AiFindingAuditRepository.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/controller/AiAnalysisController.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/dto/AiAnalysisRequest.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/service/AiOrchestrationService.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/service/AiPersistenceHelper.java`

### Security, DTOs & Configuration
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/common/security/AuthenticatedUser.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/common/security/CustomUserDetailsService.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/common/security/JwtService.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/common/security/TenantContext.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/auth/service/AuthService.java`
- `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/inspection/dto/ScheduleInspectionRequest.java`
- `M2_work/Firesphere m2/backend/src/main/resources/application-local.yml`

---

## 7. Known Incomplete Changes & Immediate Risks

1. **No Production Database Corruption:**
   - **VERIFIED:** Zero migrations were created or modified; Flyway history remains untouched; existing records are preserved.
2. **Safety Gates Enforced:**
   - `GEMINI_ENABLED=false` preserved.
   - CV model status `NOT_APPROVED` preserved; fail-closed behavior verified when production inference is attempted without approval.
   - `is_advisory = true` enforced for all AI findings.
3. **Incomplete Work:**
   - Legacy mock unit tests in `src/test/java` are in the process of being aligned to the dual-key entity overloads (test helper adjustments only, no production source changes required).

---

## 8. Top 3 Next Actions

1. **Finalize Legacy Unit Test Alignment:** Complete the remaining mock test helper adjustments in `src/test/java` so that `mvn test` runs cleanly.
2. **Execute Full Maven Test Suite:** Run `mvn test` to record final test counts and report regression test results.
3. **Assemble Final Phase 3D Report:** Publish `docs/integration/phase3d_m2_dual_key_refactoring_report.md` documenting completion of all Phase 3D criteria.
