# FireSphere.ai — Phase 10A.6A Final Preflight Review Before Gemini Retest

**Document Date:** October 12, 2026  
**Execution Context:** Phase 10A.6A — Final Preflight Review and Offline Validation Prior to Controlled Live Inference Retest  
**Scope:** Review Retry-Decoupling Implementation, Verify Attempt Mapping, Inspect 503/504 Exception Handling, Execute Offline Test Suite, and Confirm Safety Governance  
**Final Verdict:** **READY_FOR_FINAL_CONTROLLED_TEST**  
**Live Content Generation Dispatched in this Phase:** **NONE (Strictly Offline Review)**  
**Runtime Governance State:** CV Gate: `NOT_APPROVED` | CV Calibration: `NOT_CALIBRATED` | Human Verification: `requiresOfficerVerification=True`  

---

## 1. Executive Summary

In preparation for a final controlled live inference test, Phase 10A.6A performed a comprehensive offline preflight review of the retry-decoupling changes, attempt mapping contracts, HTTP 503/504 exception handling, and governance invariants in [`ai-service`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service).

### Key Audit Findings:
1. **Retry Decoupling Fully Verified:** Request-level configuration in [`GeminiProvider.analyze_evidence`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_provider.py#L398-L411) sets `gen_http_retry_opts = types.HttpRetryOptions(attempts=1)`, preventing the underlying `google-genai` tenacity loop from multiplying retries per call.
2. **Attempt Mapping Contract Confirmed:** `GEMINI_MAX_RETRIES` maps directly to **total HTTP attempts** ($M$ calls max). When `max_retries = 3`, at most 3 HTTP requests are sent (1 initial + 2 backoff retries). When `max_retries = 1` or `0`, exactly 1 HTTP request is sent (1 initial + 0 retries).
3. **HTTP 503 & 504 Exception Handling Verified:**
   - Both status codes are recognized as transient errors.
   - Exponential backoff is applied ($1.0\text{ s}, 2.0\text{ s}, \dots$) up to `max_attempts`.
   - On exhaustion, failures surface as `status = ANALYSIS_FAILED` with sanitized error descriptions (e.g. `ServerError (HTTP 504)`).
   - Non-retryable errors (401, 403, 400) abort immediately on attempt 1 without sleeping or retrying.
4. **Safety & Governance Invariants Unaltered:**
   - Computer Vision model approval remains `NOT_APPROVED`.
   - Statistical calibration status remains `NOT_CALIBRATED`.
   - Production inference remains unauthorized (`authorized = False`).
   - Human officer verification remains mandatory (`requiresOfficerVerification = True`).
   - Zero database or MinIO mutations occurred.
5. **100% Offline Regression Pass:** All 93 offline test cases passed with zero regressions.

---

## 2. Review of the Retry-Decoupling Code Change

In [`ai-service/app/models/gemini_provider.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_provider.py#L398-L411):

```python
# Convert timeout from seconds to milliseconds for google-genai SDK (e.g., 30s -> 30000ms)
timeout_ms = float(self.timeout_seconds * 1000)

# Explicit retry policy: Request-level attempts set to 1 to prevent accidental
# N x M multiplication between SDK tenacity retries and application-level retries.
# _call_gemini_with_retries serves as the single bounded retry controller.
gen_http_retry_opts = types.HttpRetryOptions(attempts=1) if (types and hasattr(types, "HttpRetryOptions")) else None
gen_config = types.GenerateContentConfig(
    system_instruction=GEMINI_SYSTEM_INSTRUCTION,
    response_mime_type="application/json",
    response_schema=GeminiRawResponse,
    temperature=0.1,
    http_options=types.HttpOptions(
        timeout=timeout_ms,
        retry_options=gen_http_retry_opts,
    ) if types else None,
)
```

### Architectural Effect:
- Inside the SDK, `_api_client._request` constructs `tenacity.Retrying` with `stop = tenacity.stop_after_attempt(1)`.
- Tenacity makes exactly 1 HTTP request per `self._client.models.generate_content(...)` invocation.
- If that single attempt fails, the SDK raises `APIError` / `ServerError` immediately to [`_call_gemini_with_retries`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_provider.py#L190-L234).
- `_call_gemini_with_retries` is now the **exclusive** retry loop, governing pacing, backoff sleep, and attempt limits.

---

## 3. Mapping of `GEMINI_MAX_RETRIES` to Total HTTP Attempts

In [`GeminiProvider._call_gemini_with_retries`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_provider.py#L195):
```python
max_attempts = max(1, self.max_retries)
for attempt in range(1, max_attempts + 1):
    ...
```

| Configured `GEMINI_MAX_RETRIES` | `max_attempts` Evaluated | Initial Attempt | Backoff Retries | Maximum Total HTTP Requests |
|:---:|:---:|:---:|:---:|:---:|
| `0` | `1` | 1 | 0 | **1** |
| `1` | `1` | 1 | 0 | **1** |
| `2` | `2` | 1 | 1 | **2** |
| `3` | `3` | 1 | 2 | **3** |
| `N` | `N` | 1 | $N - 1$ | **N** |

### Multiplier Elimination:
- **Prior Behavior:** Each application attempt could trigger up to $N$ SDK tenacity calls, resulting in up to $N \times N$ HTTP calls ($3 \times 3 = 9$).
- **Current Behavior:** SDK attempts per call $= 1$. Total HTTP calls $= 1 \times \text{max\_attempts} = \text{max\_attempts}$ ($1 \times 3 = 3$).
- **Conclusion:** Accidental multiplication is mathematically and architecturally eliminated.

---

## 4. Exception Handling Audit for HTTP 503 and HTTP 504

### 4.1 Transient Detection and Backoff
In [`_call_gemini_with_retries`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_provider.py#L206-L234):
- Non-retryable error filters:
  ```python
  is_permanent = any(
      sig in err_str
      for sig in [
          "401", "403", "unauthorized", "permissiondenied",
          "api_key_invalid", "400", "invalidargument",
      ]
  )
  ```
- HTTP 503 (`ServerError` / `UNAVAILABLE`) and HTTP 504 (`ServerError` / `DEADLINE_EXCEEDED` / `Gateway Timeout`) evaluate `is_permanent = False`.
- When `attempt < max_attempts`:
  - Computes `backoff = min(1.0 * (2 ** (attempt - 1)), 5.0)`.
  - Emits: `GeminiProvider: Transient error on attempt {attempt}/{max_attempts} ({type(exc).__name__}). Retrying in {backoff:.1f}s...`.
  - Executes: `time.sleep(backoff)`.
- When `attempt == max_attempts`:
  - Emits: `GeminiProvider: All {max_attempts} attempts exhausted ({type(exc).__name__}).`.
  - Raises `last_exception`.

### 4.2 Sanitized Status Preservation
In [`analyze_evidence`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_provider.py#L428-L442):
- Catches `call_err`.
- Extracts `status_code = getattr(call_err, "code", None)`.
- If `100 <= status_code <= 599`: formats `err_desc = f"{err_type} (HTTP {status_code})"`.
- Outputs clean, safe diagnostic:
  `Gemini API request failed: ServerError (HTTP 504)` or `Gemini API request failed: ServerError (HTTP 503)`.
- Never prints or logs credentials, authorization tokens, or raw request/response payloads.

---

## 5. Offline Mock Test Execution and Results

An isolated mock script was executed to verify all 5 preflight assertions:

```powershell
python scratch/test_preflight_checks.py
```

### Verified Scenarios:
1. **Persistent HTTP 504 with `max_retries=3`:**
   - Exactly 3 calls to `models.generate_content`.
   - Exactly 2 backoff sleeps ($1.0\text{ s}, 2.0\text{ s}$).
   - Returns `ANALYSIS_FAILED` with `error = "Gemini API request failed: ServerError (HTTP 504)"`.
   - **PASS**
2. **Persistent HTTP 504 with `max_retries=1`:**
   - Exactly 1 call to `models.generate_content`.
   - Exactly 0 backoff sleeps.
   - Returns `ANALYSIS_FAILED`.
   - **PASS**
3. **Persistent HTTP 504 with `max_retries=0`:**
   - Exactly 1 call to `models.generate_content`.
   - Exactly 0 backoff sleeps.
   - Returns `ANALYSIS_FAILED`.
   - **PASS**
4. **Transient HTTP 503 with recovery on attempt 2:**
   - Exactly 2 calls to `models.generate_content`.
   - Exactly 1 backoff sleep ($1.0\text{ s}$).
   - Returns `ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS`.
   - **PASS**
5. **Non-retryable HTTP 401 ClientError:**
   - Exactly 1 call to `models.generate_content`.
   - Exactly 0 backoff sleeps (aborts immediately).
   - Returns `ANALYSIS_FAILED` with `error = "Gemini API request failed: ClientError (HTTP 401)"`.
   - **PASS**

### Full Pytest Regression Suite:
```powershell
python -m pytest tests/test_gemini_foundation_phase1.py tests/test_gemini_vision_phase2.py tests/test_gemini_phase3_orchestration.py tests/test_error_handling.py -v
```
- `test_gemini_foundation_phase1.py`: **16 passed**
- `test_gemini_vision_phase2.py`: **20 passed**
- `test_gemini_phase3_orchestration.py`: **52 passed**
- `test_error_handling.py`: **5 passed**
- **Total:** **93 passed in 4.29s (100% Pass Rate; 0 Failures / Regressions)**

---

## 6. Safety Governance and Policy Audit

The governance state was re-verified against official policies:

| Policy Check | Expected State | Actual Inspected State | Status |
|---|---|---|:---:|
| **CV Model Approval Status** | `NOT_APPROVED` | `NOT_APPROVED` ([`model_approval_gate.is_production_approved()`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/model_approval_gate.py#L171-L193) is `False`) | **PASS** |
| **CV Calibration Status** | `NOT_CALIBRATED` | `NOT_CALIBRATED` | **PASS** |
| **CV Production Authorization** | `authorized = False` | `False` ([`model_approval_gate.check_inference_permission()`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/model_approval_gate.py#L195-L240)) | **PASS** |
| **Human Officer Verification** | Mandatory | `requiresOfficerVerification = True` | **PASS** |
| **Safe Inference Marker** | False on failure | `isSafeInference = False` | **PASS** |
| **Autonomous NOC Decisions** | Strictly Forbidden | Prohibited by schema validation | **PASS** |
| **Database / Object Storage Writes** | Zero | 0 records created | **PASS** |
| **Git Working Tree** | Clean / Untouched | 0 commits, 0 pushes | **PASS** |

---

## 7. Remaining Uncertainty & Recommended Test Parameters

### Remaining Uncertainty:
- **Upstream Google Service Health:** Non-generative metadata checks (`models.get`) continue to return 200 OK. However, whether Google's `gemini-3.8-flash` generation cluster has recovered from the previous 504 gateway timeout cannot be known without an authorized live request.

### Recommended Parameters for Next Controlled Test:
1. **Model:** `gemini-3.8-flash`.
2. **Timeout:** 30 seconds ($30{,}000\text{ ms}$).
3. **Retry Policy:** Authorize `gemini_max_retries = 2` (exactly 2 total HTTP attempts: 1 initial attempt + 1 transient backoff retry after 1.0s). Because retries are now strictly decoupled, this guarantees at most 2 HTTP requests will be dispatched to Google's API.
4. **Synthetic Image:** In-memory 640×640 JPEG with abstract geometric fire-safety shapes.
5. **Data Isolation:** Zero database or MinIO writes.

---

## 8. Final Verdict

### **FINAL VERDICT: READY_FOR_FINAL_CONTROLLED_TEST**

**Justification:**
1. Retry decoupling is verified and tested offline. Accidental $N \times M$ retry multiplication is impossible.
2. `GEMINI_MAX_RETRIES` maps directly to total HTTP attempts without ambiguity.
3. Exception handling for HTTP 503 and 504 correctly applies backoff, bounds attempts, and preserves sanitized status codes without secret leakage.
4. All 93 offline unit and integration tests pass cleanly.
5. CV gates remain locked to `NOT_APPROVED` and `NOT_CALIBRATED`.
6. Zero defects remain in the local codebase. The service is in a known, stable, and verified state.
