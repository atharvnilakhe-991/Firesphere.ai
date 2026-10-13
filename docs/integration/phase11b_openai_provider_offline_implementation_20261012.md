# FireSphere.ai — Phase 11B: Provider Abstraction and Offline OpenAI VLM Adapter Implementation

**Document Date:** October 12, 2026  
**Execution Phase:** Phase 11B — Provider Abstraction and Offline OpenAI VLM Adapter  
**Scope:** Minimal Provider Abstraction (`BaseVLMProvider`), Offline-Testable OpenAI VLM Adapter (`OpenAIProvider`), Provider Selection Configuration, Multi-Provider Provenance, Comprehensive Mocked Regression Suite  
**Final Verdict:** **PASS — OFFLINE IMPLEMENTATION VERIFIED**  
**Live Network Inference / Requests Dispatched:** **NONE (100% Strictly Offline Mocked Execution)**  
**Safety & Governance Invariants:** CV Gate: `NOT_APPROVED` | CV Calibration: `NOT_CALIBRATED` | Human Verification: `requiresOfficerVerification=True` | Statutory Decision: `ADVISORY_ONLY` | Production Inference: `UNAUTHORIZED`  

---

## 1. Executive Summary

Phase 11B implements a decoupled, maintainable Vision-Language Model (VLM) provider abstraction and a fully offline-testable OpenAI vision adapter for FireSphere.ai's Module 4 (AI Visual Inspection).

This phase was executed under strict governance and safety constraints:
1. **Zero Live Model Calls:** No live network requests were dispatched to OpenAI, Google Gemini, or any remote model provider.
2. **Preservation of Existing Work:** The Google Gemini provider implementation (`GeminiProvider`), existing prompt taxonomy, bounding box normalization contracts, and CV model approval gate (`ModelApprovalGate`) remain completely preserved and backward-compatible.
3. **No Database or MinIO Writes:** All evidence loading and repository persistence in test suites used in-memory test registries and mocks.
4. **No Exposure of Secrets:** Missing API keys fail closed safely without leaking authorization headers or credential tokens in logs or error payloads.
5. **Human-in-the-Loop Governance:** Autonomous statutory approvals (`AI_APPROVED`) and rejections (`AI_REJECTED`) remain strictly forbidden; all AI observations require mandatory verification by an authorized inspecting Fire Officer.

---

## 2. Architecture: Before and After

### 2.1 Architecture Before Phase 11B
Prior to Phase 11B:
- `VLMService` directly coupled to `GeminiProvider`:
  - `VLMService.__init__` accepted only `provider: Optional[GeminiProvider] = None` and defaulted directly to `gemini_provider`.
  - Service status evaluation hardcoded string blockers: `"GEMINI_ENABLED is false..."` and `"GEMINI_API_KEY is not configured"`.
- `InspectionOrchestrator` directly coupled to `GeminiProvider`:
  - `InspectionOrchestrator.__init__` accepted `gemini: Optional[GeminiProvider] = None`.
  - Multi-provider provenance metadata in `reconciliation.py` hardcoded `"providerName": "google-gemini"`.
- Configuration in `app/core/config.py` contained only `gemini_*` settings, with no VLM provider selection mechanism.
- Testing: No abstraction existed to substitute alternative VLMs without hacking or mocking `GeminiProvider` directly.

```
[Before Phase 11B]
+-------------------------------------------------------------+
|                  InspectionOrchestrator                     |
+------------------------------+------------------------------+
                               |
                               v
                       [GeminiProvider] <--- Direct Coupling
                               |
                               v
                     [Google GenAI Client]
```

### 2.2 Architecture After Phase 11B
Phase 11B introduces a minimal, common abstraction layer (`BaseVLMProvider`) that decouples model inference from orchestration:
- `BaseVLMProvider` defines the standard contract:
  - Identity & lifecycle: `provider_name`, `is_enabled()`, `is_configured()`, `is_ready()`, `get_status()`, `get_model_id()`, `get_provider_info()`.
  - Evidence analysis: `analyze_evidence(...)` and `analyze_image(...)` returning normalized `GeminiAnalysisResult` envelopes.
