# FireSphere.ai — Phase 3: Orchestration, Evidence Validation, Conflict Resolution, Officer Verification, and Report Generation

**Document Version:** `1.0.0-phase3`  
**Owner:** Member 4 — AI/ML, Computer Vision & AI Productivity  
**Status:** IMPLEMENTED & AUDITED  
**Governance Policy:** STRICTLY ADVISORY — HUMAN OFFICER ON-SITE VERIFICATION MANDATORY — ZERO AUTONOMOUS FIRE NOC DECISIONS  

---

## 1. Executive Summary & Phase 2 Verification Audit

### 1.1 Phase 2 Verification Audit Outcome
During the Phase 2 verification audit:
1. **Workspace and Environment Audit:**
   - Project Root: `c:\Users\athar\OneDrive\Desktop\Firesphere.ai`
   - AI-Service Directory: `ai-service/`
   - Python Virtual Environment: `.venv` (Python 3.13.7) with PyTorch, Torchvision, FastAPI, Pydantic, OpenCV.
   - Git Status: No `.git` repository initialized at project root. All working-tree changes preserved locally. No commits or remote pushes executed.
2. **Bounding Box Validation Audit (`parse_and_validate_bounding_box`):**
   - **Defect Discovered:** The coordinate parsing logic previously inferred scale conventions solely from coordinate magnitude (e.g., checking if values were >= 10 to assume 0–1000 scale). This created ambiguity for coordinates in intermediate scales (e.g., `1.05` to `10.0`) or unscaled pixel values.
   - **Correction Applied:** Updated `app/models/gemini_schemas.py` to require explicit coordinate conventions (`"gemini_0_1000"`, `"normalized"`, `"absolute_pixels"`), strictly rejecting ambiguous coordinate magnitudes rather than guessing or fabricating bounding boxes.
3. **Phase 2 Focused Test Results:**
   - `pytest tests/test_gemini_foundation_phase1.py -v`: 11 passed (100%).
   - `pytest tests/test_gemini_vision_phase2.py -v`: 24 passed (100%).
4. **Live API Smoke Test Status:**
   - Offline mocked testing verified bounded retries, schema parsing, timeout handling, and secret redaction. Live Gemini requests were not required for offline deterministic test passing.

---

## 2. Phase 3 Architecture & Execution Flow

```
                      Inspection Evidence Photos
                                  │
                                  ▼
                     Spring Boot Backend (Owner)
                                  │
                                  ▼
                         AI Job Queue (Redis/In-Memory)
                                  │
                                  ▼
                    Inspection Orchestrator
                                  │
              ┌───────────────────┴───────────────────┐
              ▼                                       ▼
       Gemini VLM                              CV Baseline Model
    (Visual Reasoning)                      (Phase 8.4 Reference)
              │                                       │
              │ (Status/Observations)                 │ (NOT_APPROVED Gate Check)
              └───────────────────┬───────────────────┘
                                  │
                                  ▼
                      Evidence Validation Engine
             (Dimensions, UUID Integrity, Foreign Reference Rejection)
                                  │
                                  ▼
                     Deterministic Reconciler
                    (12-Case Resolution Matrix)
                                  │
                                  ▼
                     Normalized Finding Representation
               (Provenance, Conflict Details, Version=1)
                                  │
                                  ▼
                     Finding Repository & Registry
                                  │
                                  ▼
                 Authorized Officer Review Workflow
               (RBAC: FIRE_OFFICER, LEAD_INSPECTOR, ADMIN)
            (Assessments: CONFIRMED_HAZARD, NOT_A_HAZARD, etc.)
                                  │
                                  ▼
                   Traceable Audit History Log
                                  │
                                  ▼
                 Verified Inspection Report Draft
               (Advisory Draft — Zero Autonomous NOC Decisions)
```

---

## 3. Provider Invocation Policy & Safety Boundaries

### 3.1 Primary Provider: Google Gemini VLM
- Encapsulated in `app.models.gemini_provider.GeminiProvider`.
- Default: Disabled by default (`GEMINI_ENABLED=false`).
- Fail-Closed: Operates safely without network calls or API keys at startup.
- Sanitization: Redacts credentials, tokens, and raw image bytes from all logs and error envelopes.

