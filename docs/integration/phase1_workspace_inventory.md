# FireSphere.ai — Phase 1 Workspace Inventory

**Document Identifier:** `FS-DOC-PHASE1-WORKSPACE-INVENTORY-20261010`  
**Phase:** 1 — Workspace Audit and Integration Design  
**Local Workspace Root:** `C:\Users\athar\OneDrive\Desktop\Firesphere.ai`  
**Audit Date:** 2026-10-10  
**Classification:** Internal Architectural Audit & Forensic Baseline  

---

## 1. Executive Summary

This inventory provides an exhaustive, evidence-based catalog of all implementations present within the local workspace root (`C:\Users\athar\OneDrive\Desktop\Firesphere.ai`). Each subsystem was inspected through source code analysis, dependency manifests, build descriptors, database migrations, configuration templates, and automated test suites. 

### Key Workspace Findings:
1. **Ownership Realignment:** Folder names alone do not convey the true technical state or current canonical ownership.
   - **M1 (Citizen & Officer Applications):** Implemented in [`M1_work/`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M1_work) across `admin-console`, `mobile`, and `packages/api-client`.
   - **M2 (Spring Boot Backend Core & NOC):** Implemented in [`M2_work/Firesphere m2/backend`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/backend) (Java 21, Spring Boot 3.2.4). The folder also contains an unpacked delivery directory [`Aaditya Morde/`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/Aaditya%20Morde), bundled tooling (`apache-maven-3.9.6`, `jdk-21.0.2+13`), and legacy Express.js/MariaDB prototype files.
   - **M3 (Database, Infrastructure & DevOps):** Implemented in [`M3_work/IP_DB`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M3_work/IP_DB) containing PostgreSQL/PostGIS Flyway migrations (`V1.0.0` through `V1.0.6`), test suites, Docker configs, and architecture decision records.
   - **M4 (AI/ML, Computer Vision, VLM, and AI Orchestration):** Two copies exist: [`ai-service/`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service) at workspace root and [`ATHARV/ai-service`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ATHARV/ai-service). Forensic comparison proves that **`ai-service/` at root is the canonical working copy** (496 files vs 440 files, contains virtual environment `.venv`, newer Phase 4 reconciliation experiments, and test isolation fixtures), while `ATHARV/` is the initial submission snapshot.
   - **M5 (GIS, Operations, Fleet, Dispatch, Inventory):** Implemented in [`M5_work/Industry M5`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M5_work/Industry%20M5) as an independent Spring Boot 3.3.4 operational service (`ai.firesphere.firesphere-operations`).

---

## 2. Canonical Member Inventory Table