- `GeminiProvider` implements `BaseVLMProvider` (`provider_name = "google-gemini"`), preserving all existing logic.
- `OpenAIProvider` implements `BaseVLMProvider` (`provider_name = "openai"`), translating image evidence to base64 data URIs, requesting strict structured output (`response_format={"type": "json_schema", ...}`), handling bounded retries, and mapping responses into the validated FireSphere schema.
- `VLMService` and `InspectionOrchestrator` depend on `BaseVLMProvider` and select the active provider dynamically via `app/core/config.py` (`vlm_provider="gemini"` or `"openai"`).
- `ResultReconciler` binds dynamic provider provenance (`"providerName": gemini_model_info.get("provider", "google-gemini")`), supporting multi-provider reconciliation.

```
[After Phase 11B]
+-------------------------------------------------------------+
|                  InspectionOrchestrator                     |
+------------------------------+------------------------------+
                               |
                               v
                         [VLMService]
                               |
                               v
                      <<BaseVLMProvider>>
                      /                 \
                     /                   \
           [GeminiProvider]         [OpenAIProvider]
             (google-gemini)            (openai)
                   |                        |
                   v                        v
            [Google GenAI]          [OpenAI Chat/Vision]
```

---

## 3. Files Added and Modified

### 3.1 Files Added
1. `app/models/vlm_provider.py`
   - Defines abstract base class `BaseVLMProvider` enforcing required lifecycle and inference methods.
   - Provides `get_vlm_provider(provider_name, settings_obj)` factory function.
2. `app/models/openai_prompts.py`
   - Defines `OPENAI_SYSTEM_INSTRUCTION` and `OPENAI_INSPECTION_PROMPT` aligning OpenAI vision reasoning with FireSphere's perception taxonomy and governance rules.
3. `app/models/openai_provider.py`
   - Full OpenAI vision API adapter implementing `BaseVLMProvider`.
   - Encapsulates base64 encoding, strict structured output schema definition, client dependency injection, bounded retries (with exponential backoff for transient errors, immediate abort on 401/403/400), sanitized error logging, and schema validation.
4. `tests/test_openai_provider_phase11b.py`
   - Comprehensive offline test suite (20 tests) covering all 18 specified testing requirements (A through R).
5. `docs/integration/phase11b_openai_provider_offline_implementation_20261012.md`
   - This documentation artifact.

### 3.2 Files Modified
1. `app/models/gemini_schemas.py`
   - Added `PROVIDER_DISABLED` and `PROVIDER_NOT_CONFIGURED` to `GeminiAnalysisStatus` with non-recursive, string-based `__eq__` for backward compatibility.
   - Added type aliases `VLMAnalysisResult`, `VLMObservation`, `VLMAnalysisStatus`.
2. `app/core/exceptions.py`
   - Added `OpenAIConfigurationException` (`status_code=500`, `error_code="OPENAI_CONFIGURATION_ERROR"`).
   - Added `OpenAIProviderException` (`status_code=502`, `error_code="OPENAI_PROVIDER_ERROR"`).
3. `app/core/config.py`
   - Added `vlm_provider` (`AI_VLM_PROVIDER`, default `"gemini"`).
   - Added `openai_enabled` (`OPENAI_ENABLED`, default `False`).
   - Added `openai_api_key` (`OPENAI_API_KEY`, default `None`).
   - Added `openai_model` (`OPENAI_MODEL`, default `"gpt-4o-mini"`).
   - Added `openai_timeout_seconds` (`OPENAI_TIMEOUT_SECONDS`, default `30`).
   - Added `openai_max_retries` (`OPENAI_MAX_RETRIES`, default `3`).
   - Added `openai_structured_outputs` (`OPENAI_STRUCTURED_OUTPUTS`, default `True`).
   - Added `validate_openai_credentials` model validator enforcing fail-closed disabled state if API key is absent.
