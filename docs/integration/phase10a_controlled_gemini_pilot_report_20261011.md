# Phase 10A: Controlled Single-Image Gemini VLM Pilot Report

**Document Date:** October 11, 2026  
**Execution Context:** Pre-Flight Audit, Infrastructure Verification, Non-Live Governance Validation, and Authorization Gate  
**Reference Specification:** FireSphere.ai Integration Architecture Phase 10A  
**Status:** **STAGE A–C COMPLETED | STAGE D AUTHORIZATION GATE ACTIVE (AWAITING OPERATOR APPROVAL)**  

---

## Executive Summary

Phase 10A initiates the controlled, single-image Gemini Vision-Language Model (VLM) pilot for FireSphere.ai. In strict adherence to the Governance Rules:
1. **Pre-Flight Audit (Stage A)** inspected the canonical M4 (`ai-service`) and M2 implementations, verifying configuration parameters, request/response contracts, evidence provenance, and error handling.
2. **Infrastructure Readiness (Stage B)** established that the local Docker Desktop daemon is currently stopped. Per the mandate, no destructive Docker, container, volume, or migration commands were executed.
3. **Non-Live Testing (Stage C)** validated M4 and M2 using test doubles and mocks. A total of **489 M4 unit/contract tests passed (0 failures)** and **20 M2 AI tests passed (0 failures)**, specifically verifying fail-closed error handling, schema integrity, tenant isolation, and regulatory invariants.
4. **Authorization Gate (Stage D)** was reached. **Zero external API calls were made.** `GEMINI_ENABLED=false` remains strictly enforced. No API keys were loaded or exposed.
5. **No Model Approval / Autonomous NOC Gates Bypassed**: The CV model remains `NOT_APPROVED` and `NOT_CALIBRATED`. AI outputs remain strictly advisory and require mandatory human Fire Officer verification.

---

## 1. Pre-Flight Findings (Stage A)

### 1.1 Configuration Loading & Environment Variables
Inspection of `ai-service/app/core/config.py` identified the exact parameters governing the Gemini VLM integration:

| Environment Variable | Field Name | Type | Default | Constraints / Invariants |
|---|---|---|---|---|
| `GEMINI_ENABLED` | `gemini_enabled` | `bool` | `False` | Fail-closed default. Must be explicitly set to `True` for live inference. |
| `GEMINI_API_KEY` | `gemini_api_key` | `Optional[str]` | `None` | Required and enforced by `@model_validator` when `gemini_enabled=True`. |
| `GEMINI_MODEL` | `gemini_model` | `str` | `"gemini-2.5-flash"` | Target model ID supporting multimodal vision reasoning and structured JSON output. |
| `GEMINI_TIMEOUT_SECONDS` | `gemini_timeout_seconds` | `int` | `30` | Request timeout interval (bounded: $1 \le t \le 300$). |
| `GEMINI_MAX_RETRIES` | `gemini_max_retries` | `int` | `3` | Maximum bounded retry attempts for transient network/server errors (bounded: $0 \le r \le 10$). |
| `AI_SERVICE_AUTH_TOKEN` | `service_auth_token` | `Optional[str]` | `None` | Shared secret token for M2-M4 service-to-service authentication. |
| `AI_AUTH_ENABLED` | `auth_enabled` | `bool` | `False` | Strict auth enforcement flag (enforced automatically if `service_env == "production"`). |
| `AI_MAX_IMAGE_SIZE_BYTES` | `max_image_size_bytes` | `int` | `20971520` (20MB) | Maximum payload byte size for uploaded inspection evidence. |
| `AI_MIN_IMAGE_WIDTH` | `min_image_width` | `int` | `64` | Minimum acceptable image width in pixels. |
| `AI_MIN_IMAGE_HEIGHT` | `min_image_height` | `int` | `64` | Minimum acceptable image height in pixels. |