### 3.2 Reference Secondary Provider: CV Model Baseline
- Encapsulated in `app.models.cv_model_adapter.CVModelAdapter`.
- Production Gate: Status is permanently `NOT_APPROVED` and `NOT_CALIBRATED`.
- Enforcement: When `test_mode=False`, the model approval gate strictly forbids production inference and returns 0 detections (fail-closed).
- Non-Production Evaluation: When `test_mode=True`, test fixture inference is permitted, explicitly stamped with `isTestData=True`.

---

## 4. Evidence Validation Rules

The `EvidenceValidator` (`app/intelligence/evidence_validator.py`) enforces:
1. **Inspection Correlation:** Ensures evidence UUIDs exist in storage and correlate with the active inspection session.
2. **Foreign Reference Rejection:** Model-generated observations are verified against the authorized request batch. Any observation referencing an evidence ID outside the request is immediately rejected.
3. **Image Dimensions:** Requires positive, finite dimensions ($10 \le \text{width}, \text{height} \le 15000$).
4. **Bounding Box Geometry:** Requires finite, ordered normalized coordinates ($0.0 \le xMin < xMax \le 1.0$, $0.0 \le yMin < yMax \le 1.0$) with strictly positive area.
5. **Observation Schema Conformance:** Validates recognized category and severity proposals.

---

## 5. Conflict Resolution & Reconciliation Matrix (12 Cases)

The `ResultReconciler` (`app/intelligence/reconciliation.py`) deterministically resolves multi-provider findings:

| Case | Scenario | Orchestration & Reconciliation Behavior | Reconciled Status | Conflict Type |
|---|---|---|---|---|
| **1** | Both agree on defect & location | Preserves both in provenance; calculates IoU ($\ge 0.3$); agreement noted; human verification required. | `AGREEMENT` | `NONE` |
| **2** | Gemini detects, CV returns 0 | Preserves Gemini finding; records CV status/absence in provenance; finding NOT discarded. | `SINGLE_PROVIDER` | `DETECTION_ABSENCE` or `NONE` |
| **3** | CV detects, Gemini returns 0 | Preserves CV finding with `NOT_APPROVED` status; routes for review; does not bypass gate. | `SINGLE_PROVIDER` | `DETECTION_ABSENCE` |
| **4** | Category Mismatch | Preserves both categories in finding with `conflictDetails`; neither arbitrarily selected. | `CONFLICT` | `CATEGORY_MISMATCH` |
| **5** | Location Mismatch | Preserves both original bounding boxes; coordinates NEVER averaged blindly; officer review required. | `CONFLICT` | `LOCATION_MISMATCH` |
| **6** | Severity Mismatch | Preserves both proposed severities; flags discrepancy in `conflictDetails`. | `AGREEMENT` / `CONFLICT` | `SEVERITY_MISMATCH` |
| **7** | One Inconclusive | Preserves inconclusive reason; retains any valid observation from other provider. | `PARTIAL_INCONCLUSIVE` | `UNRESOLVED_DISAGREEMENT` |
| **8** | One Provider Fails | Preserves successful provider output; records failure code/error; marks run as partial. | `DISCREPANCY` | `UNRESOLVED_DISAGREEMENT` |
| **9** | Both Providers Fail | Records analysis failure; does NOT report successful empty inspection. | `FAILED` | `UNRESOLVED_DISAGREEMENT` |
| **10** | Both Return 0 Observations | Honest zero-observation completion; summary notes building is NOT certified safe. | `NO_OBSERVATIONS` | `NONE` |
| **11** | Provider Disabled | Provider recorded as `DISABLED`; no synthetic results fabricated. | `NO_OBSERVATIONS` | `NONE` |
| **12** | Invalid Schema / Foreign Evidence | Observation rejected and marked invalid; diagnostic metadata preserved safely. | `INVALID` | `NONE` |

---

## 6. Officer Verification Workflow & Security Boundaries

### 6.1 Service Ownership & Trust Boundary
- **Spring Boot Backend Ownership:** Authoritative end-user authentication (JWT), tenant authorization, official Fire NOC statutory decisions, and durable PostgreSQL persistence.
- **FastAPI AI Service Ownership:** Advisory AI findings, observation validation, officer review state tracking, optimistic concurrency locking, and immutable audit history.