4. `app/models/gemini_provider.py`
   - Inherits from `BaseVLMProvider`.
   - Implements `@property def provider_name(self) -> str: return "google-gemini"`.
5. `app/services/vlm_service.py`
   - Updated constructor to accept `provider: Optional[BaseVLMProvider] = None`.
   - Resolves provider dynamically via `get_vlm_provider(settings_obj=cfg)`.
   - Updated `get_status()` to report honest, dynamic metadata and blocker reasons according to the active provider.
6. `app/services/inspection_orchestrator.py`
   - Updated constructor to accept `vlm_provider: Optional[BaseVLMProvider] = None` while preserving `gemini` parameter as backward-compatible alias.
   - Injects dynamic provider name into provenance dictionary passed to `ResultReconciler`.
7. `app/intelligence/reconciliation.py`
   - Updated provenance builder to extract `"providerName"` dynamically from `gemini_model_info.get("provider", "google-gemini")`.

---

## 4. Provider-Selection Behavior

Provider selection is controlled via configuration settings:
- Configuration key: `vlm_provider` (Environment variable: `AI_VLM_PROVIDER`).
- Allowed values: `"gemini"` (default) or `"openai"`.
- Backward Compatibility:
  - Defaults to `"gemini"`. If no environment variable is provided, the application behaves identically to previous phases.
  - Passing an explicit provider to `VLMService(provider=...)` or `InspectionOrchestrator(vlm_provider=...)` overrides global configuration (essential for unit testing and offline mocking).
- No Silent Switching:
  - If `vlm_provider == "openai"` and OpenAI inference fails, the system returns `GeminiAnalysisStatus.ANALYSIS_FAILED`. It does **not** silently fall back to Gemini.
  - If `vlm_provider == "gemini"` and Gemini inference fails, the system returns `GeminiAnalysisStatus.ANALYSIS_FAILED`. It does **not** silently fall back to OpenAI.

---

## 5. Model Capability Assumptions vs Implementation Facts

| Dimension | Implementation Fact | Capability Assumption / Uncertainty |
|---|---|---|
| **Model Identifier** | Configured via `OPENAI_MODEL` (default: `gpt-4o-mini`). | Assumes `gpt-4o-mini` is the preferred target candidate per Phase 11A cost/latency analysis. Flagship `gpt-4o` is also compatible. |
| **Vision Input Format** | Implemented as Base64 data URI in `messages[1].content[1].image_url.url = "data:{mime};base64,{b64}"` with `"detail": "high"`. | Assumes standard OpenAI Chat Completions multimodal schema. Supported by all current GPT-4o vision models. |
| **Structured Outputs** | Implemented via `response_format={"type": "json_schema", "json_schema": {"strict": True, "schema": ...}}`. Supported fallback to `{"type": "json_object"}` when `openai_structured_outputs=False`. | `json_schema` strict mode requires all schema properties in `required` and `additionalProperties: False`. This was verified against official OpenAI specifications and implemented in `OPENAI_STRUCTURED_RESPONSE_SCHEMA`. |
| **Coordinate System** | Implemented using FireSphere's native `parse_and_validate_bounding_box` utility. Prompts instruct model to return normalized `[ymin, xmin, ymax, xmax]` on a 0–1000 scale. | Vision models can occasionally vary in coordinate precision; `parse_and_validate_bounding_box` handles both 0–1000 integer coordinates and 0.0–1.0 normalized floats, clamping boundary deviations into `[0.0, 1.0]`. |
| **SDK Dependency** | Safe optional import of `openai` library with clean fallback to `httpx.Client` when `openai` is not installed. | Environment did not have `openai` installed; adapter works seamlessly with `httpx` and accepts any mock client with `chat.completions.create`. |

---

## 6. Error Handling and Retry Behavior

The adapter implements strict fault isolation:

1. **Transient Errors (Retried up to `max_retries`):**
   - HTTP 429 (Rate Limit / Quota Spikes).
   - HTTP 500, 502, 503, 504 (Server-side upstream errors).
   - Network connection errors (`httpx.ConnectError`).
   - Request timeouts (`httpx.TimeoutException`, `httpx.ReadTimeout`).
   - Bounded exponential backoff: `backoff = min(1.0 * (2 ** (attempt - 1)), 5.0)` seconds.
2. **Permanent Errors (Non-Retryable, Abort Immediately on Attempt 1):**
   - HTTP 401 Unauthorized (`invalid_api_key`).
   - HTTP 403 Forbidden (`permission_denied`).
   - HTTP 400 Bad Request (`invalid_request_error`).
3. **No Observation Fabrication:**
   - On any failure or invalid response, the adapter returns `observations = []`. It never invents synthetic objects, bounding boxes, or confidence scores.
4. **Secret Redaction:**
   - In all diagnostic logging and returned `error` fields, API keys and Authorization headers are never printed or serialized.

---

## 7. Test Commands and Actual Results

### 7.1 Phase 11B Focused Suite (`test_openai_provider_phase11b.py`)
Execution Command:
```powershell
$env:GEMINI_ENABLED="false"; .\.venv\Scripts\python.exe -m pytest tests/test_openai_provider_phase11b.py -v
```
**Results:** **20 passed, 0 failed, 2 warnings in 0.33s**

Detailed Test Case Breakdown:
- `test_openai_provider_interface_compatibility`: PASSED (Item A: `BaseVLMProvider` interface conformance)
- `test_openai_provider_info_never_leaks_secrets`: PASSED (Credential redaction)
- `test_openai_successful_mocked_response_and_schema_mapping`: PASSED (Items B, C, N, O, P, Q)
- `test_openai_genuine_empty_observations`: PASSED (Distinction between 0 observations and failure)
- `test_openai_inconclusive_response`: PASSED (Inconclusive visual evidence handling)
- `test_openai_missing_api_key_returns_not_configured`: PASSED (Item D: Missing credentials handling)
- `test_openai_disabled_by_default_returns_disabled`: PASSED (Fail-closed disabled default)
- `test_openai_invalid_image_corrupted_payload_fails_safely`: PASSED (Items E, M: Corrupted image rejection)
- `test_openai_sub_resolution_image_rejected`: PASSED (Item E: Sub-resolution image rejection)
- `test_openai_malformed_json_returns_invalid_response`: PASSED (Item F: Non-JSON output handling)
- `test_openai_schema_validation_failure`: PASSED (Item G: Schema validation error handling)
- `test_openai_markdown_fence_stripped_gracefully`: PASSED (Item F: Markdown code fencing stripping)
- `test_openai_connection_failure_fails_safely`: PASSED (Item H: Network connection error handling)
- `test_openai_timeout_fails_safely`: PASSED (Item I: Read timeout error handling)
- `test_openai_http_429_rate_limit_retries_and_exhaustion`: PASSED (Items J, L: 429 rate limit retries)
- `test_openai_http_500_or_503_fails_safely`: PASSED (Item K: 503 Server error retries)
- `test_openai_non_retryable_401_aborts_retries_immediately`: PASSED (Item L: Permanent error retry abort)
- `test_cv_gates_and_calibration_safeguards_remain_untouched`: PASSED (Item R: CV approval gates untouched)
- `test_vlm_service_with_openai_provider`: PASSED (Item T: VLMService integration regression)
- `test_inspection_orchestrator_with_openai_provider`: PASSED (Item T: Orchestrator integration regression)

### 7.2 Core VLM and Orchestration Regression Suite
Execution Command:
```powershell
$env:GEMINI_ENABLED="false"; .\.venv\Scripts\python.exe -m pytest `
  tests/test_openai_provider_phase11b.py `
  tests/test_gemini_foundation_phase1.py `
  tests/test_gemini_vision_phase2.py `
  tests/test_vlm_service_phase5.py `
  tests/test_gemini_phase3_orchestration.py `
  tests/test_compliance_boundary.py `
  tests/test_finding_aggregation.py `
  tests/test_verification_guidance.py `
  tests/test_schemas.py `
  tests/test_m2_m4_contract_specification.py -v
```
**Results:** **149 passed, 0 failed, 5 warnings in 4.82s**