### 1.2 Provider Implementation & SDK Integration
Inspection of `ai-service/app/models/gemini_provider.py` verified:
- **Official SDK Client:** Uses `google.genai.Client(api_key=..., http_options=...)` from the official `google-genai` SDK package (verified installed and available in the virtual environment).
- **Client Lifecycle:** Client instantiation is deferred until explicit invocation (`initialize()`). Initializing with missing credentials when enabled raises `GeminiConfigurationException`.
- **Structured Schema Enforcement:** Generation requests supply `types.GenerateContentConfig(response_mime_type="application/json", response_schema=GeminiRawResponse, temperature=0.1, system_instruction=...)`.
- **Bounded Retries & Timeout:** Transient HTTP/network errors trigger exponential backoff up to `max_retries` (attempts 1 to 3). Non-retryable errors (HTTP 400, 401, 403, invalid arguments, permission denied) abort immediately without wasteful retries.
- **Fail-Closed Governance:** If disabled or not configured, returns `GeminiAnalysisStatus.GEMINI_DISABLED` or `GEMINI_NOT_CONFIGURED` without initiating any network traffic.

### 1.3 Endpoint Contracts & Schemas
Two canonical M4 endpoints interact with the VLM logic:

1. **Independent VLM Endpoint (`POST /internal/ai/vlm-analysis`)** defined in `app/api/v1/vlm.py`:
   - **Request Schema (`VLMAnalysisRequest`):**
     ```json
     {
       "evidenceId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
       "tenantId": "default",
       "inspectionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
       "expectedSha256": "optional-64-char-hex-hash",
       "imageBytesBase64": "optional-base64-encoded-image-data"
     }
     ```
   - **Response Schema (`GeminiAnalysisResult`):**
     - Lifecycle status: `GeminiAnalysisStatus` (`GEMINI_DISABLED`, `GEMINI_NOT_CONFIGURED`, `ANALYSIS_COMPLETED_WITH_OBSERVATIONS`, `ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS`, `ANALYSIS_INCONCLUSIVE`, `ANALYSIS_FAILED`, `ANALYSIS_INVALID_RESPONSE`).
     - Observation list (`observations`): 14 unified contract fields including `observationId`, `evidenceId`, `objectCategory`, `potentialHazardCategory`, `observationDescription`, `observationSummary`, `visualEvidence`, `boundingBox`, `confidence`, `severityProposal`, `uncertaintyDescription`, `verificationGuidance`, `advisoryStatus` (`"ADVISORY_ONLY"`), and `officerReviewRequirement` (`"MANDATORY_OFFICER_REVIEW"`).
     - Governance flags: `requiresOfficerVerification: true`, `isSafeInference: false`.

2. **Synchronous Multi-Provider Endpoint (`POST /internal/ai/inspection-analysis`)** defined in `app/api/v1/inspection.py`:
   - Routes through `InspectionAnalysisService.process_inspection()` to `InspectionOrchestrator.orchestrate_inspection()`.
   - Executes Gemini VLM (primary) and CV adapter (secondary gated), followed by deterministic reconciliation and report generation.

### 1.4 Image Formats & Safe Test Image Provisioning
Inspection of `app/cv/file_validator.py` and `app/cv/quality.py` verified:
- **Supported MIME Types:** `image/jpeg`, `image/jpg`, `image/png`, `image/webp`. Validated strictly via binary magic signatures (`\xff\xd8\xff`, `\x89PNG\r\n\x1a\n`, `RIFF...WEBP`). File extensions are never blindly trusted.
- **Size Limits:** Maximum 20MB. Empty (0-byte) payloads are rejected with HTTP 422.
- **Safe Test Fixture Provisioning:** The existing test suite (`tests/test_vlm_service_phase5.py` and `tests/test_gemini_vision_phase2.py`) provides safe, synthetic in-memory JPEG generation via OpenCV (`sample_test_image_bytes`). It creates a 640x640 synthetic image containing geometric shapes (red cylinder representing a fire extinguisher, green box representing an exit sign) and watermarked test text. **This image contains zero citizen data, zero PII, and zero confidential premises details.**

---

## 2. Source Files Inspected and Modified