| Member | Component | Directory | Entry Point | Build Configuration | Tests | Current Status | Evidence & Citations | Known Dependencies |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **M1** | Administrative Web Console | [`M1_work/admin-console`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M1_work/admin-console) | `src/main.tsx`, `src/App.tsx` | `admin-console/package.json`, `vite.config.ts` | Vitest: `src/tableModel.test.ts`, `src/dev/mockServices.test.ts` | `MOCK_ONLY` | [`M1_work/IMPLEMENTATION_STATUS.md`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M1_work/IMPLEMENTATION_STATUS.md#L21) | M2 Backend APIs (pending contract) |
| **M1** | Citizen & Officer Mobile App | [`M1_work/mobile`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M1_work/mobile) | `src/App.tsx`, `index.js` | `mobile/package.json`, `app.json` | Vitest: `authAdapter.test.ts`, `nocModel.test.ts`, `database.test.ts`, `syncQueueModel.test.ts` | `MOCK_ONLY` | [`M1_work/IMPLEMENTATION_STATUS.md`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M1_work/IMPLEMENTATION_STATUS.md#L17-L20) | M2 Backend APIs, OS SecureStore, SQLite |
| **M1** | Client Shared API Client | [`M1_work/packages/api-client`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M1_work/packages/api-client) | `src/client.ts` | `package.json`, TypeScript | `src/client.retry.test.ts` | `DISCONNECTED` | [`packages/api-client/src/client.ts`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M1_work/packages/api-client/src/client.ts) (fails closed if `VITE_API_CONTRACTS` unset) | M2 Backend REST contract |
| **M2** | Core Backend & Fire NOC Service | [`M2_work/Firesphere m2/backend`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/backend) | `com.firesphere.backend.FiresphereBackendApplication` | `pom.xml` (Maven, Java 21, Spring Boot 3.2.4) | JUnit 5: 22 test classes (`AiAnalysisTests`, `NocApplicationTests`, `SecurityTests`) | `IMPLEMENTED_NOT_RUNTIME_VERIFIED` | [`M2_work/Firesphere m2/backend/pom.xml`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/backend/pom.xml), [`02_MEMBER_2_BACKEND_CORE_NOC.md`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/Aaditya%20Morde/docs/02_MEMBER_2_BACKEND_CORE_NOC.md) | M3 PostgreSQL, M4 AI Service, M1 Frontend |
| **M3** | Relational Database & Migrations | [`M3_work/IP_DB`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M3_work/IP_DB) | PostgreSQL 15+ / PostGIS / Flyway | Flyway SQL migrations (`V1.0.0` – `V1.0.6`), `docker-compose.yml` | SQL scripts: `database/tests/` (01 to 05 verification SQLs) | `IMPLEMENTED_NOT_RUNTIME_VERIFIED` | [`M3_work/IP_DB/database/migrations`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M3_work/IP_DB/database/migrations), [`DATABASE_AI_SCHEMA_BLOCKER.md`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M3_work/IP_DB/DATABASE_AI_SCHEMA_BLOCKER.md) | PostgreSQL Engine, PostGIS Extension |
| **M4** | AI Microservice (Canonical Working Copy) | [`ai-service/`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service) (root) | `app/main.py` (FastAPI), `app/worker.py` (Worker) | `requirements.txt`, `Dockerfile`, Python 3.13 | Pytest: 57 test files, **447 passed** (verified runtime execution) | `IMPLEMENTED_NOT_RUNTIME_VERIFIED` (Service verified; CV gated `NOT_APPROVED`; Gemini disabled) | [`ai-service/app/main.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/main.py), [`model_approval_gate.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/model_approval_gate.py) | M2 Backend Orchestration, PyTorch, S3/MinIO |
| **M4** | AI Submission Snapshot (Archive) | [`ATHARV/ai-service`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ATHARV/ai-service) | `app/main.py`, `app/worker.py` | `requirements.txt`, `Dockerfile` | Pytest: 52 test files | `MIRRORED_SNAPSHOT` | [`ATHARV/README.md`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ATHARV/README.md) (Member 4 submission package) | Superseded by root `ai-service/` |
| **M5** | Operations, GIS, Fleet & Dispatch | [`M5_work/Industry M5`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M5_work/Industry%20M5) | `ai.firesphere.FireSphereApplication` | `pom.xml` (Maven, Java 21, Spring Boot 3.3.4) | JUnit 5: 50+ test classes across GIS, Fleet, Dispatch, Inventory | `IMPLEMENTED_NOT_RUNTIME_VERIFIED` | [`M5_work/Industry M5/pom.xml`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M5_work/Industry%20M5/pom.xml), [`gis-postgis.md`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M5_work/Industry%20M5/docs/member5/gis-postgis.md) | PostgreSQL / PostGIS, Mock Map Providers |

---

## 3. Forensic Analysis of M4 Duplication (`ai-service/` vs `ATHARV/`)

An essential requirement of Phase 1 is resolving whether multiple directories representing M4 are duplicate implementations or copies, and establishing the canonical working tree.

### 3.1 File and Hash Comparison
- **Root `ai-service/` file count:** 496 files (excluding `.venv`, `__pycache__`, and `.pytest_cache`).
- **`ATHARV/ai-service/` file count:** 440 files (excluding cache).
- **Files unique to `ATHARV/ai-service`:** 0.
- **Files unique to root `ai-service/`:** 56 files, including:
  1. Virtual environment `.venv` with installed PyTorch, OpenCV, FastAPI, and test runner.
  2. Test isolation fixture in [`ai-service/tests/conftest.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/tests/conftest.py#L35-L42) (`clean_job_manager_state` autouse fixture).
  3. Controlled CV sensitivity experiment script and outputs: [`artifacts/phase4_cv_experiment/run_experiment.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/phase4_cv_experiment/run_experiment.py) and `experiment_results.json`.
  4. Bit-for-bit CV reconciliation scripts: [`artifacts/phase4_cv_reconciliation/reconcile_and_verify.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/phase4_cv_reconciliation/reconcile_and_verify.py).
  5. Dataset storage hierarchy: `datasets/fire_safety/`.
  6. Phase 8.10 execution logs: `artifacts/phase8_10/logs/phase8_10_execution.log`.
- **Documentation comparison:**
  - Root [`docs/`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/docs) contains 70 technical reports.
  - [`ATHARV/docs/`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ATHARV/docs) contains 65 technical reports.
  - The 5 reports present only in root `docs/` are:
    1. [`m2_m4_contract_compatibility_specification.md`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/docs/m2_m4_contract_compatibility_specification.md)
    2. [`cv_evaluation_reconciliation_report_v2.md`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/docs/cv_evaluation_reconciliation_report_v2.md)
    3. [`cv_model_quality_investigation_report.md`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/docs/cv_model_quality_investigation_report.md)
    4. [`phase4_evaluation_and_rollout_report.md`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/docs/phase4_evaluation_and_rollout_report.md)
    5. [`phase4_member4_followup_report.md`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/docs/phase4_member4_followup_report.md)

### 3.2 Canonical Determination Verdict
**RESOLVED:** Root `ai-service/` and root `docs/` represent the **canonical, most evolved working copy of Member 4's deliverables**. `ATHARV/` is the frozen upstream delivery archive created during Member 4's initial submission (`ATHARV/README.md`). 

**Recommendation:** For all subsequent phases, root `ai-service/` must be treated as the canonical development tree. `ATHARV/` must be preserved unmodified as an immutable archival snapshot.

---

## 4. Anomalies, Stale Files, and Conflicting Definitions

### 4.1 Stale Express.js & MariaDB Artifacts in M2_work
Inside [`M2_work/Firesphere m2`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2), the following files are legacy artifacts from an earlier prototype that contradict the approved architecture:
- `M2_work/Firesphere m2/backend/server.js` (Express.js 4.19 server listening on port 5000)
- `M2_work/Firesphere m2/backend/package.json`
- `M2_work/Firesphere m2/database/firesphere_ai_mariadb.sql` (MariaDB/MySQL DDL)
- `M2_work/Firesphere m2/frontend/` (Legacy React Vite demo)
- `M2_work/Firesphere m2/mobile/` (Legacy React Native demo)

These files must be ignored. The genuine Member 2 implementation is the Spring Boot Java application in [`M2_work/Firesphere m2/backend/src`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/backend/src) and its documentation in [`M2_work/Firesphere m2/Aaditya Morde/docs`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/Aaditya%20Morde/docs).

### 4.2 Git Repository State
The workspace root is **not a Git repository**. Only [`M1_work/.git`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M1_work/.git) contains an isolated local Git history. Subsystems M2, M3, M4, and M5 were unpacked directly into the workspace without active Git tracking.