### 7.3 Additional AI Service Core Suite
Execution Command:
```powershell
$env:GEMINI_ENABLED="false"; .\.venv\Scripts\python.exe -m pytest `
  tests/test_health.py `
  tests/test_image_quality.py `
  tests/test_cv_preprocessing.py `
  tests/test_error_handling.py `
  tests/test_evidence_validation.py `
  tests/test_confidence.py `
  tests/test_severity.py `
  tests/test_risk_scoring.py `
  tests/test_report_drafting.py
```
**Results:** **56 passed, 0 failed, 8 warnings in 0.34s**

**Total Executed Tests Across Suites:** **205 passed, 0 failed.**

---

## 8. Security and Governance Considerations

1. **Secret Redaction:**
   - `OPENAI_API_KEY` is loaded into a private instance variable `self._api_key`.
   - `get_provider_info()` explicitly excludes credential fields.
   - Diagnostic loggers format error descriptors (e.g. `ClientError (HTTP 429)`) without echoing request headers, payload contents, or authorization tokens.
2. **Fail-Closed Default:**
   - `OPENAI_ENABLED` defaults to `False`.
   - `validate_openai_credentials` validator in `Settings` ensures that if `OPENAI_ENABLED=True` in environment variables but `OPENAI_API_KEY` is absent or whitespace, `OPENAI_ENABLED` is automatically set to `False` (fail-closed).
3. **Preservation of Existing .env:**
   - Existing `.env` file was **not modified**.
   - Offline test executions utilize process-level environment overrides (`$env:GEMINI_ENABLED="false"`), preventing unintended live outbound requests during automated testing.
4. **Human Verification Invariant:**
   - Every returned observation envelope includes `requiresOfficerVerification=True`, `advisoryStatus="ADVISORY_ONLY"`, `isSafeInference=False`, and `officerReviewRequirement="MANDATORY_OFFICER_REVIEW"`.
   - No code path exists to issue autonomous Fire NOC approvals or rejections.

---

## 9. Known Limitations

1. **No Live Inference Dispatched:**
   - Because Phase 11B is strictly offline, no live connection to `api.openai.com` was established.
   - Live network behavior (DNS resolution, SSL certificate validation, real quota limits) has not yet been executed against OpenAI's live production endpoint.
2. **OpenAI SDK Package Not Pre-Installed:**
   - The `.venv` environment currently has `httpx` (0.28.1) installed, but not `openai`. The adapter includes a built-in `httpx` REST fallback and client dependency injection. For live execution with official SDK features, `openai>=1.40.0` should be installed when permitted.
3. **Local .env State:**
   - The user's existing `.env` file contains `GEMINI_ENABLED=true` from prior Phase 10A testing. In live production or offline CI runs, setting `GEMINI_ENABLED=false` or providing valid credentials is required to prevent unmocked outbound calls.

---

## 10. Remaining Work Before Phase 11C (Controlled Live Pilot)

Before executing Phase 11C (controlled, single-request live validation with OpenAI):
1. **Provision Test API Key:** A valid, funded OpenAI API key with spending limits (e.g. max $1.00 budget) must be provisioned in a secure, non-committed `.env` file.
2. **Optional SDK Installation:** Install `openai>=1.40.0` into `.venv` if the official SDK wrapper is preferred over raw `httpx`.
3. **Execute Single-Request Live Preflight:** Run a controlled single-request preflight with synthetic in-memory test image to verify live endpoint reachability, TLS handshake, and token consumption.
4. **Compare Live Latency and Structured Output Quality:** Compare live response time and bounding box localization accuracy against Phase 10A Gemini baselines.
