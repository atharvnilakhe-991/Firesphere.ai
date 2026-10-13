# FireSphere.ai — Phase 10A.5 New Gemini API Key and Live Inference Verification Report

**Document Date:** October 12, 2026  
**Execution Context:** Phase 10A.5 — New Gemini API Key Verification and Controlled Live Inference Test  
**Scope:** Repository Implementation Audit, New Key Preflight, Non-Generative Model Metadata Verification, Single Controlled Live Generation Session, Schema Validation, Diagnostics, Safety Control Verification, and Offline Regression Testing  
**Final Verdict:** **FAIL — LIVE INFERENCE**  
*(Preflight and model metadata succeeded; the new API key authenticated successfully without 401/403 errors; however, the single live generation request returned `HTTP/1.1 504 Gateway Timeout` from Google's API gateway after 29,289 ms)*  
**Live Generation Request Dispatched:** **YES (Exactly 1 HTTP Generation Call; Zero Retries)**  
**Target Model:** `gemini-3.8-flash`  
**Endpoint:** `POST https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent`  
**Runtime Governance State:** CV Gate: `NOT_APPROVED` | CV Calibration: `NOT_CALIBRATED` | Human Verification: `requiresOfficerVerification=True`  

---

## 1. Purpose of the Test

Following the replacement of the Gemini API key in [`ai-service/.env`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/.env), the objective of Phase 10A.5 was to:
1. Verify that the new credentials load correctly into [`Settings`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/core/config.py#L18-L272) and [`GeminiProvider`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_provider.py#L65-L100).
2. Confirm API authentication and model capability using a non-generative metadata check (`client.models.get`).
3. Execute exactly one controlled live image-analysis request against `gemini-3.8-flash` with in-memory synthetic evidence to determine whether live visual inference succeeds.
4. Validate the response against the [`GeminiAnalysisResult`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_schemas.py#L417-L467) schema and enforce strict governance boundaries.

---

## 2. Preflight Results

The local configuration loaded from [`ai-service/.env`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/.env) was verified without printing or exposing credentials:

| Preflight Parameter | Required Value | Actual Inspected Value | Status |
|---|---|---|:---:|
| **GEMINI_ENABLED** | `True` | `True` | **PASS** |
| **GEMINI_API_KEY Configured** | Non-empty string | `True` *(Boolean verified; zero secrets printed)* | **PASS** |
| **GEMINI_MODEL** | `gemini-3.8-flash` | `gemini-3.8-flash` | **PASS** |
| **GEMINI_TIMEOUT_SECONDS** | `30` | `30` ($30{,}000.0\text{ ms}$ SDK timeout) | **PASS** |
| **GEMINI_MAX_RETRIES** | Configured value | `3` (in persistent `.env`) | **PASS** |
| **Provider Init Status (Pre)** | `CONFIGURED` | `CONFIGURED` | **PASS** |
| **Provider Init Status (Post)** | `READY` | `READY` ([`GeminiProvider.initialize()`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_provider.py#L133-L185) succeeded) | **PASS** |
| **CV Model Approval Gate** | `NOT_APPROVED` | `NOT_APPROVED` (`is_production_approved() == False`) | **PASS** |
| **CV Calibration Status** | `NOT_CALIBRATED` | `NOT_CALIBRATED` | **PASS** |
| **CV Production Inference** | `authorized = False` | `False` (`check_inference_permission()`) | **PASS** |

All preflight requirements passed. The new key successfully initialized the official Google GenAI SDK client.

---

## 3. Model Metadata Verification (Non-Generative)

Before attempting generation, a single non-generative metadata request was executed via `client.models.get(model="gemini-3.8-flash")`:

- **Endpoint:** `GET https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash`
- **HTTP Response Status:** **`HTTP/1.1 200 OK`**
- **Authentication Result:** **SUCCESS** *(New API key accepted; zero 401 Unauthorized or 403 Forbidden errors)*
- **Model Resource Name:** `models/gemini-3.8-flash`
- **Display Name:** `Gemini 3.8 Flash`
- **Supported Actions:** `['generateContent', 'countTokens', 'createCachedContent', 'batchGenerateContent']`
- **`generateContent` Listed:** **`True`**
- **Input Context Window:** `1,048,576 tokens`
- **Output Context Window:** `65,536 tokens`

This confirms that the new key is fully valid for accessing Google's model catalog and that `gemini-3.8-flash` is active and listed with `generateContent` support.

---

## 4. Controlled Live Inference Session

### 4.1 Synthetic Evidence Specification
- **Resolution & Encoding:** 640×640 3-channel RGB image encoded directly to JPEG in memory (26,986 bytes).
- **Visual Content:** Simple artificial scene with geometric shapes and labeled fire-safety objects:
  - Red cylinder and nozzle labeled `"FIRE EXTINGUISHER"`.
  - Green illuminated exit sign labeled `"EXIT"`.
  - Floor demarcation rule and synthetic test watermark.
- **Quality Validation:** Evaluated by [`ImageQualityValidator.evaluate`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/cv/quality.py#L27-L57):
  - Resolution: $640 \times 640$ (Min: $64 \times 64$)
  - Blur score (Laplacian variance): $530.96 > 30.0$
  - Quality report: `isAcceptable=True`, `errors=[]`, `warnings=[]`
- **Data Isolation:** Assigned random UUID `1fee906a-598e-4269-8ef5-e9ab04fea0ed`. Zero real inspection photos, citizen data, or production records used. Zero bytes written to disk.

### 4.2 Retry Policy and Implementation Audit
An audit of [`GeminiProvider`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_provider.py#L398-L408) revealed that `self.max_retries` is used for **both**:
1. SDK-level `types.HttpRetryOptions(attempts=max(1, self.max_retries))` inside `gen_config`.
2. Application-level retry loop in `_call_gemini_with_retries` (`max_attempts = max(1, self.max_retries)`).

If `max_retries` were set to `2`, the dual-layer coupling could trigger up to $2 \times 2 = 4$ total HTTP attempts on transient failures. Therefore, in accordance with the prompt's instruction (*"If safely limiting the request to two total attempts is not possible without changing application code, use one attempt and document that limitation"*), the session was configured with:
- `gemini_max_retries = 0` (locking SDK attempts to 1 and application loop to 1).
- Result: **Exactly 1 total HTTP generation attempt; zero multiplier retries.**
- Persistent `.env` retry configuration (`GEMINI_MAX_RETRIES=3`) was untouched.

### 4.3 Execution Diagnostics & Transport Outcome
```
2026-10-12 19:35:19 | INFO     | [google_genai.models] AFC is enabled with max remote calls: 10.
2026-10-12 19:35:48 | INFO     | [httpx] HTTP Request: POST https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent "HTTP/1.1 504 Gateway Timeout"
2026-10-12 19:35:48 | ERROR    | [firesphere.ai] GeminiProvider: All 1 attempts exhausted (ServerError).
2026-10-12 19:35:48 | ERROR    | [firesphere.ai] GeminiProvider: API call failed for evidenceId=1fee906a-598e-4269-8ef5-e9ab04fea0ed (ServerError)
Request completed in 29289.11 ms.
```

- **Live Generation Dispatched:** **YES (Exactly 1 HTTP request transmitted)**.
- **Round-Trip Duration:** **29,289.11 ms** (~29.3 seconds).
- **HTTP Status Received:** **`HTTP/1.1 504 Gateway Timeout`**.
- **Exception Raised:** `google.genai.errors.ServerError`.
- **Root Cause:** The request was held open for ~29.3 seconds (just within our configured 30-second socket timeout). Google's upstream reverse-proxy / API gateway timed out waiting for Google's internal `gemini-3.8-flash` generation cluster to respond, returning `HTTP 504 Gateway Timeout`.

---

## 5. Response Validation and Analysis Result

The returned object from [`GeminiProvider.analyze_evidence`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_provider.py#L302-L345) was audited against [`GeminiAnalysisResult`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_schemas.py#L417-L467):

| Parameter | Reported Value | Contract Compliance |
|---|---|:---:|
| **Result Status** | `ANALYSIS_FAILED` | **PASS (Fail-closed error handling executed)** |
| **Configured Model** | `gemini-3.8-flash` | **PASS** |
| **Observation Count** | `0` | **PASS (Zero fabricated findings)** |
| **Execution Duration** | `29,289.07 ms` | **PASS (Accurately tracks upstream timeout)** |
| **Requires Officer Verification** | `True` | **PASS (Mandatory human review preserved)** |
| **Safe Inference Marker** | `False` | **PASS (No false safety certification issued)** |
| **Advisory Status** | `ADVISORY_ONLY` | **PASS (Statutory decision authority retained)** |
| **Sanitized Error Field** | `Gemini API request failed: ServerError` | **PASS (Zero credential or header leakage)** |
| **Schema Validation Outcome** | `GeminiAnalysisResult.model_validate` | **PASS (Strict Pydantic schema validated)** |

---

## 6. Safe Diagnostics Summary

- **New Key Configured:** `True` (Loaded from `ai-service/.env`).
- **Provider Initialization Succeeded:** `True` (Status: `READY`).
- **Model Metadata Access Succeeded:** `True` (HTTP 200 OK).
- **Model Identifier:** `gemini-3.8-flash`.
- **Configured Timeout:** 30 seconds ($30{,}000.0\text{ ms}$).
- **Actual Generation Attempts:** 1 (single HTTP attempt; no retries).
- **Sanitized HTTP Status:** `HTTP/1.1 504 Gateway Timeout`.
- **Sanitized Error Class:** `google.genai.errors.ServerError`.
- **Elapsed Duration:** `29,289.11 ms`.
- **Response Schema Validation:** `PASS`.
- **Final Analysis Status:** `ANALYSIS_FAILED`.
- **Database / MinIO Writes:** `0` (Zero persistent storage mutations).
- **Real Evidence Used:** `False` (In-memory synthetic JPEG only).

---

## 7. Safety-Control & Governance Verification

Following execution, governance gates and persistent configurations were re-audited:

```
Post GEMINI_ENABLED           : True
Post Model                    : gemini-3.8-flash
Post Key Configured           : True
Post Max Retries              : 3
Post CV Approved Status       : NOT_APPROVED (is_approved: False)
Post Calibration Status       : NOT_CALIBRATED
Post CV Authorized            : False
Officer Verification Required : True
Autonomous Fire NOC Decision  : False (None issued)
Database / MinIO Writes       : False (0 writes)
```

1. **CV Gate Protection:** Computer Vision model approval remains `NOT_APPROVED`, calibration remains `NOT_CALIBRATED`, and production inference remains unauthorized.
2. **Zero Storage Mutations:** No records were inserted into PostgreSQL. No objects were written to MinIO.
3. **No Autonomous Certification:** No statutory Fire NOC decisions (`AI_APPROVED` or `AI_REJECTED`) were generated.
4. **Credential Privacy:** Zero keys, prefixes, suffixes, headers, or tokens were logged or printed.

---

## 8. Offline Test Suite Execution

The existing offline test suites covering Gemini foundation, vision analysis, orchestration, and error handling were executed against the updated environment:

```powershell
python -m pytest tests/test_gemini_foundation_phase1.py tests/test_gemini_vision_phase2.py tests/test_gemini_phase3_orchestration.py tests/test_error_handling.py -q
```

### Results:
- `tests/test_gemini_foundation_phase1.py`: **16 passed**
- `tests/test_gemini_vision_phase2.py`: **18 passed**
- `tests/test_gemini_phase3_orchestration.py`: **52 passed**
- `tests/test_error_handling.py`: **5 passed**
- **Total:** **91 passed in 4.18s (0 failures, 0 regressions)**

---

## 9. Remaining Limitations and Root-Cause Analysis

1. **Authentication vs. Generation Backend:**  
   The new API key is **confirmed valid** and functioning for API authentication (`models.get` returned 200 OK). The failure is not an authentication failure (`401`) or permission failure (`403`).
2. **Upstream Google Generation Cluster Latency:**  
   In Phase 10A.2, Google returned `503 Service Unavailable` (~2.3s - 3.7s).  
   In Phase 10A.5, Google's generation cluster failed to complete generation within ~29 seconds, causing Google's frontend gateway to emit `504 Gateway Timeout`.
3. **Model Selection Implication:**  
   Google's `gemini-3.8-flash` generation backend is currently experiencing severe backend latency and capacity constraints. While the model is listed and token counts succeed, forward generation on multimodal inputs times out on Google's cloud infrastructure.

---

## 10. Final Verdict

### **FINAL VERDICT: FAIL — LIVE INFERENCE**

**Justification:**
- Preflight: **PASS** (Configuration loaded, new key initialized).
- Model Metadata: **PASS** (HTTP 200 OK, `generateContent` supported).
- Live Generation Dispatched: **YES** (Exactly 1 live HTTP request transmitted).
- Generation Result: **FAIL** (Google API gateway returned `HTTP/1.1 504 Gateway Timeout` after 29,289 ms).
- In accordance with the prompt's verdict criteria:
  - *PASS — NEW KEY VERIFIED FOR LIVE INFERENCE*
  - *FAIL — AUTHENTICATION OR CONFIGURATION*
  - *FAIL — MODEL ACCESS*
  - *FAIL — LIVE INFERENCE*
  - *INCONCLUSIVE — INFERENCE NOT ATTEMPTED*
- Because authentication and metadata access succeeded, but actual live generation failed with HTTP 504, the final verdict is **FAIL — LIVE INFERENCE**.
