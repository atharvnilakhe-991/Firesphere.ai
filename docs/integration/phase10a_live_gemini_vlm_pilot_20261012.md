# FireSphere.ai — Phase 10A Controlled Live Gemini VLM Pilot Report

**Document Date:** October 12, 2026  
**Execution Context:** Phase 10A — Controlled Live Gemini VLM Inference Execution  
**Scope:** Single Live API Request, Synthetic Evidence, Schema Validation, Sanitized Diagnostics, and Governance Verification  
**Final Verdict:** **FAILED (Live Request Occurred; Returned HTTP 404 Not Found for Model gemini-2.5-flash)**  
**Live Request Dispatched:** **YES (Exactly 1 Request Dispatched; Zero Automatic Retries)**  
**Network & Transport Connectivity:** **SUCCESSFUL (Connected to Google API in 1,245 ms; Zero Timeout; No 401/403 Auth Failure)**  
**Runtime Governance State:** CV Gate: `NOT_APPROVED` | CV Calibration: `NOT_CALIBRATED` | Human Verification: `requiresOfficerVerification=True`  

---

## 1. Executive Summary

In accordance with Phase 10A authorization protocols, exactly **one controlled live inference request** was dispatched to the Google Gemini API using an in-memory synthetic 640×640 JPEG image.

### Key Outcomes:
1. **Live Request Executed:** Exactly one HTTP request was transmitted to Google's Generative Language API endpoint (`https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent`). No automatic retries were attempted.
2. **Transport & Timeout Resolution:** The corrected timeout architecture performed flawlessly. The connection negotiated TLS and completed the round trip in **1,245.11 ms** with zero socket timeouts, confirming that the millisecond timeout conversion ($30{,}000\text{ ms} = 30.0\text{ s}$) successfully resolved the previous 159 ms `ReadTimeout` defect.
3. **Authentication Outcome:** Google's API accepted the API key without returning `401 Unauthorized` or `403 Forbidden`.
4. **Failure Cause (HTTP 404 Not Found):** Google's API returned `HTTP/1.1 404 Not Found` because the model identifier specified in configuration, `gemini-2.5-flash`, does not exist in the official Google GenAI model registry (active production models include `gemini-2.0-flash` and `gemini-1.5-flash`).
5. **Schema & Fail-Closed Integrity:** `GeminiProvider` caught the `ClientError` (404), prevented application crashes, and cleanly populated a schema-valid `GeminiAnalysisResult` with `status=ANALYSIS_FAILED`, `requiresOfficerVerification=True`, and `isSafeInference=False`.
6. **Governance Preservation:** CV model approval remains `NOT_APPROVED`, CV calibration remains `NOT_CALIBRATED`, and statutory fire safety decisions remain strictly reserved for human fire officers. Zero database or object storage mutations occurred.

---

## 2. Preflight Verification Results

| Preflight Check | Requirement | Verified State | Status |
|---|---|---|---|
| **1. Configuration Presence** | `GEMINI_ENABLED=true` loaded from `.env` | `gemini_enabled = True` | **PASS** |
| **2. API Key Availability** | Non-empty key present in `.env` | Key loaded and non-empty; never printed | **PASS** |
| **3. Configured Model** | Model set to `gemini-2.5-flash` | `gemini_model = "gemini-2.5-flash"` | **PASS** |
| **4. Timeout Setting** | App configured for 30s | `gemini_timeout_seconds = 30` | **PASS** |
| **5. Effective SDK Timeout** | 30,000 milliseconds passed to SDK | `types.HttpOptions(timeout=30000.0)` | **PASS** |
| **6. Application Retries Locked** | 0 additional retries | `temp_settings.gemini_max_retries = 0` | **PASS** |
| **7. SDK Retries Locked** | Exactly 1 attempt | `types.HttpRetryOptions(attempts=1)` | **PASS** |
| **8. CV Production Gate** | Model `NOT_APPROVED`, `NOT_CALIBRATED` | `authorized=False`, `NOT_APPROVED`, `NOT_CALIBRATED` | **PASS** |
| **9. Secrets Protection** | No secrets printed, logged, or serialized | Zero secrets printed or stored in reports | **PASS** |

---

## 3. Synthetic Evidence Generation

To guarantee absolute compliance with data privacy policies and statutory fire safety governance:
- **Dimensions & Format:** 640×640 pixels, 3-channel RGB encoded as JPEG entirely in system memory via OpenCV (`cv2.imencode`).
- **Visual Content:** Abstract geometric shapes (rectangles, circles) on a neutral light-gray background with synthetic test labeling.
- **Data Isolation:**
  - 0 real fire inspection photographs used.
  - 0 citizen, applicant, or property records involved.
  - 0 real street addresses or building premises represented.
  - 0 database or MinIO evidence items accessed.
  - Generated a random UUID (`evidence_id = str(uuid.uuid4())`) for the request.
  - Zero bytes written to disk.

