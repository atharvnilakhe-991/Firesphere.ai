# FireSphere.ai — Phase 3C Blocker Resolution and Live Verification Report

**Project:** FireSphere.ai  
**Phase:** 3C — Blocker Resolution and Live Verification  
**Date:** October 10, 2026  
**Status / Verdict:** **PHASE 3C PARTIALLY COMPLETE**  
**Lead Roles:** Senior Integration Engineer, Database Engineer, Spring Boot Engineer, FastAPI Engineer, Application Security Engineer  

---

## 1. Executive Summary & Objective

The objective of this phase was to evaluate and resolve the remaining Phase 3C infrastructure blockers and perform real, repeatable verification of the FireSphere evidence-to-AI-to-database workflow.

### Verification Guardrails & Invariants
- **AI Safety Invariant:** `GEMINI_ENABLED=false` was strictly maintained across all configurations. Zero external Gemini or third-party API calls were made.
- **Model Approval Status:** Computer Vision model approval state strictly held at `NOT_APPROVED`.
- **Calibration Status:** Calibration state held at `NOT_CALIBRATED`.
- **Statutory Authority:** All AI findings are advisory visual observations (`complianceStatus: REQUIRES_OFFICER_VERIFICATION`). The AI system cannot grant or deny a Fire NOC. Only an authorized human Fire Safety Officer can make a statutory determination.
- **Credential Hygiene:** No plaintext passwords, secret keys, or authentication tokens are exposed in this report or runtime logs.
- **Workspace & Git Integrity:** Zero destructive Git operations (no commits, pushes, merges, branch switches, or resets). Existing workspace code across M1 through M5 was preserved.

---

## 2. Local Infrastructure Audit & Resolution Plan

A rigorous re-audit of the host system was conducted across container engines, local services, and standalone binaries:

| Infrastructure Component | Command / Port Tested | Actual Result | Status |
| :--- | :--- | :--- | :--- |
| **Docker CLI** | `Get-Command docker` | Not recognized | **NOT INSTALLED** |
| **Podman CLI** | `Get-Command podman` | Not recognized | **NOT INSTALLED** |
| **WSL 2 Runtime** | `wsl.exe -l -v` | `"Windows Subsystem for Linux is not installed"` | **NOT INSTALLED** |
| **PostgreSQL Windows Service** | `Get-Service *postgres*` | No services found | **NOT INSTALLED** |
| **PostgreSQL Port** | `127.0.0.1:5432` | TCP Connect failed | **OFFLINE** |
| **MinIO Windows Service** | `Get-Service *minio*` | No services found | **NOT INSTALLED** |
| **MinIO API Port** | `127.0.0.1:9000` | TCP Connect failed | **OFFLINE** |
| **MinIO Console Port** | `127.0.0.1:9001` | TCP Connect failed | **OFFLINE** |
| **M2 Spring Boot Port** | `127.0.0.1:8080` | Port available | **OFFLINE (Awaiting DB)** |
| **M4 AI FastAPI Port** | `127.0.0.1:8000` | Port available & verified | **ONLINE & LIVE VERIFIED** |
| **Windows Shell Elevation** | `[WindowsPrincipal]::IsInRole(Admin)` | `False` (Non-elevated user context) | **STANDARD USER** |

### Infrastructure Option Analysis (Per Section 2)
1. **Option A (Existing Working Local Services):** Evaluated. Port 5432 and Port 9000 are not listening. No background Windows services are registered for PostgreSQL or MinIO.
2. **Option B (Already Available Container Runtime):** Evaluated. Neither Docker Desktop, Podman, nor WSL are installed on this Windows host.
3. **Option C (Compatible Standalone Installations):** Evaluated. Package manager `winget` is present, but installing system-level packages (e.g. `Docker.DockerDesktop` or `PostgreSQL.PostgreSQL.16`) requires Administrator UAC elevation, system-level file modifications, and potential reboots. In accordance with Rule 1 and Step 2 instructions ("*Do not install software, change system-wide settings, or require a reboot without explaining the action and obtaining approval where needed*"), system-wide installations were not executed without prior administrative authorization.

**Resolution Plan:**  
Live verification proceeded by running the standalone Python/Uvicorn runtime for M4 with full live authentication, contract validation, and role-based access control, while conducting static schema validation and standalone compilation for M2 and M3.

---

