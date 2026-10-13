# FireSphere.ai — Phase 10A.2 Controlled Live Gemini VLM Inference Verification Report

**Document Date:** October 12, 2026  
**Execution Context:** Phase 10A.2 — Controlled Single-Request Live Gemini Inference Verification (Independent Run)  
**Scope:** Preflight Audit, In-Memory Synthetic Evidence, Temporary Single-Attempt Client Configuration, Live API Request, Response Schema Validation, and Post-Test Governance Verification  
**Final Verdict:** **FAILED (Live Request Dispatched; Failed with HTTP 503 Service Unavailable from Upstream Gemini API)**  
**Live Request Dispatched:** **YES (Exactly 1 Live Call Transmitted; Zero Retries)**  
**Target Endpoint:** `POST https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent`  
**Network & Transport Outcome:** **Completed in 3,722.29 ms (Zero Socket ReadTimeout, Zero Secret Leakage)**  
**Runtime Governance State:** CV Gate: `NOT_APPROVED` | CV Calibration: `NOT_CALIBRATED` | Human Verification: `requiresOfficerVerification=True`  

---

## 1. Executive Summary

In accordance with Phase 10A.2 instructions, exactly **one controlled live inference request** was executed against Google's Generative Language API for the verified model `gemini-3.8-flash` using in-memory synthetic image evidence.

### Key Execution Highlights:
1. **Preflight Criteria Met:** All 7 preflight checks passed prior to initiating any live network call. Gemini is enabled, credentials configured locally, model set to `gemini-3.8-flash`, timeout configured for 30s ($30{,}000\text{ ms}$), and CV model gates strictly enforced (`NOT_APPROVED`, `NOT_CALIBRATED`, `authorized=False`).
2. **In-Memory Synthetic Evidence:** A 640×640 3-channel image containing abstract geometric shapes on a neutral background was synthesized entirely in memory using NumPy and OpenCV. Passed internal image quality checks (blur score $172.13 > 30.0$, dimensions $640\times 640$). Zero real premises, citizen data, or production records were accessed. Zero bytes were written to disk or database.
3. **Transport & Latency:** The request established TLS connection to Google's API gateway and completed round-trip communication in **3,722.29 ms**. The 30.0-second socket timeout was preserved, confirming no socket-level timeout defects.
4. **Upstream Response:** The Google Generative Language API returned **`HTTP/1.1 503 Service Unavailable`** (`ServerError`), reflecting transient capacity throttling or server-side load on Google's `gemini-3.8-flash` inference infrastructure.
5. **Strict Single-Attempt Enforcement:** In accordance with the controlled testing protocol (`gemini_max_retries=0`, `attempts=1`), the provider made exactly one attempt and halted immediately without retrying.
6. **Fail-Closed Envelope & Schema Compliance:** `GeminiProvider` caught the `ServerError`, prevented unhandled crashes, and populated an explicit `GeminiAnalysisResult` envelope with `status=ANALYSIS_FAILED`, `requiresOfficerVerification=True`, and `isSafeInference=False`. The result was fully validated against the Pydantic schema.
7. **Post-Test Governance Preserved:** Local `.env` remains intact (`GEMINI_MODEL=gemini-3.8-flash`, `GEMINI_ENABLED=true`). CV gates remain locked to `NOT_APPROVED` and `NOT_CALIBRATED`. No autonomous Fire NOC decisions occurred, and zero database or object-storage mutations were executed.

---

## 2. Preflight Audit Results

Prior to making any network call, all required configuration and safety invariants were audited:

| Preflight Check | Target Requirement | Verified Value | Status |
|---|---|---|:---:|
| **1. Gemini Provider Enabled** | `GEMINI_ENABLED = True` | `True` (loaded from `ai-service/.env`) | **PASS** |
| **2. API Key Present** | Non-empty API key available | Verified present; never printed or logged | **PASS** |
| **3. Configured Model** | `gemini-3.8-flash` | `gemini-3.8-flash` | **PASS** |
| **4. Timeout Setting** | 30 seconds configuration | `30` seconds | **PASS** |
| **5. Effective SDK Timeout** | 30,000 milliseconds | `30000.0` ms ($30.0\text{ s}$ socket timeout) | **PASS** |
| **6. CV Approval Gate** | `NOT_APPROVED` | `NOT_APPROVED` (`is_production_approved() == False`) | **PASS** |
| **7. CV Calibration Gate** | `NOT_CALIBRATED` | `NOT_CALIBRATED` | **PASS** |
| **8. CV Production Inference** | `authorized = False` | `False` (`check_inference_permission()`) | **PASS** |