### 6.2 Supported Officer Assessments
1. `CONFIRMED_HAZARD`: Officer verifies defect on site and confirms it is an active fire safety hazard.
2. `NOT_A_HAZARD`: Officer dismisses finding (e.g., decorative item or decommissioned fixture).
3. `UNABLE_TO_DETERMINE`: Visual observation cannot be definitively resolved on site.
4. `ADDITIONAL_INSPECTION_REQUIRED`: Mandatory follow-up physical inspection required.

### 6.3 Security Invariants & Ownership Validation
- **Inspection Ownership:** The finding being verified must authoritatively belong to the `inspection_id` identified in the request URL. Mismatches are rejected with `HTTP 400 Bad Request` (`INSPECTION_FINDING_MISMATCH`).
- **Body vs URL Consistency:** If `inspectionId` is supplied in the request body and contradicts the URL, the request is rejected with `HTTP 400 Bad Request`.
- **Fail-Closed Finding Lookup:** If a finding is not found in the repository, the service returns `HTTP 404 Not Found` (`FINDING_NOT_FOUND`). Synthetic stubs are strictly forbidden.
- **Tenant Boundary Isolation:** If tenant context is provided (`X-Tenant-ID`), cross-tenant verification attempts are rejected with `HTTP 403 Forbidden` (`TENANT_ACCESS_FORBIDDEN`).
- **Audit Immutability on Failure:** Any failed request (401, 403, 404, 409, 422) leaves the finding, its version, and its audit history 100% unaltered.
- **Decoupling of Evidence Review:** `evidenceReviewed: bool` defaults strictly to `False`. Checking `evidenceReviewed=True` does NOT automatically confirm a hazard.
- **Autonomous Decision Prohibition:** AI never autonomously issues an NOC decision (`AI_APPROVED` / `AI_REJECTED` are prohibited).
- **Server-Side RBAC:** Validates `officerRole` against `AUTHORIZED_OFFICER_ROLES` (`FIRE_OFFICER`, `LEAD_INSPECTOR`, `CHIEF_FIRE_OFFICER`, `ADMIN`). Unauthorized roles (`CITIZEN`) are rejected with `HTTP 403 Forbidden`.

### 6.4 API Endpoints
- `POST /internal/ai/inspection-analysis/{inspection_id}/verify-finding`
- `POST /internal/ai/findings/{finding_id}/verify`

**Request Body:**
```json
{
  "findingId": "e1f2a3b4-5c6d-7e8f-9a0b-1c2d3e4f5a6b",
  "inspectionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "tenantId": "tenant-municipal-01",
  "evidenceReviewed": true,
  "assessment": "CONFIRMED_HAZARD",
  "officerId": "OFFICER-4412",
  "officerRole": "FIRE_OFFICER",
  "remarks": "On-site verification: extinguisher bracket is decommissioned and cylinder is absent.",
  "categoryOverride": null,
  "severityOverride": null,
  "additionalEvidenceIds": null,
  "expectedVersion": 1
}
```

**Response Body (`200 OK`):**
```json
{
  "findingId": "e1f2a3b4-5c6d-7e8f-9a0b-1c2d3e4f5a6b",
  "inspectionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "tenantId": "tenant-municipal-01",
  "verificationStatus": "OFFICER_CONFIRMED",
  "officerAssessment": "CONFIRMED_HAZARD",
  "evidenceReviewed": true,
  "updatedFinding": { ... },
  "version": 2,
  "auditEntry": {
    "timestamp": "2026-10-10T00:15:30.123456Z",
    "officerId": "OFFICER-4412",
    "officerRole": "FIRE_OFFICER",
    "action": "OFFICER_FINDING_VERIFICATION",
    "previousAssessment": null,
    "newAssessment": "CONFIRMED_HAZARD",
    "previousEvidenceReviewed": false,
    "newEvidenceReviewed": true,
    "remarks": "On-site verification: extinguisher bracket is decommissioned and cylinder is absent.",
    "version": 1,
    "serviceAuthenticated": true
  },
  "verifiedAt": "2026-10-10T00:15:30.123456Z",
  "persistenceDurable": false,
  "persistenceStatus": "IN_MEMORY_EPHEMERAL_PENDING_SPRING_BOOT_PERSISTENCE"
}
```