## 3. Flyway Migration V1.0.7 & Schema Verification (M3)

### Migration Sequence Audit
- **Migration Script:** [`M3_work/IP_DB/database/migrations/V1.0.7__create_inspection_evidence_and_ai_tables.sql`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M3_work/IP_DB/database/migrations/V1.0.7__create_inspection_evidence_and_ai_tables.sql)
- **Preceding Migration:** `V1.0.6__add_secondary_foreign_key_indexes.sql`
- **Verification Script:** [`M3_work/IP_DB/database/tests/06_verify_ai_and_evidence_schema.sql`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M3_work/IP_DB/database/tests/06_verify_ai_and_evidence_schema.sql)
- **Target Schema:** `firesphere`

### Schema Structural Validation
| Table Name | Primary Key | Public UUID | Foreign Keys | Tenant Scoping | RLS Enabled |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `firesphere.inspection_evidence` | `id BIGSERIAL` | `uuid UUID UNIQUE` | `org_id -> organizations(id)`<br>`inspection_id -> inspections(id)` | `org_id BIGINT` | **YES** |
| `firesphere.ai_analysis_runs` | `id BIGSERIAL` | `uuid UUID UNIQUE` | `org_id -> organizations(id)`<br>`inspection_id -> inspections(id)` | `org_id BIGINT` | **YES** |
| `firesphere.ai_findings` | `id BIGSERIAL` | `uuid UUID UNIQUE` | `run_id -> ai_analysis_runs(id)`<br>`evidence_id -> inspection_evidence(id)` | Via Run & Evidence | **YES** |
| `firesphere.ai_officer_reviews` | `id BIGSERIAL` | `uuid UUID UNIQUE` | `finding_id -> ai_findings(id)`<br>`officer_id -> users(id)` | Via Finding | **YES** |
| `firesphere.ai_finding_audits` | `id BIGSERIAL` | `uuid UUID UNIQUE` | `finding_id -> ai_findings(id)`<br>`performed_by -> users(id)` | Via Finding | **YES** |

### Constraints and Business Rules Verified
- **Statutory Advisory Constraint:**  
  `CONSTRAINT chk_ai_finding_advisory CHECK (is_advisory = TRUE)` — Prevents any database insert or update from marking an AI observation as legally binding.
- **Confidence Range Constraint:**  
  `CONSTRAINT chk_ai_finding_confidence CHECK (confidence >= 0.000 AND confidence <= 1.000)`
- **Content Type Constraint:**  
  `CONSTRAINT chk_evidence_content_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp'))`
- **File Size Constraint:**  
  `CONSTRAINT chk_evidence_file_size CHECK (file_size_bytes > 0 AND file_size_bytes <= 10485760)` (Enforces 10MB maximum photographic size).

### Row-Level Security (RLS) Policy Consistency
- Evaluated against M3 baseline `V1.0.5__enable_row_level_security.sql`:
  ```sql
  ALTER TABLE firesphere.inspection_evidence ENABLE ROW LEVEL SECURITY;
  ALTER TABLE firesphere.ai_analysis_runs ENABLE ROW LEVEL SECURITY;

  CREATE POLICY tenant_isolation_policy ON firesphere.inspection_evidence
      FOR ALL
      USING (org_id = firesphere.current_org_id() OR firesphere.is_super_admin())
      WITH CHECK (org_id = firesphere.current_org_id() OR firesphere.is_super_admin());
  ```
- **Live Database Execution Status:** **BLOCKED** due to PostgreSQL daemon being offline on host.

---

## 4. Database Security & Tenant Isolation Analysis

Using the project's security architecture defined in `V1.0.5` and `05_verify_row_level_security.sql`:

1. **Application Role Bypass Check:**
   - The application database user (`firesphere_app` / `firesphere_admin`) is configured in M3 docker-compose with `SET search_path TO firesphere, public`.
   - In production PostgreSQL, application roles must NOT have `BYPASSRLS` privileges.
2. **Session Context Setting:**
   - Context is set using `SELECT firesphere.set_tenant_context(p_org_id BIGINT, p_is_admin BOOLEAN);` which sets PostgreSQL session parameters `app.current_org_id` and `app.is_super_admin`.
3. **Cross-Tenant Leakage Protection:**
   - In `05_verify_row_level_security.sql`, switching session context from Org 1 to Org 2 causes all Org 1 records to return `0` visible rows (`SELECT COUNT(*) = 0`).
   - Cross-tenant updates or inserts are rejected by `WITH CHECK` clauses.

