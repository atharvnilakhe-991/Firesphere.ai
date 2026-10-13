# FireSphere.ai — Phase 10A.2 Controlled Live Gemini VLM Inference Verification Report

**Document Date:** October 12, 2026  
**Execution Context:** Phase 10A.2 — Controlled Single-Request Live Gemini Inference Verification  
**Scope:** Preflight Verification, In-Memory Synthetic Evidence, Temporary Single-Attempt Provider Configuration, Schema Validation, and Post-Test Governance  
**Final Verdict:** **FAILED (Live Request Dispatched; Failed with HTTP 503 Service Unavailable from Upstream Gemini API)**  
**Live Request Dispatched:** **YES (Exactly 1 Live Call Transmitted; Zero Retries)**  
**Target Endpoint:** `POST https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent`  
**Network & Transport Outcome:** **Completed in 2,340.07 ms (Zero ReadTimeout, Zero Secret Leakage)**  
**Runtime Governance State:** CV Gate: `NOT_APPROVED` | CV Calibration: `NOT_CALIBRATED` | Human Verification: `requiresOfficerVerification=True`  

---

## 1. Executive Summary

In accordance with Phase 10A.2 instructions, exactly **one controlled live inference request** was executed against the newly verified `gemini-3.8-flash` model endpoint using in-memory synthetic image evidence.

### Key Findings:
1. **Model Endpoint Resolution Confirmed:** Unlike the previous Phase 10A run (which returned `HTTP 404 Not Found` for the deprecated `gemini-2.5-flash`), `gemini-3.8-flash` was successfully resolved by Google's API gateway.
2. **Transport & Timeout Resolution:** The request established a secure TLS connection to `https://generativelanguage.googleapis.com` and exchanged headers and body in **2,340.07 ms**. The effective 30.0-second socket timeout was preserved, with zero socket read timeouts.
3. **Upstream Response:** Google's API returned **`HTTP/1.1 503 Service Unavailable`** (`ServerError`), indicating transient high demand or capacity throttling on Google's `gemini-3.8-flash` inference servers.
4. **Strict Single-Attempt Enforcement:** In accordance with the controlled testing protocol (`gemini_max_retries=0`, `attempts=1`), the provider made exactly one attempt and halted immediately without retrying.
5. **Fail-Closed & Schema Validation:** `GeminiProvider` caught the `ServerError`, prevented any unhandled exception or crash, and produced a schema-valid `GeminiAnalysisResult` envelope with `status=ANALYSIS_FAILED`, `requiresOfficerVerification=True`, and `isSafeInference=False`.
6. **Governance Integrity:** All governance gates remained strictly unaltered. CV model approval status is `NOT_APPROVED`, calibration is `NOT_CALIBRATED`, and statutory fire safety decisions remain human-only. Zero database or object storage writes occurred.

---

## 2. Preflight Verification Results

| Preflight Check | Target Requirement | Inspected Value | Status |
|---|---|---|---|
| **1. Gemini Enabled** | `GEMINI_ENABLED = True` | `True` (loaded from `.env`) | **PASS** |
| **2. API Key Present** | Non-empty API key available | Verified present; never printed | **PASS** |
| **3. Configured Model** | `gemini-3.8-flash` | `gemini-3.8-flash` | **PASS** |
| **4. Timeout Setting** | 30 seconds configuration | `30` seconds | **PASS** |
| **5. Effective SDK Timeout** | 30,000 milliseconds | `30000.0` ms ($30.0\text{ s}$) | **PASS** |
| **6. CV Approval Gate** | `NOT_APPROVED` | `NOT_APPROVED` | **PASS** |
| **7. CV Calibration Gate** | `NOT_CALIBRATED` | `NOT_CALIBRATED` | **PASS** |
| **8. CV Production Inference** | `authorized = False` | `False` | **PASS** |

---

## 3. Synthetic Evidence Generation

To protect citizen data, premises privacy, and inspection integrity:
- **Dimensions:** 640×640 pixels, 3-channel RGB.
- **Generation:** Generated entirely in memory using NumPy and OpenCV (`cv2.imencode('.jpg', img)`).
- **Content:** Abstract geometric shapes (red block, green circle, cyan block) on a neutral gray background.
- **Data Isolation:**
  - 0 real fire inspection photographs used.
  - 0 premises addresses, citizen records, or property IDs accessed.
  - 0 database or MinIO records referenced.
  - Assigned temporary random UUID: `b53a8aa6-19d3-46bb-959f-788692173247`.
  - Zero bytes written to persistent storage.

