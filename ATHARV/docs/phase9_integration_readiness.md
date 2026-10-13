# FireSphere.ai — Phase 9 Integration Contract Readiness & Phase 10 Handoff

**Document Version:** `1.0.0-phase9`  
**Governing Phase:** Phase 9 — Final AI Model Selection, Calibration, Evaluation & Validation  
**Target Milestone:** Phase 10 — Production AI Integration  
**Author:** AI/ML Engineering Agent  
**Status:** `READY_FOR_PHASE_10_INTEGRATION`  
**Date:** 2026-10-09  

---

## 1. Executive Summary & Architectural Scope

Phase 9 validates that the AI service subsystems, data schemas, and API contracts are fully structured, tested, and compatible for production integration in Phase 10. No working subsystems require refactoring or rewriting. The integration surface is stabilized around standard REST APIs, Redis asynchronous job queues, MinIO/S3 object storage, and PostgreSQL/PostGIS persistence schemas.

---

## 2. Integration Contract Audit Matrix

| Subsystem Contract | Technical Interface | Validated Behaviors | Phase 10 Readiness Status |
| :--- | :--- | :--- | :---: |
| **Spring Boot Core Backend** | HTTP REST (`/internal/ai/v1/analyze`, `/jobs/{id}`) | UUIDv4 correlation, ISO-8601 timestamps, tenant header scoping | **COMPATIBLE** |
| **PostgreSQL & PostGIS** | Relational Finding & Spatial Geometries | Normalized bounding boxes map directly to PostGIS polygons; idempotent runs | **COMPATIBLE** |
| **Redis Asynchronous Jobs** | Celery / Redis Queue Broker | Asynchronous job dispatch, exponential backoff retry with jitter, dead-letter queue | **COMPATIBLE** |
| **MinIO / S3 Storage** | Evidence Client (`boto3` / HTTP streaming) | 20MB image limit enforcement, per-evidence fault isolation, presigned URLs | **COMPATIBLE** |
| **Officer Review Workflow** | Draft Report & Advisory Findings | `REQUIRES_OFFICER_VERIFICATION`, editable explanations, verification guidance | **COMPATIBLE** |
| **Security & Isolation** | FastAPI Middleware & Config | Zero hardcoded secrets, tenant header isolation, robust 422 input validation | **COMPATIBLE** |

---

## 3. Subsystem Contract Specifications

### 3.1 Spring Boot Backend Interoperability
- **Request Payloads:** `POST /internal/ai/v1/analyze` accepts:
  ```json
  {
    "inspectionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "evidenceIds": ["7c9e6679-7425-40de-944b-e07fc1f90ae7"],
    "occupancyContext": "COMMERCIAL_OFFICE",
    "historicalContext": "NOC_RENEWAL_ANNUAL",
    "testMode": false
  }
  ```
- **Response Payloads:** Synchronous 202 Accepted with `jobId` for async polling, or immediate `AIRun` response in synchronous batch mode.
- **Traceability Guarantee:** Every finding contains `findingId`, `evidenceId`, `model`, `modelVersion`, and `createdAt`.

### 3.2 Asynchronous Job Processing & Retry Mechanics
In [`app/jobs/manager.py`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/jobs/manager.py) and [`app/jobs/retry.py`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/jobs/retry.py):
- **Idempotency:** Re-submitting an identical `inspectionId` with matching evidence IDs returns existing completed runs or active in-flight jobs.
- **Fault Isolation:** Corrupted or oversized image files in a multi-image inspection do **NOT** fail the entire inspection run; failed images are logged in `evidenceProcessingSummary.failedEvidenceIds` while valid images are analyzed normally.
- **Exponential Backoff:** Transient network or storage connection dropouts trigger automated retries ($2^n \times \text{backoff\_factor} \pm \text{jitter}$).

### 3.3 Security, Credential Safety & Input Validation
- **Zero Secret Leakage:** All connection URIs (MinIO, Redis, PostgreSQL) are injected via environment variables through [`Settings`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/core/config.py#L8-L211). No credentials exist in code or repository artifacts.
- **Input Guardrails:** 
  - File size strictly capped at 20MB via [`CVPreprocessor`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/cv/preprocessor.py).
  - Images smaller than $64 \times 64$ pixels rejected with explicit 422 errors.
  - Image decompression bomb protection enforced via PIL limits.

---

## 4. Phase 10 Integration Handoff Requirements

When the backend engineering team commences Phase 10 (Production AI Integration), the following specifications are frozen and ready:
1. **Model Weights Staging:** Production weights path defaults to `None`. When authorized, model weights will be mounted into `AI_MODEL_WEIGHTS_PATH`.
2. **Finding Persistence:** The Spring Boot backend must persist AI findings into the `inspection_ai_findings` table with compliance status `REQUIRES_OFFICER_VERIFICATION`.
3. **Officer UI Rendering:** The web frontend must render bounding boxes as advisory overlays with checkbox confirmation for inspecting officers.
4. **No Blockers:** The AI service has zero pending internal architectural refactors.