### 2.1 Files Inspected
1. `ai-service/app/core/config.py` — Settings schema, aliases, validators for Gemini and service auth.
2. `ai-service/app/models/gemini_provider.py` — SDK client initialization, retries, image decoding, prompt construction, observation parsing.
3. `ai-service/app/models/gemini_schemas.py` — Raw and unified observation models, status enums, bounding box conversion.
4. `ai-service/app/models/gemini_prompts.py` — Canonical system prompt and vision inspection prompt.
5. `ai-service/app/services/vlm_service.py` — Independent VLM service logic, SHA-256 integrity verification, tenant boundary validation.
6. `ai-service/app/api/v1/vlm.py` — FastApi router for `/internal/ai/vlm-analysis` and `/internal/ai/vlm/status`.
7. `ai-service/app/schemas/vlm.py` — Request and status Pydantic models.
8. `ai-service/app/cv/file_validator.py` — Magic-byte MIME detection and payload size bounds.
9. `ai-service/app/cv/quality.py` — Blur, resolution, and brightness checks.
10. `ai-service/app/core/auth.py` — Constant-time token verification.
11. `ai-service/app/services/inspection_service.py` — Orchestrator integration bridge.
12. `ai-service/app/services/inspection_orchestrator.py` — Multi-provider dispatch, fault isolation, reconciliation.
13. `ai-service/app/intelligence/reconciliation.py` — 12 deterministic reconciliation cases.
14. `ai-service/app/intelligence/deterministic_generator.py` — Structured report generation engine.
15. `ai-service/app/services/inspection_report_service.py` — Final report coordination.
16. `ai-service/tests/test_vlm_service_phase5.py` — 17 unit/integration tests for VLM service.
17. `ai-service/tests/test_gemini_vision_phase2.py` — 24 unit tests for Gemini provider and schema parsing.
18. `ai-service/tests/test_gemini_foundation_phase1.py` — 11 foundation tests for config and fail-closed defaults.
19. `ai-service/tests/test_m2_m4_contract_specification.py` — 7 contract tests verifying M2/M4 cross-compatibility.
20. `ai-service/tests/test_error_handling.py` — 5 error-handling tests.
21. `ai-service/tests/test_gemini_phase3_orchestration.py` — 46 orchestration and governance tests.
22. `ai-service/tests/test_compliance_boundary.py` — 4 tests enforcing prohibition of autonomous Fire NOC decisions.
23. `M2_work/Firesphere m2/backend/src/test/java/com/firesphere/backend/HttpAiAnalysisClientTests.java` — 5 Spring/JUnit tests for HTTP AI client.
24. `M2_work/Firesphere m2/backend/src/test/java/com/firesphere/backend/AiAnalysisTests.java` — 15 Spring/JUnit integration tests for AI workflow and persistence.

### 2.2 Files Modified
**NONE.** No source code, configuration files, or environment configurations were modified during this stage.

---

## 3. Infrastructure Health Results (Stage B)

- **Docker Daemon Check:** Executed `docker ps`.
- **Result:**
  ```text
  failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine;
  check if the path is correct and if the daemon is running:
  open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.
  ```
- **Finding:** Docker Desktop is currently stopped.
- **Action Taken:** In accordance with Governance Rule 5 and Stage B instructions ("If Docker Desktop is stopped, report that fact and stop. Do not change installation settings, remove volumes, or rebuild the environment destructively"), no destructive commands or daemon resets were executed.
- **Component Status:**
  - PostgreSQL / PostGIS: Container stopped (Docker inactive).
  - MinIO: Container stopped (Docker inactive).
  - M2 Backend: Not running live in container; verified via in-memory H2 test slice.
  - M4 FastAPI Service: Not running live in container; verified via FastAPI `TestClient` in Python 3.13 venv.

---

## 4. Non-Live Test Results (Stage C)

All non-live tests were executed in the local virtual environment and Maven test runners using test doubles and mocks.

### 4.1 Test Execution Summary