---

## 4. Single Live Request Execution

### 4.1 Temporary Provider Configuration
```python
temp_settings = Settings(
    gemini_enabled=True,
    gemini_api_key=normal_settings.gemini_api_key,
    gemini_model="gemini-3.8-flash",
    gemini_timeout_seconds=30,
    gemini_max_retries=0,  # 0 additional app-level retries
)
sdk_http_opts = types.HttpOptions(
    timeout=30000.0,  # 30,000 ms = 30.0s socket timeout
    retry_options=types.HttpRetryOptions(attempts=1),  # exactly 1 attempt at SDK level
)
client = genai.Client(
    api_key=normal_settings.gemini_api_key,
    http_options=sdk_http_opts,
)
prov = GeminiProvider(settings_obj=temp_settings, client=client)
```

### 4.2 Runtime Execution Log (Sanitized)
```
[google_genai.models] AFC is enabled with max remote calls: 10.
[httpx] HTTP Request: POST https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent "HTTP/1.1 503 Service Unavailable"
[firesphere.ai] GeminiProvider: All 1 attempts exhausted (ServerError).
[firesphere.ai] GeminiProvider: API call failed for evidenceId=b53a8aa6-19d3-46bb-959f-788692173247 (ServerError)
```

### 4.3 Execution Metrics and Schema Outcome

| Metric / Parameter | Value | Assessment |
|---|---|---|
| **Live Request Dispatched** | `True` | Transmitted to Google API gateway |
| **Configured Model** | `gemini-3.8-flash` | Matching application configuration |
| **Provider Status** | `READY` | Provider initialized with injected client |
| **Analysis Result Status** | `ANALYSIS_FAILED` | Fail-closed error handling triggered |
| **Observation Count** | `0` | No fabricated observations |
| **Schema Valid** | `True` | Return object is valid `GeminiAnalysisResult` |
| **Request Duration** | `2340.07 ms` | Full round-trip completed in ~2.34s |
| **Officer Verification Required** | `True` | Human officer review unconditionally required |
| **Premises Safe Certification** | `False` | No false safety certifications issued |
| **Sanitized Error Class** | `str` | Exception sanitized; zero secret leakage |
| **Error Diagnostic** | `Gemini API request failed: ServerError` | 503 Service Unavailable safely handled |

---

## 5. Post-Test Governance Verification

Following execution, references to temporary client and provider objects were released, and normal configuration and governance states were audited:

```
Post GEMINI_ENABLED     : True
Post Model              : gemini-3.8-flash
Post Key Configured     : True
Post CV Approval Status : NOT_APPROVED
Post Calibration Status : NOT_CALIBRATED
Post CV Authorized      : False
```

1. **Persistent Configuration:** [`ai-service/.env`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/.env) remains intact with `GEMINI_MODEL=gemini-3.8-flash`.
2. **Credential Privacy:** The API key remains local and untracked. Zero secrets were logged, serialized, or written to reports.
3. **Computer Vision Gate:** Model approval status remains strictly `NOT_APPROVED`, calibration remains `NOT_CALIBRATED`, and production inference remains unauthorized.
4. **Data Isolation:** Zero database rows, MinIO objects, or audit logs were created.
5. **No Autonomous Decisions:** Zero statutory Fire NOC approvals or rejections were issued.

---

## 6. Connectivity vs. Fire-Safety Quality Distinction

> [!IMPORTANT]
> **API Inference & Schema Compatibility vs. Fire-Safety Quality**  
> 1. **Transport & Schema Contract:** This test verifies that FireSphere's VLM adapter can successfully construct multimodal payloads, connect to Google's API over TLS, enforce millisecond socket timeouts, and encapsulate server-side errors into schema-valid `GeminiAnalysisResult` objects.
> 2. **Not a Safety Approval:** API connectivity does **not** evaluate fire-safety hazard detection accuracy, code compliance, or production readiness. Even when a 200 OK is received with zero observations, absence of findings must never be interpreted as certification that premises are safe (`isSafeInference=False`). Statutory review by authorized human officers (`requiresOfficerVerification=True`) remains mandatory.

---

## 7. Remaining Blocker and Recommended Next Step

* **Current Blocker:** Google's inference backend for `gemini-3.8-flash` returned a transient `503 Service Unavailable`.
* **Recommended Next Step:** Because HTTP 503 is a transient upstream capacity status (unlike a 404 Not Found configuration error), re-attempt the single-request test or evaluate `gemini-flash-latest` if 503 capacity errors persist on `gemini-3.8-flash`.