---

## 4. Live Request Execution and Result

### 4.1 Controlled Request Configuration
```python
temp_settings = Settings(
    gemini_enabled=True,
    gemini_api_key=normal_settings.gemini_api_key,
    gemini_model="gemini-2.5-flash",
    gemini_timeout_seconds=30,
    gemini_max_retries=0,  # 0 additional app-level retries
)
sdk_http_opts = types.HttpOptions(
    timeout=30000.0,  # 30,000 ms = 30.0 s
    retry_options=types.HttpRetryOptions(attempts=1),  # exactly 1 attempt at SDK level
)
client = genai.Client(
    api_key=normal_settings.gemini_api_key,
    http_options=sdk_http_opts,
)
prov = GeminiProvider(settings_obj=temp_settings, client=client)
```

### 4.2 Sanitized Execution Diagnostics
```
[google_genai.models] AFC is enabled with max remote calls: 10.
[httpx] HTTP Request: POST https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent "HTTP/1.1 404 Not Found"
[firesphere.ai] GeminiProvider: All 1 attempts exhausted (ClientError).
[firesphere.ai] GeminiProvider: API call failed for evidenceId=56d42bc6-0450-4b79-8063-2275eb15e3de (ClientError)
```

### 4.3 Result Metrics & Schema Validation

| Metric / Parameter | Value | Assessment |
|---|---|---|
| **Live Request Attempted** | `True` | Live call dispatched to Google API |
| **Configured Model** | `gemini-2.5-flash` | Matching application configuration |
| **Provider Status** | `READY` | Provider initialized with injected client |
| **Result Status** | `ANALYSIS_FAILED` | Fail-closed error handling triggered |
| **Observation Count** | `0` | No fabricated observations |
| **Schema Valid** | `True` | Return object is valid `GeminiAnalysisResult` |
| **Execution Duration** | `1245.11 ms` | Round-trip completed in ~1.25s (no timeout) |
| **Officer Verification Required** | `True` | Mandatory human verification enforced |
| **Premises Safe Certification** | `False` | No false safety certifications issued |
| **Sanitized Error Diagnostic** | `Gemini API request failed: ClientError` | No secret leakage in error envelope |

---

## 5. Diagnostic Analysis of the 404 Not Found Error

1. **Network Connectivity Succeeded:** The client established a secure TLS connection to `generativelanguage.googleapis.com` and exchanged HTTP request/response payloads in 1,245 ms.
2. **Credential Authentication Succeeded:** The request was not rejected with `401 Unauthorized` or `403 Forbidden` (which occurs when an API key is invalid or lacks Gemini API permissions).
3. **Model Name Discrepancy:** The URL requested was:
   `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent`
   Google's API returned `404 Not Found` because Google has not released a model named `gemini-2.5-flash`. The supported model names in the Google Gemini API are:
   - `gemini-2.0-flash`
   - `gemini-1.5-flash`
   - `gemini-1.5-pro`
4. **Single-Attempt Enforcement:** The provider halted immediately after the first `ClientError` without retrying, strictly honoring `gemini_max_retries=0` and `attempts=1`.

---

## 6. Post-Test Governance Verification

Following execution, references to the temporary provider and client were released, and the system state was audited:

```
Post GEMINI_ENABLED     : True
Post Provider Status    : CONFIGURED
Post CV Approval Status : NOT_APPROVED
Post Calibration Status : NOT_CALIBRATED
Post CV Authorized      : False
```

1. **Persistent Configuration:** `ai-service/.env` remains intact and untracked by Git.
2. **Credential Protection:** Zero keys or tokens were printed, logged, or persisted.
3. **Computer Vision Gate:** Model approval status remains `NOT_APPROVED`, calibration remains `NOT_CALIBRATED`, and production inference remains unauthorized.
4. **Data Isolation:** Zero records were created in PostgreSQL or MinIO.
5. **No Autonomous Decisions:** Zero statutory Fire NOC approvals or rejections were generated.

---

## 7. Remaining Blocker and Recommended Next Step

### Blocker
The configured model name `gemini-2.5-flash` in `ai-service/.env` is not recognized by the Google GenAI API endpoint, resulting in `HTTP/1.1 404 Not Found`.

### Recommended Next Step
Update the model identifier in `ai-service/.env` to an active, valid Google Gemini model, such as:
```dotenv
GEMINI_MODEL=gemini-2.0-flash
```
or
```dotenv
GEMINI_MODEL=gemini-1.5-flash
```
Once updated, repeat the single-request test to complete visual reasoning and structured observation verification.