---

## 7. Report Generation Integration

`DeterministicReportGenerator` (`app/reporting/deterministic_generator.py`) incorporates Phase 3 enhancements:
1. **Model Disagreements:** Any finding with `conflictType != "NONE"` generates an explicit `ReportUnresolvedItem` flagged as `MODEL_DISAGREEMENT_{type}`.
2. **Follow-Up Inspections:** Any finding marked `ADDITIONAL_INSPECTION_REQUIRED` generates an explicit unresolved item requiring follow-up inspection.
3. **Traceability:** Preserves provenance linking each statement to underlying models, evidence IDs, and review logs.
4. **Regulatory Governance Notice:** Reaffirms that the draft is advisory and cannot issue a Fire NOC.

---

## 8. Test Execution Commands & Results

| Test Target | Command | Result |
|---|---|---|
| Phase 1 Foundation | `.venv\Scripts\pytest tests/test_gemini_foundation_phase1.py -v` | 11 passed (100%) |
| Phase 2 Vision Analysis | `.venv\Scripts\pytest tests/test_gemini_vision_phase2.py -v` | 24 passed (100%) |
| Phase 3 Orchestration & Verification | `.venv\Scripts\pytest tests/test_gemini_phase3_orchestration.py -v` | 46 passed (100%) |
| Inspection Analysis Endpoint | `.venv\Scripts\pytest tests/test_inspection_analysis.py -v` | 3 passed (100%) |
| Full AI-Service Suite | `.venv\Scripts\pytest` | **424 passed, 0 failed, 10 warnings** (100%) |

---

## 9. Environment Variables & Configuration

| Variable | Type | Default | Description |
|---|---|---|---|
| `GEMINI_ENABLED` | bool | `false` | Enables Google Gemini provider adapter |
| `GEMINI_API_KEY` | str | `null` | API key for Google GenAI SDK (never logged) |
| `GEMINI_MODEL` | str | `gemini-2.5-flash` | Configured Gemini model identifier |
| `GEMINI_TIMEOUT_SECONDS` | int | `30` | Per-call HTTP timeout limit |
| `GEMINI_MAX_RETRIES` | int | `3` | Max retry attempts for transient API errors |
| `AI_SERVICE_AUTH_TOKEN` | str | `null` | Service-to-service authentication token |

---

## 10. Spring Boot Integration, Persistence Architecture & Trust Boundary

### 10.1 Division of Responsibilities

#### A. Implemented and Tested in FastAPI AI Service
1. Synchronous and asynchronous visual inspection orchestration (`InspectionOrchestrator`).
2. Google Gemini VLM integration with bounded retries and schema validation.
3. Strict model approval gating failing closed for unapproved CV models.
4. Deterministic 12-case cross-provider reconciliation matrix.
5. In-memory finding repository with thread-safe locking and version control (`InMemoryFindingRepository`).
6. Service-to-service authentication enforcement rejecting unauthenticated callers (`UNAUTHORIZED_SERVICE`).
7. Server-side RBAC validation rejecting unauthorized client roles (`CITIZEN`).
8. Trusted officer identity assertion handling via gateway headers (`X-Officer-ID`, `X-Officer-Role`), rejecting header/body conflicts with 400.
9. Provenance tracking marking identity as `TRUSTED_FORWARDED_ASSERTION` or `CALLER_SUPPLIED_BODY_PENDING_SPRING_BOOT_VERIFICATION`.
10. Inspection ownership enforcement matching findings to the URL `inspection_id` (rejecting cross-inspection manipulation with 400).
11. Strict tenant boundary isolation:
    - Missing tenant context rejected with 403 `TENANT_CONTEXT_REQUIRED` when finding is tenant-scoped.
    - Header vs body tenant conflict rejected with 400 `TENANT_CONTEXT_MISMATCH`.
    - Cross-tenant access rejected with 403 `TENANT_ACCESS_FORBIDDEN`.
