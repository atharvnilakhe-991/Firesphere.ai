# FireSphere.ai — Multi-Phase Integration Roadmap

**Document Identifier:** `FS-DOC-PHASE1-ROADMAP-20261010`  
**Phase:** 1 — Workspace Audit and Integration Design  
**Local Workspace Root:** `C:\Users\athar\OneDrive\Desktop\Firesphere.ai`  
**Date:** 2026-10-10  
**Classification:** Strategic Engineering Plan & Dependency-Aware Execution Roadmap  

---

## 1. Executive Summary

This roadmap establishes a dependency-aware, phased execution schedule for integrating the verified deliverables of all five members into a unified, production-oriented FireSphere.ai platform. 

Implementation is **strictly prohibited in Phase 1**. This plan serves as the approved technical contract for subsequent execution phases.

### Master Dependency Sequence:
```text
Phase 1: Audit & Design (CURRENT — AUDIT ONLY)
   │
   ├──────────────────────────────┐
   ▼                              ▼
Phase 2: API Contract Alignment   Phase 3: Database & Persistence
(M2 ↔ M4 Contract)                (M3 Schema & PK Harmonization)
   │                              │
   └──────────────┬───────────────┘
                  ▼
          Phase 4: CV Model Integration (Gated Baseline)
                  │
                  ▼
          Phase 5: VLM Integration (Gemini Adapter & Prompts)
                  │
                  ▼
          Phase 6: Multi-Model Orchestration & Reconciliation
                  │
                  ▼
          Phase 7: M1 Frontend & M5 GIS Integration
                  │
                  ▼
          Phase 8: Security, Authorization & Audit Hardening
                  │
                  ▼
          Phase 9: End-to-End Integration Testing
                  │
                  ▼
          Phase 10: Controlled Deployment & Production Readiness
```

---

## 2. Phase-by-Phase Task Breakdown

### PHASE 2 — API Contract Alignment (M2 ↔ M4 Harmonization)

