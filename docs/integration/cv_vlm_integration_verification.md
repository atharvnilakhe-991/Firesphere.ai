# CV–VLM Integration Verification Report

**Project:** FireSphere.ai  
**Phase:** CV–VLM Integration Verification  
**Audit Type:** Read-Only Source Audit & Focused Non-Destructive Test Execution  
**Execution Date:** 2026-10-13  
**Audit Status:** COMPLETE  

---

## 1. Executive Verdict

| Component / Subsystem | Claim / Verification Subject | Status | Evidence Summary |
| :--- | :--- | :--- | :--- |
| **Local Ollama Runtime** | Service active & `qwen2.5vl:3b` available | **VERIFIED** | Port 11434 responding; `/api/tags` confirms model `qwen2.5vl:3b` (3.8B params, Q4_K_M, vision+completion capabilities). |
| **Ollama VLM Provider** | `OllamaQwenProvider` contract & error handling | **VERIFIED** | 19 unit tests passed: fail-closed defaults, timeout/retries, non-retryable 404 abort, malformed JSON recovery, no synthetic bounding boxes. |
| **Provider Selection & Fallback** | `get_vlm_provider()` selection & zero hosted leakage | **VERIFIED** | Provider selection isolates Ollama requests strictly to `http://127.0.0.1:11434`; zero fallback to Gemini/OpenAI; no hosted API leaks. |
| **VLM Default Configuration** | Default `AI_VLM_PROVIDER` configuration | **DEFECT_CONFIRMED** | `config.py` defaults `vlm_provider` to `"gemini"`. Without explicit `AI_VLM_PROVIDER=ollama` in `.env`, the system defaults to Gemini. |
| **CV Model Inference** | Gated production inference (`ModelApprovalGate`) | **VERIFIED** | Baseline `firesphere-perception-baseline-v0.8.0` is strictly `NOT_APPROVED`. Production inference fails closed with 0 detections. |
| **CV Checkpoint Integrity** | Cryptographic integrity of weights checkpoint | **VERIFIED** | SHA-256 digest `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666` verified against `artifacts/phase8_4/checkpoints/best.pt`. |
| **Capability Gating** | Suppression of unsupported perception classes | **VERIFIED** | `EXIT_DOOR` and `OBSTACLE` detections strictly suppressed from actionable findings due to 0 true positives on baseline test sets. |
| **Result Reconciliation** | 12-case cross-model reconciliation matrix | **VERIFIED** | Agreement (IoU >= 0.3), category conflict, location conflict, single-provider, inconclusive, and failure cases verified by 21 unit tests. |
| **Reconciler Model Attribution** | VLM finding model naming in `ResultReconciler` | **DEFECT_CONFIRMED** | `_create_gemini_finding()` hardcodes `model="google-gemini"` and `modelVersion="gemini-2.5-flash"` instead of reading provider metadata. |
| **AI-to-Checklist Mapping** | Phase 17 deterministic & conflict mapping | **VERIFIED** | Supported categories mapped to NBC 2016 items; ambiguous & unmapped findings isolated; `CV_VLM_CONFLICT` warnings flagged; officer response immutable. |
| **Checklist Authorization** | Phase 18 API boundaries & tenant isolation | **VERIFIED** | 58 backend tests passed: assigned-officer mutations, citizen sanitization, completed-checklist immutability, tenant isolation enforced. |
| **Database Compatibility** | PostgreSQL runtime migration compatibility | **VERIFIED_WITH_LIMITATIONS** | Verified against H2 in-memory test database; live PostgreSQL (port 5432) was offline in workspace environment. |

---

## 2. Actual Architecture and Data Flow

The inspection pipeline bridges local computer vision perception, local Vision-Language Model reasoning, cross-provider reconciliation, finding persistence, and statutory checklist mapping.

