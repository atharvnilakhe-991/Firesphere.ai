# FireSphere.ai — Phase 10A.7 Final Controlled Gemini Live Inference Verification Report

**Document Date:** October 12, 2026  
**Execution Context:** Phase 10A.7 — Final Controlled Gemini Live Inference Verification  
**Scope:** Preflight Verification, Synthetic In-Memory Evidence, Controlled 2-Attempt Live Execution, Sanitized Diagnostics, Response Schema Validation, and Final Decision Architecture  
**Final Verdict:** **FAIL — LIVE INFERENCE UNAVAILABLE**  
*(Preflight passed; authentication and model access succeeded; exactly 2 bounded HTTP attempts were dispatched with 1.0s backoff; Google's remote inference cluster returned `HTTP/1.1 503 Service Unavailable` on both attempts)*  
**Live Generation Request Dispatched:** **YES (Exactly 2 HTTP Generation Calls Dispatched; Zero Multiplier Retries)**  
**Target Model:** `gemini-3.8-flash`  
**Endpoint:** `POST https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent`  
**Runtime Governance State:** CV Gate: `NOT_APPROVED` | CV Calibration: `NOT_CALIBRATED` | Human Verification: `requiresOfficerVerification=True`  

---

## 1. Executive Summary

In accordance with Phase 10A.7 instructions, the **final planned controlled live inference test** of Google's Gemini Vision-Language Model was executed using the corrected, decoupled retry architecture.

### Key Outcomes:
1. **Preflight Criteria Met:** All preflight checks passed prior to initiating live generation. The new API key is active and authenticated, `gemini-3.8-flash` is configured, socket timeout is set to 30.0s ($30{,}000\text{ ms}$), the retry-decoupling correction is active, and CV safety gates remain locked (`NOT_APPROVED`, `NOT_CALIBRATED`, `authorized = False`).
2. **Strict 2-Attempt Boundary Honored:**
   - The decoupled retry architecture performed with mathematical precision.
   - Attempt 1 failed at ~2.0s with `HTTP/1.1 503 Service Unavailable`.
   - The provider logged a transient error and paused for exactly 1.0s exponential backoff.
   - Attempt 2 failed at ~3.5s with `HTTP/1.1 503 Service Unavailable`.
   - The provider halted immediately, having exhausted the configured 2-attempt limit.
   - **Exactly 2 total HTTP requests were dispatched.** The previous accidental $N \times M$ multiplier was completely absent.
3. **Fail-Closed Envelope & Schema Validation:**
   - The provider returned a schema-valid [`GeminiAnalysisResult`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_schemas.py#L417-L467) envelope with `status = ANALYSIS_FAILED`.
   - Preserved `error = "Gemini API request failed: ServerError (HTTP 503)"`.
   - `observations = []` (zero fabricated observations).
   - `requiresOfficerVerification = True` and `isSafeInference = False` strictly enforced.
4. **Final Decision:**
   - Across Phase 10A.2 (HTTP 503), Phase 10A.5 (HTTP 504 Gateway Timeout), and Phase 10A.7 (HTTP 503 across both attempts), Google's remote inference cluster for `gemini-3.8-flash` has repeatedly demonstrated severe upstream unavailability and latency for multimodal vision inference.
   - In accordance with the prompt's explicit decision rule, **all live testing of Gemini is now stopped**, and FireSphere.ai will transition to a structured evaluation of alternative VLM providers.

---

## 2. Preflight Audit Results

Prior to making any network call, all required configuration and safety invariants were audited:

| Preflight Parameter | Target Requirement | Actual Inspected Value | Status |
|---|---|---|:---:|
| **GEMINI_ENABLED** | `True` | `True` | **PASS** |
| **GEMINI_API_KEY Configured** | Non-empty string | `True` *(Boolean verified; zero secrets printed)* | **PASS** |
| **GEMINI_MODEL** | `gemini-3.8-flash` | `gemini-3.8-flash` | **PASS** |
| **GEMINI_TIMEOUT_SECONDS** | `30` | `30` ($30{,}000.0\text{ ms}$ SDK timeout) | **PASS** |
| **Retry Decoupling Present** | `gen_http_retry_opts.attempts = 1` | Verified present in `analyze_evidence` | **PASS** |
| **SDK Attempts per Invocation** | `1` | Verified locked to 1 per call | **PASS** |
| **Application Total-Attempt Limit**| `2` | Configured for controlled test (`max_retries = 2`) | **PASS** |
| **CV Model Approval Gate** | `NOT_APPROVED` | `NOT_APPROVED` (`is_production_approved() == False`) | **PASS** |
| **CV Calibration Status** | `NOT_CALIBRATED` | `NOT_CALIBRATED` | **PASS** |
| **CV Production Inference** | `authorized = False` | `False` (`check_inference_permission()`) | **PASS** |

All preflight checks passed.

---

## 3. Synthetic Evidence Generation

To protect inspection record integrity, premises privacy, and citizen confidentiality:
- **Dimensions & Encoding:** 640×640 pixels, 3-channel RGB encoded to JPEG in memory (26,672 bytes).
- **Scene Composition:** Simple artificial scene containing geometric shapes and labeled fire-safety objects:
  - Red cylinder and nozzle labeled `"FIRE EXTINGUISHER"`.
  - Green illuminated exit sign labeled `"EXIT"`.
  - Ground demarcation rule and synthetic watermark (`"FINAL CONTROLLED TEST - PHASE 10A.7"`).
- **Quality Evaluation:** Evaluated by [`ImageQualityValidator.evaluate`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/cv/quality.py#L27-L57):
  - Blur score (Laplacian variance): $522.69 > 30.0$.
  - Dimensions: $640 \times 640$.
  - Quality report: `isAcceptable = True`, `errors = []`, `warnings = []`.
- **Data Isolation:**
  - Assigned temporary random UUID: `1614f398-38d4-4fa1-82f5-1e1b1e8c033c`.
  - Zero real photographs, premises addresses, or citizen data used.
  - Zero bytes written to disk, PostgreSQL, or MinIO.

---

## 4. Controlled Live Request Execution & Diagnostics

### 4.1 Execution Configuration
```python
temp_settings = Settings(
    gemini_enabled=True,
    gemini_api_key=normal_settings.gemini_api_key,
    gemini_model="gemini-3.8-flash",
    gemini_timeout_seconds=30,
    gemini_max_retries=2,  # Total-attempt limit = 2
)

sdk_http_opts = types.HttpOptions(
    timeout=30000.0,
    retry_options=types.HttpRetryOptions(attempts=1),
)

temp_client = genai.Client(
    api_key=normal_settings.gemini_api_key,
    http_options=sdk_http_opts,
)

provider = GeminiProvider(
    settings_obj=temp_settings,
    client=temp_client,
)
```

### 4.2 Sanitized Runtime Log
```
=== STEP 2: CONTROLLED LIVE REQUEST EXECUTION ===
Provider status: READY
Dispatching controlled live image-analysis request (max 2 total HTTP attempts)...
2026-10-12 19:54:32 | INFO     | [google_genai.models] AFC is enabled with max remote calls: 10.
2026-10-12 19:54:34 | INFO     | [httpx] HTTP Request: POST https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent "HTTP/1.1 503 Service Unavailable"
2026-10-12 19:54:34 | WARNING  | [firesphere.ai] GeminiProvider: Transient error on attempt 1/2 (ServerError). Retrying in 1.0s...
2026-10-12 19:54:35 | INFO     | [google_genai.models] AFC is enabled with max remote calls: 10.
2026-10-12 19:54:39 | INFO     | [httpx] HTTP Request: POST https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent "HTTP/1.1 503 Service Unavailable"
2026-10-12 19:54:39 | ERROR    | [firesphere.ai] GeminiProvider: All 2 attempts exhausted (ServerError).
2026-10-12 19:54:39 | ERROR    | [firesphere.ai] GeminiProvider: API call failed for evidenceId=1614f398-38d4-4fa1-82f5-1e1b1e8c033c (ServerError (HTTP 503))
Live request session completed in 6744.72 ms.
```

### 4.3 Attempt Breakdown:
- **Attempt 1:** Initiated at `19:54:32`. Returned `HTTP/1.1 503 Service Unavailable` in **~2,000 ms**.
- **Transient Backoff:** Slept **1.0 second** exponential backoff (`Retrying in 1.0s...`).
- **Attempt 2:** Initiated at `19:54:35`. Returned `HTTP/1.1 503 Service Unavailable` in **~3,500 ms**.
- **Attempt Exhaustion:** Logged `All 2 attempts exhausted (ServerError)`.
- **Total HTTP Attempts:** **Exactly 2** (zero accidental multiplier retries).
- **Total Session Elapsed Time:** **6,744.72 ms**.

---

## 5. Response & Schema Validation

The returned object from [`GeminiProvider.analyze_evidence`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_provider.py#L302-L345) was audited against [`GeminiAnalysisResult`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_schemas.py#L417-L467):

| Parameter | Reported Value | Governance Contract | Status |
|---|---|---|:---:|
| **Analysis Status** | `ANALYSIS_FAILED` | Fail-closed lifecycle state | **PASS** |
| **Configured Model** | `gemini-3.8-flash` | Matches configuration | **PASS** |
| **Observation Count** | `0` | Zero fabricated findings | **PASS** |
| **Execution Duration** | `6,744.68 ms` | Round-trip tracked inside result envelope | **PASS** |
| **Officer Verification Required** | `True` | Mandatory human verification preserved | **PASS** |
| **Safe Inference Marker** | `False` | Zero false safety certifications | **PASS** |
| **Advisory Status** | `ADVISORY_ONLY` | Governance boundary preserved | **PASS** |
| **Sanitized Error Field** | `Gemini API request failed: ServerError (HTTP 503)` | Safe HTTP status preserved without secret leakage | **PASS** |
| **Schema Validation Outcome** | `GeminiAnalysisResult.model_validate` | Fully valid Pydantic schema instance | **PASS** |

---

## 6. Post-Test Governance Verification

Following execution, temporary client references were released, and the persistent system state was audited:

```
Post-test GEMINI_ENABLED           : True
Post-test Model                    : gemini-3.8-flash
Post-test Key Configured           : True (Value hidden)
Post-test Max Retries              : 3 (Persistent .env setting intact)
Post-test CV Approved Status       : NOT_APPROVED
Post-test CV Calibration Status    : NOT_CALIBRATED
Post-test CV Production Authorized : False
Officer Verification Required      : True
Autonomous Fire NOC Decision       : False (None issued)
Database / MinIO Writes            : False (0 writes)
```

1. **Persistent Configuration Intact:** [`ai-service/.env`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/.env) retains `GEMINI_MAX_RETRIES=3`, `GEMINI_MODEL=gemini-3.8-flash`, and `GEMINI_TIMEOUT_SECONDS=30`.
2. **Credential Confidentiality:** Zero secret keys, tokens, or authorization headers were logged or printed.
3. **Computer Vision Gate Protection:** Model approval remains `NOT_APPROVED`, calibration remains `NOT_CALIBRATED`, and production inference remains unauthorized.
4. **Data Isolation:** Zero rows written to PostgreSQL. Zero objects uploaded to MinIO.
5. **No Autonomous Statutory Decisions:** No Fire NOC application was approved (`AI_APPROVED`) or rejected (`AI_REJECTED`).

---

## 7. Comparative Diagnostic History Across All Phases

| Phase | Test Scope | Model | Attempts Configured | Actual HTTP Calls | Transport Outcome | Result |
|---|---|---|:---:|:---:|---|---|
| **Phase 10A** | Controlled live request | `gemini-2.5-flash` | 1 | 1 | Connected in 1,245 ms; `HTTP 404 Not Found` | Handled: Model identifier deprecated/unavailable |
| **Phase 10A.1** | Discovery (Non-generative) | `gemini-3.8-flash` | 1 | 1 | `HTTP 200 OK`; Multimodal token count = 1,093 | Succeeded: Model active in catalog |
| **Phase 10A.2** | Controlled live request | `gemini-3.8-flash` | 1 | 1 | Connected in 3,722 ms; `HTTP 503 Service Unavailable` | Handled: Transient upstream server load |
| **Phase 10A.3** | Non-generative diagnostic | `gemini-3.8-flash` | 1 | 1 | `HTTP 200 OK`; metadata retrieved | Succeeded: Model supports `generateContent` |
| **Phase 10A.5** | New key live verification | `gemini-3.8-flash` | 1 | 1 | Connected; `HTTP 504 Gateway Timeout` at 29,289 ms | Handled: Upstream cloud cluster timeout |
| **Phase 10A.6** | Offline audit & hardening | `gemini-3.8-flash` | N/A | 0 (Offline) | 93/93 tests passed; retry decoupled | Defect fixed: Multiplier eliminated |
| **Phase 10A.7** | Final controlled retest | `gemini-3.8-flash` | 2 | 2 | Attempt 1: 503 (2.0s) -> 1.0s backoff -> Attempt 2: 503 (3.5s) | **FAIL — LIVE INFERENCE UNAVAILABLE** |

---

## 8. Final Decision and Architecture Recommendation

### Decision:
In accordance with the project instructions:
> *"If the test fails with another transient 503/504 or gateway timeout after the permitted attempts, recommend stopping Gemini live testing and moving to a structured evaluation of alternative VLM providers. Do not automatically switch models or initiate another live test."*

### Recommendation:
1. **Halt Live Gemini Testing:** Terminate all further live generation attempts against the Google Gemini API.
2. **Architecture Assessment:** While the FireSphere.ai Gemini provider adapter is architecturally robust, fail-closed, schema-compliant, and fully tested (93/93 passing tests), Google's cloud generation backend for `gemini-3.8-flash` cannot reliably serve multimodal image inference within statutory operational timeframes.
3. **Structured Alternative VLM Evaluation:** Proceed to evaluate alternative Vision-Language Models that offer predictable latency and high availability for municipal fire safety inspections (e.g., self-hosted / on-premise vision models such as Qwen2-VL / LLaVA, or enterprise cloud VLM endpoints with provisioned throughput).

---

## 9. Final Verdict

### **FINAL VERDICT: FAIL — LIVE INFERENCE UNAVAILABLE**

**Justification:**
- Preflight: **PASS** (Credentials, model, timeout, and decoupling verified).
- Model Metadata: **PASS** (API key is valid; model is active in Google's catalog).
- Actual Generation Dispatched: **YES** (Exactly 2 controlled HTTP generation attempts dispatched with 1.0s backoff).
- Generation Outcome: **FAIL** (Google's inference cluster returned `HTTP/1.1 503 Service Unavailable` on both Attempt 1 and Attempt 2).
- Schema & Error Handling: **PASS** (Surfaced cleanly as `status = ANALYSIS_FAILED` with `ServerError (HTTP 503)`).
- In accordance with the prompt's verdict options:
  - *PASS — LIVE INFERENCE SUCCESSFUL*
  - *FAIL — LIVE INFERENCE UNAVAILABLE*
  - *FAIL — AUTHENTICATION OR CONFIGURATION*
  - *FAIL — MODEL ACCESS*
  - *INCONCLUSIVE — REQUEST NOT SENT*
- Because authentication and model access succeeded, but actual live generation failed due to upstream server unavailability across all permitted attempts, the final verdict is **FAIL — LIVE INFERENCE UNAVAILABLE**.