#### Task 2.1 — Align M2 Backend AI Client with M4 Inspection API
- **Task ID:** `TSK-P2-01`
- **Priority:** **P0 (Critical Blocker)**
- **Responsible Member:** Member 2 (with Member 4 review)
- **Target Files:**
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/client/HttpAiAnalysisClient.java`
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/dto/ExternalAiRequest.java`
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/dto/ExternalAiResponse.java`
  - `M2_work/Firesphere m2/backend/src/main/resources/application.yml`
- **Required Dependencies:** None (Independent task).
- **Team Decisions / Approvals:** Ratification of `FS-SPEC-M2-M4-CONTRACT-20261010` proposed contract.
- **Implementation Scope:**
  - Update `HttpAiAnalysisClient.java` endpoint path to `/internal/ai/inspection-analysis`.
  - Update request header to `X-AI-Service-Token: ${ai.service.api-key}`.
  - Refactor `ExternalAiRequest` to provide `inspectionId: UUID`, `evidenceIds: List<UUID>`, `testMode: Boolean`, `occupancyContext: String`.
  - Refactor `ExternalAiResponse` into a rich DTO capturing `Finding` attributes (type, confidence, severity, explanation, bounding box).
- **Acceptance Criteria:**
  - Spring Boot MockMvc / WireMock contract tests pass with 100% deserialization accuracy against M4 JSON fixtures.
- **Testing Requirements:**
  - *Unit Test:* `HttpAiAnalysisClientTests.java` verifying header construction and JSON serialization.
  - *Contract Test:* `M2M4WireMockContractTests.java` verifying compatibility with M4 schema.
- **Risks & Rollback:** Low risk; isolated to client package. Backup existing files before edit.

#### Task 2.2 — Add Flexible Header Compatibility to M4 FastAPI Service
- **Task ID:** `TSK-P2-02`
- **Priority:** **P1**
- **Responsible Member:** Member 4
- **Target Files:**
  - `ai-service/app/core/auth.py`
  - `ai-service/app/api/router.py`
- **Required Dependencies:** None.
- **Team Decisions / Approvals:** Approve dual-header acceptance (`X-AI-Service-Token` and `X-API-KEY`).
- **Implementation Scope:**
  - Add `X-API-KEY` alias to `verify_service_token` in `auth.py`.
- **Acceptance Criteria:** Both `curl -H "X-AI-Service-Token: ..."` and `curl -H "X-API-KEY: ..."` succeed.

---

### PHASE 3 — Database and Persistence Integration

#### Task 3.1 — Reconcile Primary Key Strategy (UUID vs BIGSERIAL)
- **Task ID:** `TSK-P3-01`
- **Priority:** **P0 (Critical Blocker)**
- **Responsible Member:** Member 3 & Member 2
- **Target Files:**
  - `M3_work/IP_DB/database/migrations/V1.0.1__core_tenancy_and_identities.sql` through `V1.0.6`
  - Alternatively: `V1.0.0_uuid__...` or `V2.0.0__harmonized_uuid_schema.sql`
- **Required Dependencies:** Team approval of UUID architecture.
- **Team Decisions / Approvals:** Approve Strategy 1 (Standardize on UUID primary keys across all services).
- **Implementation Scope:**
  - Update M3 Flyway migration DDL to define `id UUID PRIMARY KEY DEFAULT gen_random_uuid()` and `org_id UUID REFERENCES organizations(id)`.
- **Acceptance Criteria:**
  - Migrations run clean on blank PostgreSQL 15 database.
  - `ddl-auto: validate` on Spring Boot starts successfully without type mismatch exceptions.

#### Task 3.2 — Author Migration `V1.0.7__ai_insights_and_reviews.sql`
- **Task ID:** `TSK-P3-02`
- **Priority:** **P0 (Critical Blocker)**
- **Responsible Member:** Member 3 (with Member 2 and Member 4 sign-off)
- **Target Files:**
  - `M3_work/IP_DB/database/migrations/V1.0.7__ai_insights_and_reviews.sql`
- **Required Dependencies:** `TSK-P3-01`.
- **Team Decisions / Approvals:** Ratification of ADR-005 resolution schema.
- **Implementation Scope:**
  - Create tables: `firesphere.ai_analysis_runs`, `firesphere.ai_findings`, `firesphere.ai_officer_reviews`.
  - Apply Row-Level Security policies matching V1.0.5.
  - Add GIN indexes on `bounding_box_json` and `summary_json`.
- **Acceptance Criteria:** Resolves ADR-005. Tables visible in `information_schema.tables`.

---

### PHASE 4 — Computer Vision Model Integration

#### Task 4.1 — Package & Load Phase 8.4 Baseline Checkpoint
- **Task ID:** `TSK-P4-01`
- **Priority:** **P1**
- **Responsible Member:** Member 4
- **Target Files:**
  - `ai-service/app/core/config.py`
  - `ai-service/app/models/model_loader.py`
  - `ai-service/app/models/model_approval_gate.py`
- **Required Dependencies:** `ai-service/artifacts/phase8_4/checkpoints/best.pt`.
- **Team Decisions / Approvals:** Confirm `NOT_APPROVED` gate invariant remains active.
- **Implementation Scope:**
  - Configure `AI_MODEL_WEIGHTS_PATH=artifacts/phase8_4/checkpoints/best.pt`.
  - Verify `ModelLoader` executes forward pass on test image.
  - Ensure `ModelApprovalGate` enforces `ADVISORY_ONLY` on detected extinguishers.
- **Acceptance Criteria:** `pytest tests/test_model_integration.py` passes with weights loaded.

---

### PHASE 5 — Vision-Language Model (VLM) Integration

#### Task 5.1 — Configure & Verify Gemini Provider Adapter
- **Task ID:** `TSK-P5-01`
- **Priority:** **P2**
- **Responsible Member:** Member 4
- **Target Files:**
  - `ai-service/app/models/gemini_provider.py`
  - `ai-service/app/models/gemini_prompts.py`
- **Required Dependencies:** Controlled API credentials in `.env` (strictly outside Git).
- **Team Decisions / Approvals:** Approval to run controlled non-production visual test on sample fire inspection photos.
- **Implementation Scope:**
  - Test structured JSON parsing on standard resolution images.
  - Validate prompt injection resistance against adversarial signage.
- **Acceptance Criteria:** Gemini returns validated `GeminiAnalysisResult` matching Pydantic schema.

---

### PHASE 6 — Multi-Model Orchestration and Finding Reconciliation

#### Task 6.1 — Connect Dual-Model Inference Pipeline to M2 Callback
- **Task ID:** `TSK-P6-01`
- **Priority:** **P1**
- **Responsible Member:** Member 4 & Member 2
- **Target Files:**
  - `ai-service/app/services/inspection_orchestrator.py`
  - `ai-service/app/intelligence/reconciliation.py`
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/service/AiOrchestrationService.java`
- **Required Dependencies:** `TSK-P2-01`, `TSK-P4-01`, `TSK-P5-01`.
- **Implementation Scope:**
  - `InspectionOrchestrator` executes both CV and VLM passes.
  - Reconciles findings, scores risk, and returns advisory report draft to M2.
  - M2 saves findings into `ai_findings` table.