12. Optimistic concurrency control (HTTP 409 on version conflict).
13. Decoupled `evidenceReviewed` state (defaults `False`, does not imply hazard).
14. Audit history logging attributing verified changes, with complete state immutability on rejected requests.
15. Traceable advisory report generation with disagreement tracking.

#### B. Documented Integration Requirements
1. **Service Authentication:** Spring Boot must include `X-AI-Service-Token: <token>` or `Authorization: Bearer <token>` on all requests.
2. **Correlation ID:** Spring Boot must forward `X-Correlation-ID: <uuid>` for end-to-end request tracing across service logs.
3. **Tenant Context:** Spring Boot must derive the officer's municipal jurisdiction from their authenticated session and inject `X-Tenant-ID: <tenantId>`.
4. **Officer Identity Assertion:** Spring Boot must assert authoritative user identity via `X-Officer-ID: <officerId>` and `X-Officer-Role: <role>` derived directly from the validated JWT.

#### C. Authoritative Spring Boot Responsibilities (Members 1 & 2)
1. **Authoritative Identity Provider (IdP):** Spring Boot validates end-user JWTs (Keycloak/Cognito), verifies officer permissions, confirms the officer is assigned to the specific inspection, and forwards verified identity assertions to FastAPI. FastAPI is an internal service and must NEVER be exposed publicly as a substitute for Spring Boot authentication.
2. **Authoritative PostgreSQL Persistence:** Spring Boot owns all durable database transactions and ACID persistence:
   - `ai_analysis_runs`: Run metadata, configuration snapshot, model versions.
   - `ai_findings`: Deduplicated findings, severity, evidence links, versions.
   - `ai_officer_reviews`: Human officer assessments, remarks, overrides.
   - `ai_audit_trail`: Tamper-evident append-only log of all actions.
   - `ai_report_drafts`: Versioned inspection reports.
3. **Statutory Fire NOC Workflow:** Official NOC grant/rejection decisions are owned solely by Spring Boot and municipal authorities.

#### D. React Native Mobile Application Responsibilities (Member 3)
1. Officer review interface displaying AI observations alongside original photographs.
2. Independent controls for "Evidence Reviewed" checkbox and officer assessment dropdown (`CONFIRMED_HAZARD`, `NOT_A_HAZARD`, `UNABLE_TO_DETERMINE`, `ADDITIONAL_INSPECTION_REQUIRED`).
3. Display of model disagreements and visual bounding boxes.
4. Concurrency conflict handling (prompting officer to reload on HTTP 409).

### 10.2 Authoritative Persistence Contract & Durable Commit Guarantee

1. **Ephemeral vs. Durable Storage:**
   - The AI service repository (`InMemoryFindingRepository`) is strictly an in-memory, process-local cache (`is_durable = False`).
   - All records in the AI service are lost upon process termination, crash, or horizontal replica scale-out.
   - Therefore, a successful HTTP 200 response from the AI service indicates only that the verification was validated and updated in memory.
   - The response explicitly includes:
     - `persistenceDurable: false`
     - `persistenceStatus: "IN_MEMORY_EPHEMERAL_PENDING_SPRING_BOOT_PERSISTENCE"`
   - Callers and clients CANNOT mistake this response for a durable PostgreSQL commit.

2. **Reconciliation of Finding Versions and Audit History:**
   - Each finding contains a strictly monotonically increasing integer `version`.
   - On each valid officer verification, `version` is incremented (`version += 1`) and an immutable audit entry is appended.
   - Spring Boot matches the finding by UUID and performs an optimistic locking check against PostgreSQL (`version = expectedVersion`).
   - If a concurrency conflict occurs in Spring Boot or AI service, HTTP 409 is returned and the client must refresh before retrying.

3. **Failure Handling, Retries, and Idempotency:**
   - Spring Boot maintains idempotency using unique request tokens and correlation IDs (`X-Correlation-ID`).
   - If a network failure occurs between Spring Boot and the AI service, Spring Boot may re-query the finding state from the AI service before re-issuing the update.
   - In case of AI service restart, Spring Boot is the single source of truth and can hydrate finding state back into the AI service if needed.