| Test Suite | Scope / Module | Tests Run | Passed | Failed | Execution Mode |
|---|---|---|---|---|---|
| `test_vlm_service_phase5.py` | VLM Service, Status, Schemas, Hash Integrity, Auth, Tenant | 17 | 17 | 0 | Mock / Test Double |
| `test_gemini_vision_phase2.py` | Provider Parsing, Bounding Boxes, Retries, Timeouts, Sanity | 24 | 24 | 0 | Mock / Test Double |
| `test_gemini_foundation_phase1.py` | Config Defaults, Fail-Closed Security, Gate Invariants | 11 | 11 | 0 | Mock / Offline |
| `test_m2_m4_contract_specification.py` | DTO Serialization, HTTP Auth, Payload Contracts | 7 | 7 | 0 | TestClient / Mock |
| `test_error_handling.py` | Request Validation, 422 Responses, Failure Payloads | 5 | 5 | 0 | TestClient |
| `test_gemini_phase3_orchestration.py` | Orchestration, 12 Reconciliation Cases, Officer Review, RBAC | 46 | 46 | 0 | TestClient / Mock |
| `test_compliance_boundary.py` | Prohibition of Autonomous Decisions (`AI_APPROVED` forbidden) | 4 | 4 | 0 | Unit Test |
| **Complete M4 Pytest Suite** | Full AI Service Regression (all modules) | **489** | **489** | **0** | **Mock / Unit / Contract** |
| `HttpAiAnalysisClientTests` (M2) | Spring HTTP Client Error & DTO Handling | 5 | 5 | 0 | Mockito / WireMock |
| `AiAnalysisTests` (M2) | Application AI Workflow, Audit Logging, Dual Transactions | 15 | 15 | 0 | H2 In-Memory / Mock |
| **Total Non-Live Tests** | **Combined Workspace Verification** | **509** | **509** | **0** | **100% Passing** |

### 4.2 Specific Verification Checkpoints
1. **Malformed Responses & Invalid Schemas:** Tested in `test_malformed_json_returns_invalid_response`, `test_empty_response_text_returns_invalid_response`, and `test_bounding_box_rejection_of_invalid_geometries`. Returns `ANALYSIS_INVALID_RESPONSE` with sanitized diagnostics.
2. **Provider Timeout & Unavailable Behavior:** Tested in `test_exhausted_retries_returns_analysis_failed` and `test_orchestration_partial_provider_failure_handled`. Handled gracefully without service crashes.
3. **Missing/Invalid Credentials:** Tested in `test_gemini_enabled_missing_credentials_fails_gracefully` and `test_non_retryable_auth_error_aborts_immediately`. Fails cleanly without exposing secrets.
4. **Evidence UUID & Tenant Isolation:** Tested in `test_http_vlm_analysis_tenant_mismatch_rejected_400`, `test_tenant_isolation_missing_tenant_context_rejected_403`, and `test_evidence_validation_rejects_foreign_model_evidence_id`. Enforced with 400/403 status codes.
5. **Provider Failure $\ne$ Premises Safe:** Tested in `test_reconciliation_case_9_both_providers_failed` and `test_reconciliation_case_8_one_fails_one_succeeds`. When providers fail, `isSafeInference=false` and findings are marked `INCONCLUSIVE_PROVIDER_FAILURE`. It is impossible for a provider error to be interpreted as a safe certification.
6. **Advisory Outputs & Mandatory Officer Verification:** Enforced across all observation models (`advisoryStatus="ADVISORY_ONLY"`, `requiresOfficerVerification=true`, `officerReviewRequirement="MANDATORY_OFFICER_REVIEW"`).
7. **CV Approval & Calibration Gates:** Enforced in `test_cv_model_approval_gate_unaltered_by_vlm_integration` and `test_cv_model_approval_gate_remains_intact`. Gate state remains `NOT_APPROVED` and `NOT_CALIBRATED`.

---

## 5. Authorization Status & External Traffic Verification

- **Explicit Authorization Obtained:** **NO** (Not yet requested/granted).
- **Real External API Requests Occurred:** **NO.** Zero network calls were made to Google Gemini or external endpoints.
- **Provider State:** `GEMINI_ENABLED=false` was strictly preserved throughout execution.
- **Live Provider Execution Evidence:** None (prohibited prior to operator authorization).

---

## 6. Security, Privacy, and Governance Observations

