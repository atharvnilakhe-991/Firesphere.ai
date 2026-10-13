# FireSphere.ai — Phase 10: Production AI Integration Specification

**Phase:** 10 — Production AI Integration  
**Owner:** Member 4 — AI/ML, Computer Vision & AI Productivity  
**Date:** October 9, 2026  
**Service Version:** `0.10.0-phase10`  
**Status:** `PHASE_10_COMPLETED_PRODUCTION_INTEGRATION_HANDOFF`  
**Production Model Approval Status:** `NOT_APPROVED`  
**Calibration Status:** `NOT_CALIBRATED`  

---

## 1. Executive Summary & Regulatory Context

Phase 10 completes the integration of the FireSphere Computer Vision & Safety Intelligence AI microservice into the enterprise FireSphere inspection platform. In strict accordance with the project governance roadmap, this phase integrates the AI service **without misrepresenting unapproved model weights as production-ready**.

### Permanent Architectural & Regulatory Invariants
1. **Reference Baseline Control:** The Phase 8.4 baseline model (`best.pt`, SHA-256: `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666`) is retained as the authoritative reference baseline. Its production approval status remains strictly `NOT_APPROVED`.
2. **Fail-Closed Production Policy:** When production inference (`testMode=False`) is invoked, the service fails closed with 0 detections. No fabricated detections are ever returned.
3. **Capability Gating:** `FIRE_EXTINGUISHER` and `EXIT_SIGN` are gated as `ADVISORY_ONLY`. `EXIT_DOOR` and `OBSTACLE` have 0 test true positives and are strictly gated as `UNSUPPORTED`, preventing them from generating actionable operational findings.
4. **Prohibition of Autonomous Certification:** Programmatic validators strictly reject `AI_APPROVED`, `AI_REJECTED`, `APPROVED`, or `REJECTED`. The AI service never grants or rejects a Fire No Objection Certificate (NOC).
5. **Absence of Detections $\neq$ Compliance:** Zero detections in an inspection photograph does not imply building compliance. Physical on-site inspection by an authorized human Fire Safety Officer is mandatory.

---

## 2. End-to-End System Integration Flow

```mermaid
sequenceDiagram
    autonumber
    actor Officer as Fire Safety Officer (Mobile/Web)
    participant Backend as Spring Boot Core Backend (M1/M2)
    participant Storage as MinIO / S3 Object Storage
    participant Queue as Redis FIFO Queue
    participant Worker as AI Job Worker Process
    participant Gate as Model Capability & Approval Gate
    participant Pipeline as OpenCV & CV Preprocessor
    participant Adapter as PyTorch CV Model Adapter
    participant Intelligence as Safety Intelligence Engine
    participant Reporting as Deterministic Report Generator

    Officer->>Backend: Captures evidence photos & submits inspection
    Backend->>Storage: Stores raw JPEG/PNG at tenants/{tenantId}/evidence/{evidenceId}
    Backend->>Queue: POST /internal/ai/jobs (HTTP 202 Accepted, jobId returned)
    
    loop Asynchronous Processing
        Worker->>Queue: Dequeues pending jobId (BRPOP)
        Worker->>Storage: Downloads evidence bytes with SHA-256 checksum verification
        Worker->>Gate: Evaluates model approval & capability permissions
        alt Production Mode (testMode=False)
            Gate-->>Worker: Status NOT_APPROVED; fail-closed with 0 detections
        else Test Mode (testMode=True)
            Gate-->>Worker: Experimental mode authorized; returns synthetic detections
        end
        Worker->>Pipeline: Resizes canvas (640x640 letterbox) & evaluates quality
        Worker->>Adapter: Executes forward pass under torch.inference_mode()
        Worker->>Gate: Filters out UNSUPPORTED classes (EXIT_DOOR, OBSTACLE)
        Worker->>Intelligence: Maps actionable detections with severity, risk & limitations
        Worker->>Reporting: Compiles deterministic, traceable report draft
        Worker->>Backend: Persists completed analysisRunId & reportId into Redis repository
    end

    Backend->>Worker: GET /internal/ai/jobs/{jobId}/result
    Worker-->>Backend: Returns complete structured result payload
    Backend->>Officer: Displays advisory findings for human verification & sign-off
```

---

## 3. Subsystem Integration Mapping

| Subsystem | Integration Layer | Implementation Status | Test Coverage |
| :--- | :--- | :--- | :--- |
| **Spring Boot Core** | REST API (`/internal/ai/*`) | Contract Specified & Verified | Mock tested via `TestClient` (330 + 12 tests) |
| **Redis Job Queue** | Distributed FIFO (`LPUSH`/`BRPOP`) | Implemented with In-Memory fallback | Tested in unit and integration test suites |
| **MinIO / S3 Storage** | Evidence Retrieval & SHA-256 Guard | Implemented with Mock fallback | Path traversal & checksum tampering tested |
| **PostgreSQL / PostGIS** | Relational Runs, Findings & Polygons | DDL Schema specified for handoff | Serialized JSON and geometry verified |
| **Officer Review UI** | Mobile & Web Review Interface | States specified for Member 3 handoff | `AI_SUGGESTION`, `CONFIRMED`, `DISMISSED` verified |

---

## 4. End-to-End Identity Linkage

To ensure auditable lineage across distributed services, all primary identifiers remain decoupled:

$$\text{tenantId} \rightarrow \text{inspectionId} \rightarrow \text{jobId} \rightarrow \text{analysisRunId} \rightarrow \text{findingId} \rightarrow \text{reportId} \rightarrow \text{evidenceId}$$

* `tenantId`: Identifies the municipality or commercial tenant for complete data isolation.
* `inspectionId`: Physical inspection session created by the Spring Boot core backend.
* `jobId`: Asynchronous queue execution unit created upon job submission.
* `analysisRunId`: Specific computer vision inference run executed by the worker.
* `findingId`: Unique identifier for each individual visual defect observation.
* `reportId`: Deterministic executive draft compiled from the run findings.
* `evidenceId`: UUID of the physical photographic evidence asset stored in MinIO/S3.

---

## 5. Deployment Topology

The AI service can run in three execution topologies controlled by `AI_SERVICE_MODE`:

1. **`api` Mode**: FastAPI server only. Handles HTTP intake, authentication, idempotency check, and enqueues jobs to Redis.
2. **`worker` Mode**: Headless daemon (`python -m app.jobs.worker`). Consumes jobs from Redis queue, runs inference, and writes results to Redis repository.
3. **`combined` Mode**: Local development and staging mode. Spawns an in-process worker thread within the FastAPI process.