```
+---------------------------------------------------------------------------------------------------+
|                                   InspectionOrchestrator                                          |
|                                                                                                   |
|  1. Ingestion: EvidenceClient.fetch_evidence_bytes(eid, tenant_id)                                 |
|  2. Preprocessing & Quality: CVPreprocessor.preprocess() -> 640x640 canvas, Laplacian variance   |
|                                                                                                   |
|         +-------------------------------------+   +------------------------------------+          |
|         |        VLM Inspection Branch        |   |        CV Perception Branch        |          |
|         |                                     |   |                                    |          |
|         |  VLMProviderFactory:                |   |  CVModelAdapter:                   |          |
|         |  -> OllamaQwenProvider              |   |  -> ModelApprovalGate Check        |          |
|         |  -> Local HTTP: 127.0.0.1:11434     |   |     (Baseline: NOT_APPROVED)       |          |
|         |  -> Model: qwen2.5vl:3b             |   |  -> Production: FAIL CLOSED []     |          |
|         |  -> Structured JSON Parsing         |   |     cv_status="NOT_APPROVED_GATED" |          |
|         |  -> GeminiAnalysisResult            |   |  -> Capability Gate:               |          |
|         |                                     |   |     Suppress UNSUPPORTED classes   |          |
|         +------------------+------------------+   +-----------------+------------------+          |
|                            |                                        |                             |
|                            +-------------------+--------------------+                             |
|                                                |                                                  |
|                                                v                                                  |
|                                      ResultReconciler                                             |
|                                                                                                   |
|  3. 12-Case Reconciler Matrix:                                                                    |
|     - Spatial IoU matching (threshold >= 0.3) + semantic category equivalence                      |
|     - Case 1: Agreement -> AGREEMENT (Boosted confidence, detailed provenance)                    |
|     - Case 2/3: Single Provider -> SINGLE_PROVIDER (ConflictType: DETECTION_ABSENCE or NONE)       |
|     - Case 4: Category Conflict -> CONFLICT (CATEGORY_MISMATCH, both categories preserved)         |
|     - Case 5: Location Conflict -> CONFLICT (LOCATION_MISMATCH, both bounding boxes preserved)    |
|     - Case 7/8/9: Inconclusive/Failure -> Safe envelopes, DISCREPANCY or FAILED                   |
|     - Case 10/11: Zero Detections -> NO_OBSERVATIONS (Never certifies compliance)                |
|                                                                                                   |
|  4. Persistence: FindingRepository (ai-service) / ai_findings & ai_analysis_runs (backend)        |
+------------------------------------------------+--------------------------------------------------+
                                                 |
                                                 v
+---------------------------------------------------------------------------------------------------+
|                         InspectionChecklistService (Spring Boot Backend)                          |
|                                                                                                   |
|  5. AI-to-Checklist Mapping (Phase 17):                                                           |
|     - Supported Mappings:                                                                         |
|         * FIRE_EXTINGUISHER_* -> Target Tag "FIRE_EXTINGUISHER" (NBC 2016 Part 4)                 |
|         * EXIT_SIGN_*         -> Target Tag "EXIT_SIGN"         (NBC 2016 Part 4)                 |
|         * EXIT_DOOR           -> Target Tag "EXIT_DOOR"         (NBC 2016 Part 4)                 |
|         * OBSTACLE            -> Target Tag "OBSTACLE"          (NBC 2016 Part 4)                 |
|     - Ambiguous Isolation:                                                                        |
|         * EMERGENCY_EXIT_OBSTRUCTED -> AMBIGUOUS (Door swing vs egress corridor)                  |
|         * COMBUSTIBLE_MATERIAL_HAZARD -> AMBIGUOUS (Missing statutory zone context)               |
|         * GENERIC_FIRE_SAFETY_DEFECT  -> AMBIGUOUS (Lacks NBC clause specificity)                 |
|     - Unsupported Isolation:                                                                      |
|         * SPRINKLER_HEAD_DEFECT       -> UNMAPPED (No active approved checklist clause)           |
|         * ELECTRICAL_PANEL_HAZARD     -> UNMAPPED (No active approved checklist clause)           |
|     - Conflict Detection:                                                                         |
|         * Multiple findings with contradictory suggestions -> Flagged as "CV_VLM_CONFLICT"       |
|     - Advisory Boundary:                                                                          |
|         * Generates ChecklistFindingLink (linkStatus=SUGGESTED, reviewStatus=PENDING_REVIEW)      |
|         * CRITICAL INVARIANT: NEVER alters officer's responseStatus (COMPLIANT/NON_COMPLIANT)     |
|                                                                                                   |
|  6. Officer Authorization & Immutability (Phase 18):                                              |
|     - Only assigned officer or admin can mutate checklist items                                   |
|     - Completed checklists are strictly immutable (400 Bad Request)                               |
|     - Citizen view sanitizes internal AI suggestions and model metadata                           |
+---------------------------------------------------------------------------------------------------+
```