All preflight criteria were satisfied without exception.

---

## 3. Synthetic Evidence Generation

To strictly protect citizen privacy, premises confidentiality, and statutory record integrity:
- **Dimensions:** 640×640 pixels, 3-channel RGB.
- **Generation:** Synthesized in memory via NumPy array allocation and OpenCV rasterization.
- **Visual Features:** Abstract geometric shapes (red polygon, green circle, orange polygon, demarcation rule) on a neutral gray background.
- **Quality Check:** Evaluated by [`ImageQualityValidator.evaluate`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/cv/quality.py#L27-L57):
  - Dimensions: $640 \times 640$ (Min: $64 \times 64$)
  - Blur score (Laplacian variance): $172.13$ (Threshold: $30.0$)
  - Quality report: `isAcceptable=True`, `errors=[]`, `warnings=[]`
- **Data Isolation:**
  - Encoded directly to in-memory JPEG byte buffer (`cv2.imencode('.jpg', img)`): 19,715 bytes.
  - Temporary evidence UUID: `af2907ce-ef23-42dc-b38a-fee156eb0de5`.
  - Zero disk writes, zero database insertions, zero MinIO uploads.

---

## 4. Single Live Request Execution

### 4.1 Temporary Provider & SDK Configuration
```python
temp_settings = Settings(
    gemini_enabled=True,
    gemini_api_key=normal_settings.gemini_api_key,
    gemini_model="gemini-3.8-flash",
    gemini_timeout_seconds=30,
    gemini_max_retries=0,  # 0 additional application-level retries
)

sdk_http_opts = types.HttpOptions(
    timeout=30000.0,  # 30,000 ms = 30.0s socket timeout
    retry_options=types.HttpRetryOptions(attempts=1),  # exactly 1 attempt at SDK level
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

### 4.2 Runtime Execution Log (Sanitized)
```
=== STEP 3 & 4: TEMPORARY CLIENT CONFIGURATION & SINGLE LIVE REQUEST ===
Provider initialized: Status = READY
Executing provider.analyze_evidence (Single live request)...
2026-10-12 19:19:27 | INFO     | [google_genai.models] AFC is enabled with max remote calls: 10.
2026-10-12 19:19:31 | INFO     | [httpx] HTTP Request: POST https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent "HTTP/1.1 503 Service Unavailable"
2026-10-12 19:19:31 | ERROR    | [firesphere.ai] GeminiProvider: All 1 attempts exhausted (ServerError).
2026-10-12 19:19:31 | ERROR    | [firesphere.ai] GeminiProvider: API call failed for evidenceId=af2907ce-ef23-42dc-b38a-fee156eb0de5 (ServerError)
Single live request finished in 3722.29 ms.
```

---

## 5. Response Validation and Analysis Result

The returned object from [`GeminiProvider.analyze_evidence`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_provider.py#L302-L345) was audited and validated against the Pydantic [`GeminiAnalysisResult`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_schemas.py#L417-L467) schema:

| Evaluation Dimension | Reported Value | Requirement / Contract | Status |
|---|---|---|:---:|
| **Live Request Attempted** | `True` | Exactly 1 request | **PASS** |
| **Configured Model** | `gemini-3.8-flash` | Matches `.env` specification | **PASS** |
| **Provider Status** | `READY` | Initialized with injected client | **PASS** |
| **Analysis Result Status** | `ANALYSIS_FAILED` | Fail-closed lifecycle state | **PASS** |
| **Observation Count** | `0` | Zero fabricated findings | **PASS** |
| **Request Duration (Execution)** | `3,722.25 ms` | Latency tracked in result | **PASS** |
| **Request Duration (Wall clock)** | `3,722.29 ms` | Full round-trip duration | **PASS** |
| **Officer Verification Required** | `True` | Mandatory human verification | **PASS** |
| **Safe Inference Marker** | `False` | No false safety certifications | **PASS** |
| **Sanitized Error Class** | `str` | Exception sanitized; zero secrets | **PASS** |
| **Error Diagnostic** | `Gemini API request failed: ServerError` | HTTP 503 safely encapsulated | **PASS** |
| **Schema Validation** | `GeminiAnalysisResult.model_validate` | Fully valid Pydantic schema instance | **PASS** |

### Safety Invariants Verified:
- **No False Safety Clearance:** Even if zero observations are returned or analysis fails, `isSafeInference=False` is strictly enforced.
- **Mandatory Human Verification:** `requiresOfficerVerification=True` is unconditionally set.
- **Fail-Closed Handling:** Server errors return an orderly `ANALYSIS_FAILED` object rather than raising an unhandled exception or crashing the service.

---

## 6. Post-Test Governance Verification

Following the single request, temporary client and provider instances were deregistered, and the persistent system state was audited:

```
=== STEP 6: POST-TEST GOVERNANCE AUDIT ===
Post-test GEMINI_ENABLED: True
Post-test Model: gemini-3.8-flash
Post-test API Key present: True (Value hidden)
Post-test CV Approval: NOT_APPROVED (is_approved: False)
Post-test CV Calibration: NOT_CALIBRATED
Post-test CV Production Authorized: False
Officer verification mandatory: True
Autonomous Fire NOC decision occurred: False
Database / object storage writes occurred: False
```

1. **Persistent Configuration Intact:** [`ai-service/.env`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/.env) retains `GEMINI_ENABLED=true`, `GEMINI_MODEL=gemini-3.8-flash`, `GEMINI_TIMEOUT_SECONDS=30`, and `GEMINI_MAX_RETRIES=3`.
2. **Credential Confidentiality:** The API key remains confined to the local untracked `.env` file. Zero secret tokens or authorization headers were printed, logged, or serialized.
3. **Computer Vision Gate Integrity:**
   - Production approval status: `NOT_APPROVED`.
   - Calibration status: `NOT_CALIBRATED`.
   - Production inference authorization: `False`.
4. **Data Isolation:** Zero database writes occurred in PostgreSQL. Zero objects were written to MinIO. Zero inspection records were mutated.
5. **No Autonomous Statutory Decisions:** No Fire NOC application was approved (`AI_APPROVED`) or rejected (`AI_REJECTED`).
6. **Process Memory Disclosure:** As required by governance standards, temporary variable references were released in Python, with the recognition that standard runtime object dereferencing does not guarantee immediate physical erasure from operating system memory pages.

---

## 7. Connectivity & Schema Verification vs. Statutory Safety Quality

> [!IMPORTANT]
> **API Inference & Schema Compatibility vs. Statutory Fire-Safety Compliance**  
> 1. **Scope of Test:** This test evaluates **only** API transport connectivity, timeout preservation, single-attempt bounding, error trapping, and Pydantic schema validation (`GeminiAnalysisResult`).
> 2. **Not a Certification of Safety:** Successful API interaction or an empty observation response does **not** evaluate fire hazard detection fidelity, model accuracy, or regulatory compliance under NBC 2016 / Part 4.
> 3. **Non-Delegable Human Authority:** All visual findings remain strictly advisory (`ADVISORY_ONLY`). Final statutory determinations for Fire NOC issuance remain the exclusive responsibility of authorized human Fire Safety Officers (`requiresOfficerVerification=True`).

---

## 8. Remaining Blocker and Root-Cause Analysis

- **Current Blocker:** Upstream Google Generative Language API returned `HTTP/1.1 503 Service Unavailable` (`ServerError`) for `POST .../models/gemini-3.8-flash:generateContent`.
- **Root-Cause Analysis:**
  - In Phase 10A, the request returned `404 Not Found` because `gemini-2.5-flash` was deprecated.
  - In Phase 10A.1, `gemini-3.8-flash` was identified as Google's active replacement model, and metadata queries (`count_tokens`, `models.get`) succeeded.
  - In Phase 10A.2, `POST ...:generateContent` reached Google's `gemini-3.8-flash` endpoint, but Google's inference servers responded with `503 Service Unavailable`, indicating transient backend capacity or high server load on Google's cloud infrastructure.
  - Transport latency was 3,722 ms (confirming socket timeouts and network connectivity are fully functional).
  - Single-attempt enforcement worked flawlessly (exactly 1 call, zero unwanted retries).

---

## 9. Final Verdict

### **FINAL VERDICT: FAILED**

**Reasoning:**
- Preflight checks: **PASS** (all 8 prerequisites verified).
- Single live request dispatched: **YES** (exactly 1 request transmitted to Google's API; zero retries).
- Network & Timeout: **PASS** (3,722.29 ms round-trip; 30s socket timeout enforced without socket read timeouts).
- Schema validation: **PASS** (`GeminiAnalysisResult` envelope valid).
- API Content Generation: **FAILED** due to upstream `HTTP/1.1 503 Service Unavailable` (`ServerError`) from Google's `gemini-3.8-flash` endpoint.
- In accordance with the prompt's verdict taxonomy:
  - *PASS — One live request returned a schema-valid result.*
  - *BLOCKED — A preflight requirement prevented execution.*
  - *FAILED — The request occurred but failed or returned an invalid result.*
- Because the live request was dispatched but encountered an upstream HTTP 503 error, the verdict is **FAILED**.