1. **Secret & Key Protection:** No API keys or tokens exist in tracked source code, environment files, git history, or test outputs. The provider uses constant-time string comparisons for service authentication and sanitizes all exception messages to strip URLs and credentials.
2. **Citizen Privacy & Evidence Provenance:** All non-live testing utilized synthetic in-memory JPEG arrays generated via OpenCV. No real citizen inspection images, personal data, or premises photographs were loaded or processed.
3. **Statutory Decision Integrity:** The system strictly prohibits autonomous Fire NOC decisions. The enums `AI_APPROVED` and `AI_REJECTED` are explicitly rejected by schema validation. All AI outputs are advisory and must be individually confirmed or dismissed by an authorized Fire Safety Officer.
4. **Tenant Isolation:** Multi-tenancy is enforced at both M2 and M4 layers. Mismatched tenant headers (`X-Tenant-ID`) against request bodies or evidence records result in HTTP 400 or HTTP 403 rejections.

---

## 7. Stage D — Formal Authorization Request

In compliance with Stage D of the Governance Protocol, execution has stopped prior to making any external network requests. Below is the formal, structured authorization request for operator review:

```markdown
================================================================================
                    PHASE 10A PILOT AUTHORIZATION REQUEST
================================================================================

1. EXACT ENDPOINT TO BE CALLED:
   POST /internal/ai/vlm-analysis
   (Canonical independent VLM analysis endpoint in M4 ai-service)

2. EXACT PROVIDER & MODEL IDENTIFIER:
   Provider: Google GenAI SDK (google-genai)
   Model: gemini-2.5-flash

3. DATA TO BE SENT EXTERNALLY:
   - Raw binary bytes of one (1) synthetic JPEG test image.
   - Standard FireSphere VLM inspection prompt (requesting structured detection
     of fire equipment, exit signage, and observable physical hazards).
   - Expected response schema definition (GeminiRawResponse).
   - Note: No tenant IDs, user IDs, premises addresses, or application metadata
     are transmitted in the prompt or payload.

4. PROPOSED TEST IMAGE AND SOURCE:
   - Source: Synthetic in-memory JPEG generated via OpenCV (from tests/test_vlm_service_phase5.py).
   - Dimensions: 640 x 640 pixels, 3 channels (RGB/BGR).
   - Content: Solid neutral background with geometric synthetic shapes representing
     a fire extinguisher and an exit sign, with synthetic test watermark.

5. SENSITIVE DATA STATUS:
   - Personal Data / PII: NONE (0%).
   - Citizen Evidence: NONE (0%).
   - Confidential Inspection Data: NONE (0%).
   - Contains only synthetically generated geometric primitives.

6. EXPECTED COST / QUOTA IMPACT:
   - Exactly one (1) multimodal inference request to gemini-2.5-flash.
   - Input tokens: ~300 image tokens + ~400 prompt tokens = ~700 tokens.
   - Output tokens: ~150 - 300 tokens (structured JSON).
   - Financial cost: Negligible (< $0.001 USD under standard Gemini 2.5 Flash pricing).

7. EXACT RUNTIME CONFIGURATION CHANGES NEEDED:
   Temporarily export in the active test session:
   - GEMINI_ENABLED=true
   - GEMINI_API_KEY=<operator-supplied-api-key>
   - GEMINI_MODEL=gemini-2.5-flash

8. CLEANUP & RESET OF RUNTIME FLAGS AFTER TEST:
   Immediately following the single inference request:
   - Reset GEMINI_ENABLED=false
   - Clear GEMINI_API_KEY from environment memory
   - Verify provider get_status() returns 'DISABLED'
   - Verify CV model approval remains NOT_APPROVED and NOT_CALIBRATED
   - Report any temporary memory or artifact structures created.
================================================================================
```

---

## 8. Remaining Blockers & Next Action

1. **Docker Infrastructure:** Docker Desktop is currently stopped. This prevents live end-to-end containerized execution across PostgreSQL, MinIO, and M2. However, it does **not** block the M4 single-image VLM pilot, which executes within the M4 Python environment.
2. **Operator Authorization:** Live inference requires explicit operator confirmation and a valid Gemini API key.
3. **Recommended Next Action:**
   - Review this report and the formal Stage D Authorization Request.
   - Provide explicit operator authorization and API key to execute Stage E (Single-Image Live Test).
   - If authorization is granted, execute Stage E with the approved synthetic image, verify the real provider response, capture sanitized evidence, execute Stage F (restore safe config), and finalize the report.
   - If authorization is withheld, maintain safe non-live operation.