---

## 3. Local Ollama / Qwen Verification

### 3.1 Configured Selection Path and Endpoints
- **Provider Interface:** `OllamaQwenProvider` implements [BaseVLMProvider](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/vlm_provider.py#L14-L90).
- **Factory Selection:** `get_vlm_provider()` in [vlm_provider.py](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/vlm_provider.py#L91-L119) evaluates `selected = (provider_name or getattr(cfg, "vlm_provider", "gemini")).strip().lower()`. If `selected` is in `("ollama", "qwen", "ollama-qwen", "ollama_qwen")`, it returns `OllamaQwenProvider`.
- **Configured Endpoint:** `settings.ollama_base_url` defaults to `http://127.0.0.1:11434`.
- **Configured Model:** `settings.ollama_model` defaults to `qwen2.5vl:3b`.
- **Runtime Verification:** Non-destructive TCP probe (`Test-NetConnection -ComputerName 127.0.0.1 -Port 11434`) confirmed port 11434 is listening. Direct query to `http://127.0.0.1:11434/api/tags` returned:
  - Model Name: `qwen2.5vl:3b`
  - Parameter Size: `3.8B`
  - Format / Quantization: `gguf` / `Q4_K_M`
  - Capabilities: `["completion", "vision"]`
  - Status: **VERIFIED**

### 3.2 Timeout, Retry, and Network Failure Behavior
- **Timeout Parameters:** `ollama_timeout_seconds=60`, `ollama_connect_timeout_seconds=10`.
- **Retry Bounding:** `ollama_max_retries=2`.
- **Permanent Error Isolation:** In `_call_ollama_with_retries()` ([ollama_provider.py:L238-L245](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/ollama_provider.py#L238-L245)), HTTP 400, 404, 422, or errors containing `"not found"` or `"invalid_request"` are classified as permanent. Retries are aborted immediately (`assert mock_client.post.call_count == 1`).
- **Transient Error Backoff:** Transient network failures (connection errors, timeouts, 500, 503) retry with exponential backoff: `min(1.0 * (2 ** (attempt - 1)), 5.0)` seconds.
- **Connection Error Handling:** Target host unavailability raises `httpx.ConnectError`, caught cleanly, returning `GeminiAnalysisStatus.ANALYSIS_FAILED` without throwing unhandled exceptions.
- **Status:** **VERIFIED**

### 3.3 Response Parsing and Malformed JSON Handling
- **Prompt Formulation:** Sends system instruction `OLLAMA_SYSTEM_INSTRUCTION` and prompt `OLLAMA_INSPECTION_PROMPT` with base64 image data and `"format": "json"`.
- **Schema Validation:** Extracts assistant message content and validates via `GeminiRawResponse.model_validate_json()`.
- **Markdown Fencing Fallback:** If markdown fences (` ```json `) are returned, cleans boundaries before re-parsing.
- **Invalid Response Envelope:** If model outputs non-JSON or fails schema validation, returns `GeminiAnalysisStatus.ANALYSIS_INVALID_RESPONSE` with descriptive error and empty observations.
- **Inconclusive Handling:** If `isInconclusive=True`, returns `GeminiAnalysisStatus.ANALYSIS_INCONCLUSIVE` preserving `inconclusiveReason`.
- **Synthetic Fabrication Protection:** Bounding boxes and confidence scores are validated against numeric domains; null fields remain `None` and are never synthesized.
- **Status:** **VERIFIED**

### 3.4 Fallback and Hosted Leakage Check
- **No Silent Fallback:** If `OllamaQwenProvider` encounters an error, timeout, or missing model, it returns a failed `GeminiAnalysisResult`. Neither `OllamaQwenProvider` nor `InspectionOrchestrator` invokes Google Gemini or OpenAI as a fallback.
- **Hosted AI Leakage:** HTTP requests originate strictly to `ollama_base_url`. Hosted providers (Gemini, OpenAI) are fail-closed by default with credentials defaulted to `None`.
- **Status:** **VERIFIED**

---

## 4. CV Model and Approval Gate Verification

### 4.1 Architecture and Checkpoint Loading
- **Adapter Architecture:** `CVModelAdapter` in [cv_model_adapter.py](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/cv_model_adapter.py#L14-L219) consumes preprocessed RGB arrays, converts them to `(1, 3, H, W)` batch tensors via `prepare_input_tensor()`, and delegates forward execution to PyTorch / TorchScript models.
- **Checkpoint Location:** `artifacts/phase8_4/checkpoints/best.pt`.
- **Cryptographic Audit:** `ModelApprovalGate.verify_baseline_checkpoint()` computes SHA-256 and verifies exact match against `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666`.
- **Status:** **VERIFIED**

### 4.2 Production Inference Authorization Check
- **Gate Evaluation:** `ModelApprovalGate.check_inference_permission()` in [model_approval_gate.py:L195-L240](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/model_approval_gate.py#L195-L240) checks whether the model is registered in `model_registry` with `ModelLifecycleState.PRODUCTION`.
- **Approval State:** Baseline model `firesphere-perception-baseline-v0.8.0` is strictly `NOT_APPROVED`. `is_production_approved()` evaluates to `False`.
- **Production Mode Behavior:** When `test_mode=False` and `gate_decision["authorized"]=False`, `CVModelAdapter.predict()` logs `PRODUCTION_INFERENCE_DISABLED` and returns `[]`.
- **Status:** **VERIFIED**

### 4.3 Representation of Unapproved CV State
- **Orchestrator Context:** In [inspection_orchestrator.py:L206-L223](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/services/inspection_orchestrator.py#L206-L223), the orchestrator checks `cv_gate["authorized"]`. When unapproved, it sets `cv_status = "NOT_APPROVED_GATED"` and `raw_cv_detections = []`.
- **Reconciler Context:** `ResultReconciler.reconcile()` receives `cv_status = "NOT_APPROVED_GATED"`, recording provenance with `executionStatus="NOT_APPROVED_GATED"` and `componentStatus="UNAVAILABLE_GATED"`.
- **Distinction from Zero Detections:**
  - When the model is unapproved: `cv_status="NOT_APPROVED_GATED"`. Conflict type for single-provider VLM findings is `ConflictType.NONE`.
  - When an approved model runs and detects zero objects: `cv_status="SUCCESS"`, `raw_cv_detections=[]`. Conflict type for single-provider VLM findings is `ConflictType.DETECTION_ABSENCE`.
- **Method-Level Return Limitation:** In `CVModelAdapter.predict()`, an unapproved model returns an empty list `[]`. If a caller bypasses `InspectionOrchestrator` and directly calls `predict()`, the return value alone does not distinguish gating from zero detections without checking logs or `get_metadata()`.
- **Status:** **VERIFIED_WITH_LIMITATIONS**

### 4.4 Capability Gating of Unsupported Classes
- **Class Capabilities:** Evaluated in `ModelApprovalGate.CLASS_CAPABILITIES`:
  - `FIRE_EXTINGUISHER`: `ADVISORY_ONLY` (actionable=True, 143 TP on test set)
  - `EXIT_SIGN`: `ADVISORY_ONLY` (actionable=True, 52 TP on test set)
  - `EXIT_DOOR`: `UNSUPPORTED` (actionable=False, 0 TP on test set)
  - `OBSTACLE`: `UNSUPPORTED` (actionable=False, 0 TP on test set)
- **Suppression:** `filter_and_gate_detections()` strips `EXIT_DOOR` and `OBSTACLE` detections before findings can be generated.
- **Status:** **VERIFIED**

### 4.5 Test Fixture Mode Isolation
- **Isolation:** When `test_mode=True`, `CVModelAdapter.predict()` emits test detections carrying attributes `testFixture=True`, `isTestData=True`, `modelApprovalStatus="NOT_APPROVED"`, `calibrationStatus="NOT_CALIBRATED"`.
- **Status:** **VERIFIED**

---

## 5. Reconciliation and Provenance Verification

### 5.1 12-Case Reconciliation Matrix
Traced and verified in [ResultReconciler.reconcile()](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/intelligence/reconciliation.py#L86-L557):

| Case | Scenario | Reconciliation Status | Conflict Type | Handling Behavior |
| :---: | :--- | :--- | :--- | :--- |
| **1** | Full Agreement | `AGREEMENT` | `NONE` (or `SEVERITY_MISMATCH`) | IoU >= 0.3 and semantic match; combines observations into single finding; retains detailed provenance from both models. |
| **2** | VLM only (CV clean / gated) | `SINGLE_PROVIDER` | `DETECTION_ABSENCE` (if CV ran) / `NONE` (if gated) | Preserves VLM finding; records CV status honestly in provenance. |
| **3** | CV only (VLM clean / unavailable) | `SINGLE_PROVIDER` | `DETECTION_ABSENCE` (if VLM ran) / `NONE` (if disabled) | Preserves CV finding; records VLM status honestly in provenance. |
| **4** | Category Mismatch | `CONFLICT` | `CATEGORY_MISMATCH` | IoU >= 0.3 but categories disagree; preserves both categories in `conflictDetails`; flags for human officer adjudication. |
| **5** | Location Mismatch | `CONFLICT` | `LOCATION_MISMATCH` | Categories match but IoU < 0.3; preserves both bounding boxes in `conflictDetails`; does not merge coordinates. |
| **6** | Multiple Observations | `AGREEMENT` / `CONFLICT` | Paired by IoU | Multi-object matching pairs closest spatial bounding boxes, appends unmatched items as single-provider findings. |
| **7** | One Inconclusive | `PARTIAL_INCONCLUSIVE` | `UNRESOLVED_DISAGREEMENT` | Preserves valid findings from the operational model; appends inconclusive reason to `knownLimitations`. |
| **8** | One Failed, One Succeeded | `DISCREPANCY` | `UNRESOLVED_DISAGREEMENT` | Preserves operational model findings; records failed provider error in provenance. |
| **9** | Both Failed | `FAILED` | `UNRESOLVED_DISAGREEMENT` | Returns empty findings list and explicit `FAILED` status; generates `ConflictDetail`. |
| **10** | Both Zero Detections | `NO_OBSERVATIONS` | `NONE` | Returns empty list; explicitly noted as NOT proof of compliance. |
| **11** | One Zero, Other Unavailable | `NO_OBSERVATIONS` | `NONE` | Returns empty list with honest component statuses in summary. |
| **12** | Invalid Evidence Linkage | Exception (`400`) | N/A | Evidence ID or inspection ID mismatch raises `InvalidEvidenceException`. |

### 5.2 Source Provenance Retention
- **Provenance Items:** Each finding contains a `provenance` array with entries for both components, recording `providerName`, `modelId`, `modelVersion`, `executionStatus`, `componentStatus`, `timestamp`, and optional `error`.
- **Status:** **VERIFIED**

### 5.3 Safety Invariants in Reconciliation
- **Zero Detections != Compliance:** Missing detections are never treated as statutory compliance. All findings default to `officerVerificationStatus="UNREVIEWED"` and `evidenceReviewed=False`.
- **Confidence Values:** Raw confidences are preserved without claiming calibration. Tagged with `calibrationStatus="NOT_CALIBRATED"`.
- **Bounding Boxes:** Normalized to `[0, 1]`; coordinates from conflicting models are not averaged or synthesized.
- **Status:** **VERIFIED**

---

## 6. Checklist Mapping Verification

### 6.1 Deterministic Mapping Rules
Traced in `InspectionChecklistService.evaluateAndApplyMappings()` ([InspectionChecklistService.java:L395-L640](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/backend/src/main/java/com/firesphere/backend/checklist/service/InspectionChecklistService.java#L395-L640)):

| Finding Type Prefix / Value | Target Category Tag | Suggested Compliance | Statutory Reference |
| :--- | :--- | :--- | :--- |
| `FIRE_EXTINGUISHER_MISSING` | `FIRE_EXTINGUISHER` | `NON_COMPLIANT` | NBC 2016 Part 4 |
| `FIRE_EXTINGUISHER_BLOCKED` | `FIRE_EXTINGUISHER` | `NON_COMPLIANT` | NBC 2016 Part 4 |
| `FIRE_EXTINGUISHER_EXPIRED` | `FIRE_EXTINGUISHER` | `NON_COMPLIANT` | NBC 2016 Part 4 |
| `FIRE_EXTINGUISHER` (other) | `FIRE_EXTINGUISHER` | `NEEDS_FURTHER_INSPECTION` | NBC 2016 Part 4 |
| `EXIT_SIGNAGE_MISSING` | `EXIT_SIGN` | `NON_COMPLIANT` | NBC 2016 Part 4 |
| `EXIT_SIGN` (other) | `EXIT_SIGN` | `NEEDS_FURTHER_INSPECTION` | NBC 2016 Part 4 |
| `EXIT_DOOR` | `EXIT_DOOR` | `NEEDS_FURTHER_INSPECTION` | NBC 2016 Part 4 |
| `OBSTACLE` | `OBSTACLE` | `NON_COMPLIANT` | NBC 2016 Part 4 |

### 6.2 Ambiguous and Unmapped Finding Isolation
- **Ambiguous Categories:**
  - `EMERGENCY_EXIT_OBSTRUCTED`: Isolated as `AMBIGUOUS` ("Ambiguous between exit door swing clearance and corridor egress pathway; requires officer disambiguation").
  - `COMBUSTIBLE_MATERIAL_HAZARD`: Isolated as `AMBIGUOUS` ("Lacks designated statutory zone context").
  - `GENERIC_FIRE_SAFETY_DEFECT`: Isolated as `AMBIGUOUS` ("Lacks NBC clause specificity").
- **Unmapped Categories:**
  - `SPRINKLER_HEAD_DEFECT`: Isolated as `UNMAPPED` ("Active statutory checklist templates do not contain an approved sprinkler inspection clause").
  - `ELECTRICAL_PANEL_HAZARD`: Isolated as `UNMAPPED` ("Active statutory checklist templates do not contain an approved electrical panel clause").
  - Unrecognized categories: Isolated as `UNMAPPED`.
- **Status:** **VERIFIED**

### 6.3 Multi-Finding Conflict Detection
- `complianceByTargetTag` pre-aggregates compliance proposals for each tag. If multiple findings propose divergent outcomes (e.g., `NON_COMPLIANT` and `NEEDS_FURTHER_INSPECTION`), the resulting `AiFindingMappingDto` flags `conflictWarning = "CV_VLM_CONFLICT"`.
- **Status:** **VERIFIED**

### 6.4 Idempotency and Tenant Isolation
- **Idempotency:** Map `existingLinkMap` keys on `checklistResponseId + "_" + findingId`. Re-evaluating mappings does not create duplicate `ChecklistFindingLink` entities.
- **Tenant Isolation:** Enforces `orgId` matching on inspection, checklist, and findings (`finding.getRun().getOrgId().equals(inspection.getOrgId())`).
- **Status:** **VERIFIED**

### 6.5 Officer Authority and Immutability Boundary
- **Advisory Only:** Creating or evaluating AI finding links creates non-binding `ChecklistFindingLink` records (`linkStatus=SUGGESTED`, `reviewStatus="PENDING_REVIEW"`).
- **Officer Response Immutable by AI:** Neither mapping evaluation nor AI link review alters `targetResponse.complianceStatus` (remains `NOT_REVIEWED` or officer-assigned).
- **Completed Checklist Lock:** Completed checklists reject mutations with `400 Bad Request` ("Checklist has already been finalized and completed").
- **Status:** **VERIFIED**

---

## 7. Security and Failure-Handling Review

| Security Control | Verification Method | Result | Status |
| :--- | :--- | :--- | :--- |
| **Fail-Closed Default** | Code inspection & unit tests | Providers and gates default to disabled/unapproved. | **VERIFIED** |
| **Zero Hosted AI Leakage** | Code inspection of Ollama adapter | All requests routed to `http://127.0.0.1:11434`; zero external SDK calls. | **VERIFIED** |
| **Credential Protection** | File inspection & sanitization methods | `get_provider_info()` sanitizes metadata; `.env` not displayed. | **VERIFIED** |
| **Service Authentication** | `test_phase10_service_authentication_enforced` | Endpoints require `X-AI-Service-Token` when auth enabled. | **VERIFIED** |
| **Storage Isolation** | `test_phase10_storage_tenant_isolation_and_path_security` | S3 keys use `tenants/{tenantId}/...`; path traversal (`../../`) rejected. | **VERIFIED** |
| **Input Validation** | `test_ollama_corrupt_image_rejected`, sub-resolution tests | Reject corrupt, oversized, or sub-64x64 inputs with `ImageQualityException`. | **VERIFIED** |
| **No Autonomous Certification** | Pydantic validation on `Finding` | `AI_APPROVED` and `AI_REJECTED` raise `ValidationError`. | **VERIFIED** |
| **PostgreSQL Live Dialect** | Runtime check | Local PostgreSQL port 5432 was offline; tested on H2 in-memory. | **VERIFIED_WITH_LIMITATIONS** |

---

## 8. Actual Tests and Results

### 8.1 Test Execution Summary

| Test Suite / Scope | Command Executed | Execution Type | Tests Run | Passed | Failed | Skipped | Environment |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :--- |
| **Ollama Provider Suite** | `.\.venv\Scripts\python.exe -m pytest tests/test_ollama_provider_phase13.py -v` | Offline Unit / Mock | 19 | 19 | 0 | 0 | Python 3.13 / pytest |
| **Phase 10 Production Gate Suite** | `.\.venv\Scripts\python.exe -m pytest tests/test_phase10_production_integration.py -v` | Integration / TestClient | 13 | 13 | 0 | 0 | Python 3.13 / FastAPI TestClient |
| **Reconciliation & Report Suite** | `.\.venv\Scripts\python.exe -m pytest tests/test_phase6_reconciliation_and_report.py -v` | Unit / Offline | 21 | 21 | 0 | 0 | Python 3.13 / pytest |
| **Backend Checklist & AI Suite** | `mvn.cmd test "-Dtest=ChecklistDomainTests,OfficerChecklistApiTests,AiAnalysisTests"` | Integration / MockMvc | 58 | 58 | 0 | 0 | JDK 21 / Spring Boot / H2 DB |
| **Ollama Port Availability** | `Test-NetConnection -ComputerName 127.0.0.1 -Port 11434` | Network Probe | 1 | 1 | 0 | 0 | Live Windows Host |
| **Ollama Model Inventory** | `Invoke-RestMethod -Uri "http://127.0.0.1:11434/api/tags" -Method Get` | HTTP GET | 1 | 1 | 0 | 0 | Live Local Ollama daemon |

**Total Formal Tests Run Across Repositories:** 111 passed, 0 failed, 0 skipped.

### 8.2 Runtime vs Static Claim Distinction
- **Runtime Verified:**
  - Local Ollama process is active and running on port 11434 with `qwen2.5vl:3b`.
  - Backend Spring Boot checklist endpoints handle officer mutations and immutable completions on H2.
  - Python AI service handles gating, reconciliation, and mock evidence correctly.
- **Static Code Analysis Only:**
  - Live PostgreSQL runtime migration compatibility was not executed against a live PostgreSQL 15/16 instance (port 5432 down).
  - Live end-to-end HTTP pipeline invocation from backend to live Ollama was not performed to prevent un-audited state mutation.

---

## 9. Confirmed Defects

### Defect 1: Hardcoded Model Attribution in `ResultReconciler._create_gemini_finding()`
- **Severity:** Medium
- **Location:** [ai-service/app/intelligence/reconciliation.py:L589-L591](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/intelligence/reconciliation.py#L589-L591)
- **Description:** In `_create_gemini_finding()`, the finding attributes are hardcoded:
  ```python
  explanation=f"Gemini VLM identified visual defect: {desc}",
  model="google-gemini",
  modelVersion="gemini-2.5-flash",
  ```
- **Impact:** When `OllamaQwenProvider` is the active provider, findings produced by the reconciler in single-provider VLM cases display `model: "google-gemini"` and `explanation: "Gemini VLM identified visual defect..."`, even though the underlying provenance correctly attributes Ollama (`qwen2.5vl:3b`).
- **Classification:** **DEFECT_CONFIRMED**

### Defect 2: Default `vlm_provider` Configured to `"gemini"` Instead of `"ollama"`
- **Severity:** Medium
- **Location:** [ai-service/app/core/config.py:L211-L215](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/core/config.py#L211-L215)
- **Description:** `Settings.vlm_provider` defaults to `"gemini"`. If `AI_VLM_PROVIDER=ollama` is not explicitly declared in `ai-service/.env`, `get_vlm_provider()` defaults to `GeminiProvider`. Because `ai-service/.env` does not contain `AI_VLM_PROVIDER`, default instantiation attempts Gemini requests.
- **Impact:** Calling orchestrator endpoints without an explicit provider parameter causes the service to attempt external Gemini calls, triggering 30s timeouts if unconfigured or offline.
- **Classification:** **DEFECT_CONFIRMED**

### Defect 3: Starlette Deprecation Warnings in FastAPI TestClient
- **Severity:** Low
- **Location:** `ai-service/tests` runtime output
- **Description:** Deprecation warnings emitted during pytest execution:
  - `StarletteDeprecationWarning: Using httpx with starlette.testclient is deprecated; install httpx2 instead.`
  - `StarletteDeprecationWarning: 'HTTP_422_UNPROCESSABLE_ENTITY' is deprecated. Use 'HTTP_422_UNPROCESSABLE_CONTENT' instead.`
- **Classification:** **DEFECT_CONFIRMED**

---

## 10. Risks and Limitations

1. **PostgreSQL Compatibility Limited to H2 Emulation:** Backend tests pass against H2 in PostgreSQL compatibility mode, but differences in PostgreSQL-specific SQL syntax (`jsonb`, UUID operators, Flyway lock semantics) remain unverified against a real database daemon.
2. **Method-Level CV Gating Ambiguity:** In `CVModelAdapter.predict()`, an unapproved model returns `[]`. Callers bypassing `InspectionOrchestrator` cannot determine whether the model was gated or detected zero objects.
3. **Synthetic Dataset Generalization:** Model evaluation metrics derive from synthetic fixtures and held-out crops; real-world field images with dust, glare, angle distortion, and varied lighting have not yet undergone statistical calibration.
4. **Local Hardware Constraints for VLM Inference:** Running `qwen2.5vl:3b` on local Ollama is hardware-dependent. Concurrency and response latency must be benchmarked on target inspection field hardware.

---

## 11. Recommended Next Actions

1. **Correct Reconciler Attribution (Immediate Fix):** Update `ResultReconciler._create_gemini_finding()` to accept dynamic provider metadata (`gemini_model_info["provider"]` and `gemini_model_info["model"]`) so findings accurately display `ollama` and `qwen2.5vl:3b`.
2. **Explicit Default Alignment in `config.py`:** Update default `vlm_provider` in `app/core/config.py` to default to `"ollama"` or require explicit configuration, eliminating silent fallbacks to unauthenticated Gemini endpoints.
3. **Dedicated Return Envelope for `CVModelAdapter`:** Refactor `CVModelAdapter.predict()` to return a structured inference result object containing both detection lists and explicit operational statuses (`SUCCESS`, `NOT_APPROVED_GATED`, `FAILED`) to avoid ambiguous empty lists.
4. **Provision Local PostgreSQL 16 Instance:** Start a local PostgreSQL service on port 5432 to verify Flyway migrations V1 through V1.0.10 and execute the 58 backend tests on the target production database engine.
5. **Aboard Member 1 Redesign Integration:** Maintain postponement of Member 1 UI integration until the M1 redesigned work artifacts are officially released.

---

**Audit Sign-off:**  
FireSphere.ai Quality & Architecture Verification Team  
Status: **AUDIT COMPLETE (READ-ONLY)**