- **Acceptance Criteria:** End-to-end integration test runs from M2 through M4 and writes to PostgreSQL.

---

### PHASE 7 — M1 Frontend & M5 GIS Integration

#### Task 7.1 — Connect M1 Mobile App to M2 Inspection Endpoints
- **Task ID:** `TSK-P7-01`
- **Priority:** **P1**
- **Responsible Member:** Member 1 & Member 2
- **Target Files:**
  - `M1_work/packages/api-client/src/client.ts`
  - `M1_work/mobile/src/features/officer/OfficerInspectionFlow.tsx`
  - `M1_work/mobile/src/storage/syncQueue.ts`
- **Required Dependencies:** `TSK-P2-01`, `TSK-P3-01`.
- **Implementation Scope:**
  - Configure `VITE_API_BASE_URL` to point to M2.
  - Replace mock services with authenticated REST calls.
  - Wire officer review action to `POST /api/v1/inspections/{id}/findings/{id}/verify`.
- **Acceptance Criteria:** Officer mobile app displays real inspection findings fetched from M2.

#### Task 7.2 — Integrate M5 Navigation Handoff into M1 Officer App
- **Task ID:** `TSK-P7-02`
- **Priority:** **P2**
- **Responsible Member:** Member 5 & Member 1
- **Target Files:**
  - `M5_work/Industry M5/src/main/java/ai/firesphere/gis/foundation/controller/GisFoundationController.java`
  - `M1_work/mobile/src/features/officer/OfficerInspectionFlow.tsx`
- **Required Dependencies:** None (Can proceed in parallel).
- **Implementation Scope:**
  - M1 mobile app invokes M5 navigation endpoint to generate RFC-5870 `geo:` URI for assigned inspection coordinates.
- **Acceptance Criteria:** Tapping "Navigate to Site" triggers device map chooser.

---

### PHASE 8 — Security, Authorization, and Auditability Hardening
- **Tasks:**
  - Parameterize CORS origins in M2 (`TSK-P8-01`).
  - Enforce server-side `@PreAuthorize` on all officer actions (`TSK-P8-02`).
  - Wire database audit logging triggers to capture officer finding confirmations (`TSK-P8-03`).

### PHASE 9 — End-to-End Integration Testing
- **Tasks:**
  - Execute complete citizen application $\rightarrow$ document upload $\rightarrow$ checklist generation $\rightarrow$ inspection assignment $\rightarrow$ AI analysis run $\rightarrow$ officer verification $\rightarrow$ statutory approval workflow across live Dockerized containers.

### PHASE 10 — Controlled Deployment and Readiness Assessment
- **Tasks:**
  - Produce final production deployment manifests (Kubernetes / Docker Compose).
  - Verify fail-closed behavior under simulated AI service outage.
  - Final sign-off by all five members.

---

## 3. Parallelization Matrix

```text
[Sprint 1]
Parallel Stream A (Backend/AI): Task 2.1 (M2 Client) + Task 2.2 (M4 Header)
Parallel Stream B (Database):   Task 3.1 (UUID Schema) + Task 3.2 (AI Migration)
Parallel Stream C (GIS/Client): Task 7.2 (M5 Navigation Handoff to M1)

[Sprint 2] (Requires Sprint 1 Completion)
Stream A: Task 4.1 (CV Baseline) + Task 5.1 (Gemini VLM)
Stream B: Task 6.1 (Orchestration & Reconciliation)
Stream C: Task 7.1 (M1 Mobile to M2 Backend)

[Sprint 3]
Security Hardening (Phase 8) + End-to-End Testing (Phase 9) + Release Readiness (Phase 10)
```