---

## 5. MinIO & Evidence Storage Configuration Analysis

- **Configured Storage Endpoint:** `http://127.0.0.1:9000`
- **Target Bucket:** `firesphere-evidence`
- **Path Template:** `organizations/{orgId}/inspections/{inspectionId}/{evidenceUuid}.{ext}`
- **Cryptographic Hash:** SHA-256 (64 hex characters) computed client-side in M2 and validated against storage byte streams.
- **Backend Service:** Implemented in [`S3EvidenceStorageService.java`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/backend/src/main/java/com/firesphere/backend/document/service/S3EvidenceStorageService.java) using AWS SDK for Java v2 (`software.amazon.awssdk.services.s3.S3Client`).
- **Live Status:** Unverified against live MinIO server due to storage container being offline.

---

## 6. M4 AI FastAPI Service Live HTTP & Security Verification

The M4 AI Service was launched live on `http://127.0.0.1:8000` with strict authentication enabled:
- `AI_SERVICE_AUTH_TOKEN="live-integration-token"`
- `AI_AUTH_ENABLED=true`
- `GEMINI_ENABLED=false`
- `AI_ENABLE_MOCK_EVIDENCE_LOADER=true`

### Live HTTP Execution Test Results

| Test # | Endpoint & Scenario | HTTP Method | Auth Header | Request Body State | Actual HTTP Status | Result |
| :---: | :--- | :---: | :--- | :--- | :---: | :---: |
| **1** | `/health` — Service health | `GET` | None | N/A | **200 OK** | **PASSED** |
| **2** | `/ready` — Component readiness | `GET` | None | N/A | **200 OK** | **PASSED** |
| **3** | `/internal/ai/inspection-analysis` — Missing token | `POST` | None | Valid inspection payload | **401 Unauthorized** | **PASSED** |
| **4** | `/internal/ai/inspection-analysis` — Invalid token | `POST` | `X-AI-Service-Token: invalid-token` | Valid inspection payload | **401 Unauthorized** | **PASSED** |
| **5** | `/internal/ai/inspection-analysis` — Valid token (`testMode: true`) | `POST` | `X-AI-Service-Token: [CONFIGURED]` | Valid inspection payload | **200 OK** | **PASSED** |
| **6** | `/internal/ai/inspection-analysis` — Malformed body | `POST` | `X-AI-Service-Token: [CONFIGURED]` | `inspectionId: "not-a-uuid"`, `evidenceIds: []` | **422 Unprocessable** | **PASSED** |
| **7** | `/internal/ai/findings/{id}/verify` — Unauthorized role | `POST` | `X-Officer-Role: CITIZEN` | Valid verification request | **403 Forbidden** | **PASSED** |
| **8** | `/internal/ai/findings/{id}/verify` — Cross-tenant | `POST` | `X-Tenant-ID: tenant-bravo` | Finding belongs to `tenant-alpha` | **403 Forbidden** | **PASSED** |
| **9** | `/internal/ai/inspection-analysis` — Fail-closed (`testMode: false`) | `POST` | `X-AI-Service-Token: [CONFIGURED]` | Valid payload, `testMode: false` | **200 OK** (0 findings) | **PASSED** |

### Verified Security Invariants in Live Responses
- **Token Verification:** Missing or invalid tokens produce structured JSON:
  ```json
  {
    "status": "FAILED",
    "errorCode": "UNAUTHORIZED_SERVICE",
    "message": "Missing required service authentication token (X-AI-Service-Token or Bearer)."
  }
  ```
- **Role-Based Access Control (RBAC):** Attempts by unauthorized roles (e.g., `CITIZEN`) to verify inspection findings produce HTTP 403:
  ```json
  {
    "status": "FAILED",
    "errorCode": "FORBIDDEN_ROLE",
    "message": "Role 'CITIZEN' is not authorized to verify fire inspection findings. Authorized officer credentials are required."
  }
  ```
- **Tenant Boundary Enforcement:** Attempts by an officer of `tenant-bravo` to review findings owned by `tenant-alpha` produce HTTP 403:
  ```json
  {
    "status": "FAILED",
    "errorCode": "TENANT_ACCESS_FORBIDDEN",
    "message": "Access to finding across tenant boundaries is forbidden.",
    "requestedTenantId": "tenant-bravo",
    "actualTenantId": "tenant-alpha"
  }
  ```
- **Fail-Closed Unapproved Model Execution:** In non-test mode (`testMode: false`), when weights are unapproved (`NOT_APPROVED`), the service fails closed with 0 detections without fabricating findings:
  ```
  [firesphere.ai] PRODUCTION_INFERENCE_DISABLED | Model=firesphere-inspection-cv (v0.3.0-phase3) | Status=NOT_APPROVED | Failing closed with 0 detections.
  ```

---

## 7. M2–M4 Integration Contract & Persistence Architecture

### Synthetic Workflow Mapping

```
[Citizen / Inspector]
        │
        ▼ (Multipart Upload)
[M2: POST /api/v1/inspections/{id}/evidence]
        │
        ├── 1. Computes SHA-256 hash
        ├── 2. Stores in S3/MinIO bucket ('firesphere-evidence')
        └── 3. Persists row in 'firesphere.inspection_evidence' (BIGINT id, UUID uuid)
        │
        ▼
[M2: POST /api/v1/noc/applications/{id}/ai-analysis]
        │
        ├── 4. Resolves evidence UUIDs from DB
        ├── 5. Inserts 'ai_analysis_runs' (status: 'PROCESSING') [TRANSACTION 1]
        │
        ▼ (Outbound HTTP Outside Transaction Boundary)
[M4: POST /internal/ai/inspection-analysis]
        │
        ├── 6. Authenticates 'X-AI-Service-Token'
        ├── 7. Executes Model Adapter (Fails closed if NOT_APPROVED)
        └── 8. Returns InspectionAnalysisResponse (JSON)
        │
        ▼
[M2: AiPersistenceHelper.recordSuccess]
        │
        ├── 9. Updates 'ai_analysis_runs' (status: 'COMPLETED') [TRANSACTION 2]
        └── 10. Inserts advisory findings into 'ai_analysis_findings'
        │
        ▼
[Fire Safety Officer Review]
        │
        ▼
[M2: POST /api/v1/noc/applications/{appId}/ai-analysis/{runId}/findings/{findingId}/review]
        │
        ├── 11. Enforces @PreAuthorize("hasRole('OFFICER')")
        ├── 12. Records determination in 'ai_officer_reviews'
        └── 13. Appends immutable audit entry in 'ai_finding_audits'
```

### Route Discrepancy Resolution
- **M2 Implemented Route:**  
  `POST /api/v1/noc/applications/{applicationId}/ai-analysis/{runId}/findings/{findingId}/review`  
  Protected by Spring Security: `@PreAuthorize("hasRole('OFFICER') or hasRole('ADMIN')")`.
- **M4 Implemented Internal Routes:**  
  `POST /internal/ai/inspection-analysis/{inspection_id}/verify-finding`  
  `POST /internal/ai/findings/{finding_id}/verify`  
  Protected by gateway headers: `X-Officer-Role`, `X-Officer-ID`, `X-Tenant-ID`.
- **Resolution:** The M2 backend acts as the authoritative application gateway for human officers, authenticates users via JWT session tokens, and executes internal verification calls or persists directly to the database.

---

## 8. Test Execution Summary

### 1. Java Compilation (M2 Backend)
- **Compiler:** `javac 26.0.2.1` (Java SE 26)
- **Files:** 123 production Java source files
- **Command:**
  ```powershell
  javac -cp "target/lib/BOOT-INF/lib/*;target/classes" -d target/classes $(Get-ChildItem -Recurse -Filter *.java src/main/java)
  ```
- **Exit Code:** `0` (Zero compiler errors)

### 2. Pytest Automated Test Suites (M4 AI Service)
- **Command:**
  ```powershell
  pytest tests/test_m2_m4_contract_specification.py tests/test_inspection_analysis.py tests/test_evidence_validation.py tests/test_phase10_production_integration.py tests/test_repository_contracts.py -v
  ```
- **Passed:** **37**
- **Failed:** **0**
- **Skipped:** **0**
- **Duration:** 0.66s

### 3. Live HTTP Security & Verification Test Suite (M4 AI Service)
- **Script:** [`scratch/test_m4_auth_and_review.py`](file:///C:/Users/athar/.gemini/antigravity-ide/brain/923e41e5-796a-4791-92f1-ccf4bb955039/scratch/test_m4_auth_and_review.py)
- **Total Scenarios:** 9
- **Passed:** **9**
- **Failed:** **0**

### 4. Categorized Test Verification Matrix
| Category | Mechanism | Passed | Failed | Blocked |
| :--- | :--- | :---: | :---: | :---: |
| **Java Static Compilation** | `javac` 26.0.2.1 | 123 files | 0 | 0 |
| **M4 Contract & Unit Tests** | `pytest` | 37 tests | 0 | 0 |
| **Live M4 HTTP & Auth** | Python `urllib` / Live Uvicorn | 9 tests | 0 | 0 |
| **Flyway DDL Static Check** | SQL inspection | 8 migrations | 0 | 0 |
| **Live DB Migration Run** | Flyway against live DB | 0 | 0 | **BLOCKED (Offline DB)** |
| **Live MinIO S3 Upload** | S3 API client against MinIO | 0 | 0 | **BLOCKED (Offline MinIO)** |
| **Live DB Row Persistence** | SQL queries against live DB | 0 | 0 | **BLOCKED (Offline DB)** |
| **Real Model Inference** | Model inference with weights | 0 | 0 | **HELD (Safety Gate `NOT_APPROVED`)** |

---

## 9. Safe Commands for Future Startup and Shutdown

When Docker Desktop or local PostgreSQL and MinIO services are started, use the following commands:

### Startup Sequence
```powershell
# 1. Start Infrastructure Stack (PostgreSQL 16 + MinIO + Redis)
cd "C:\Users\athar\OneDrive\Desktop\Firesphere.ai\M3_work\IP_DB"
docker compose up -d

# 2. Verify Database Health
# Port 5432 should respond; Flyway executes V1.0.0 through V1.0.7

# 3. Start M4 AI FastAPI Service
cd "C:\Users\athar\OneDrive\Desktop\Firesphere.ai\ai-service"
$env:AI_SERVICE_AUTH_TOKEN = "live-integration-token"
$env:AI_AUTH_ENABLED = "true"
$env:GEMINI_ENABLED = "false"
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000

# 4. Start M2 Spring Boot Backend
cd "C:\Users\athar\OneDrive\Desktop\Firesphere.ai\M2_work\Firesphere m2\backend"
java -jar target/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

### Shutdown Sequence
```powershell
# Stop M2 and M4 processes (Ctrl+C in respective consoles)

# Stop Docker Services
cd "C:\Users\athar\OneDrive\Desktop\Firesphere.ai\M3_work\IP_DB"
docker compose stop
```

---

## 10. Remaining Blockers & Concrete Next Steps

1. **Host Container Runtime:** Install Docker Desktop (or launch local native PostgreSQL 16 + PostGIS 3.4 and MinIO executables) on the development host.
2. **Execute Flyway Migration:** Run `V1.0.7` against the active local database and execute `06_verify_ai_and_evidence_schema.sql`.
3. **Live End-to-End Verification:** With all four services online (PostgreSQL, MinIO, M2, and M4), execute an end-to-end evidence upload and verify row persistence in `firesphere.inspection_evidence` and `firesphere.ai_analysis_runs`.

---

## 11. Final Verdict

# **PHASE 3C PARTIALLY COMPLETE**

### Authoritative Verdict Statement:
- **Verified Complete:**
  - M4 FastAPI AI service is **online, healthy, and verified live via HTTP** across 9 distinct scenarios.
  - Service token authentication (`401` on missing/invalid token, `200` on valid token) is **live verified**.
  - Role-based authorization (`403` on `CITIZEN` role) and cross-tenant boundary isolation (`403` on mismatched tenant) are **live verified**.
  - Fail-closed execution under unapproved model status (`NOT_APPROVED`) is **live verified**.
  - 37 automated M4 pytest tests passed in 0.66s.
  - All 123 M2 Java source files compile with 0 errors under JDK 26.
  - Flyway migration `V1.0.7` and verification script `06_verify_ai_and_evidence_schema.sql` are structurally validated.
- **Unverified / Blocked:**
  - PostgreSQL (port 5432) and MinIO (port 9000) remain offline due to the absence of a container runtime (Docker/WSL) or native database daemons on the host OS.
  - Live Flyway DDL execution against an active PostgreSQL engine and live SQL query verification of persisted rows cannot be completed until the database daemon is online.
