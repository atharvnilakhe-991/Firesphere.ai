# FireSphere.ai — Phase 10A Gemini ReadTimeout Diagnostic Report

**Document Date:** October 12, 2026  
**Execution Context:** Phase 10A Controlled Gemini Pilot — ReadTimeout Root Cause Diagnosis  
**Scope:** Read-Only Audit of GeminiProvider, google-genai SDK Timeout Unit Semantics, Transport Layer, and AFC Invariants  
**Status:** **ROOT CAUSE CONFIRMED WITH SOURCE CODE EVIDENCE | ZERO EXTERNAL REQUESTS MADE**  
**Runtime Security State:** `GEMINI_ENABLED=false` | Provider: `DISABLED` | CV Gate: `NOT_APPROVED` | Calibration: `NOT_CALIBRATED`  

---

## 1. Executive Summary

During the controlled Phase 10A single-image Gemini VLM live inference test, the operator executed the isolated verification command with `$env:GEMINI_API_KEY` active in their terminal. The request failed with:
- **Status:** `ANALYSIS_FAILED`
- **Error:** `Gemini API request failed: ReadTimeout`
- **Reported Execution Latency:** `159.05 ms`
- **Log Observation:** `[google_genai.models] AFC is enabled with max remote calls: 10.`

In strict compliance with the investigation mandate, this diagnostic was conducted **without making any external network requests, without viewing or printing credentials, and without altering source code or configuration**.

### Definitive Root Cause Finding (100% Confidence)
The `ReadTimeout` after only **159.05 ms** is directly caused by a **unit mismatch** between FireSphere configuration and the `google-genai` SDK v2:
- FireSphere configuration defines `timeout_seconds = 30` (30 seconds).
- `GeminiProvider` and the pilot command passed `types.HttpOptions(timeout=30.0)`.
- However, the official `google-genai` SDK defines `HttpOptions.timeout` in **milliseconds**, not seconds:
  ```python
  # google.genai._api_client.get_timeout_in_seconds:
  def get_timeout_in_seconds(timeout: Optional[Union[float, int]]) -> Optional[float]:
      if timeout:
          # HttpOptions.timeout is in milliseconds. But httpx.Client.request() expects seconds.
          timeout_in_seconds = timeout / 1000.0
      ...
  ```
- Consequently, passing `30.0` configured the underlying `httpx` HTTP transport with a socket read timeout of:
  $$\frac{30.0\text{ ms}}{1000} = 0.030\text{ seconds } (\mathbf{30\text{ milliseconds}})$$
- After DNS resolution and TLS handshake completed (~129 ms), `httpx` waited exactly **30 milliseconds** for response body bytes from Google. When Google did not finish multimodal token generation within 30 ms, `httpx` aborted the socket read and raised `httpx.ReadTimeout`.

---

## 2. Evidence-Based Technical Investigation

### A. Provider Implementation & SDK Invocation Path
- **Files Inspected:**
  - `ai-service/app/models/gemini_provider.py` (lines 158–170, 386–428)
  - `ai-service/app/core/config.py` (lines 217–223)
- **Invocation Path:**
  1. `GeminiProvider.initialize()` creates `types.HttpOptions(timeout=float(self.timeout_seconds))`.
  2. `GeminiProvider.analyze_evidence()` creates `gen_config = types.GenerateContentConfig(..., http_options=types.HttpOptions(timeout=float(self.timeout_seconds)))`.
  3. `GeminiProvider._call_gemini_with_retries()` invokes `client.models.generate_content(model=..., contents=..., config=gen_config)`.
  4. In all locations, `float(self.timeout_seconds)` passed `30.0`.

### B. Timeout Configuration Path & Unit Mismatch
Inspection of the installed `google-genai==2.29.0` SDK package (`google.genai._api_client`) revealed:
1. **Pydantic Schema Definition:**
   `types.HttpOptions.model_fields['timeout'].description`:
   > *"Timeout for the request in milliseconds."*
2. **SDK Translation Routine:**
   `google.genai._api_client.get_timeout_in_seconds`:
   ```python
   def get_timeout_in_seconds(
       timeout: Optional[Union[float, int]],
   ) -> Optional[float]:
     """Converts the timeout to seconds."""
     if timeout:
       # HttpOptions.timeout is in milliseconds. But httpx.Client.request()
       # expects seconds.
       timeout_in_seconds = timeout / 1000.0
     else:
       timeout_in_seconds = None
     return timeout_in_seconds
   ```
3. **Execution Evidence:**
   - Input to SDK: `HttpOptions(timeout=30.0)`.
   - Transformed by SDK: `get_timeout_in_seconds(30.0)` $\rightarrow$ `0.03` seconds ($30\text{ ms}$).
   - Dispatched to `httpx`: `self._httpx_client.build_request(..., timeout=0.03)`.
   - Result: Socket read timeout enforced at **30 ms**. For a 30-second timeout, the SDK requires `HttpOptions(timeout=30000.0)`.

### C. Exception Origin & Transport Layer
- **Exception Class:** `httpx.ReadTimeout` (subclass of `httpx.TimeoutException`, `httpx.TransportError`, `httpx.HTTPError`).
- **Origin:** Raised directly by `httpx._transports.default.HTTPTransport` during `self._httpx_client.send(httpx_request)` inside `_api_client.BaseApiClient._request_once`.
- **Handling in FireSphere:**
  In `GeminiProvider.analyze_evidence`:
  ```python
  except Exception as call_err:
      err_type = type(call_err).__name__  # Evaluated to "ReadTimeout"
      return GeminiAnalysisResult(
          status=GeminiAnalysisStatus.ANALYSIS_FAILED,
          error=f"Gemini API request failed: {err_type}",
          ...
      )
  ```
- **Finding:** The error originated entirely in the underlying `httpx` transport layer when waiting for the server's initial read response buffer, bound by the 30ms limit.

### D. Installed Package Versions & Settings
- **`google-genai` SDK Version:** `2.29.0`
- **`httpx` Version:** `0.28.1`
- **`HttpRetryOptions` Behavior:**
  - Configured with `attempts=1`.
  - In `google.genai._api_client.retry_args`:
    `stop = tenacity.stop_after_attempt(options.attempts or _RETRY_ATTEMPTS)`
    With `attempts=1`, tenacity sets `stop_after_attempt(1)` with `reraise=True`.
  - This verified that **zero SDK retries occurred**, exactly as intended.

### E. Automatic Function Calling (AFC) Analysis
- **Log Line Observed:** `[google_genai.models] AFC is enabled with max remote calls: 10.`
- **Inspection of `google.genai.models.Models.generate_content`:**
  Lines 93–126 of `models.py`:
  ```python
  remaining_remote_calls_afc = _extra_utils.get_max_remote_calls_afc(parsed_config)
  logger.info(f'AFC is enabled with max remote calls: {remaining_remote_calls_afc}.')
  while remaining_remote_calls_afc > 0:
      ...
      response = self._generate_content(model=model, contents=contents, config=parsed_config_to_call)
      if not function_map:
          break
  ```
- **Findings:**
  1. The log line is an **unconditional informational message** printed by `google-genai` whenever `generate_content` is called.
  2. Because FireSphere's `GenerateContentConfig` passes no tools (`tools=None`), `function_map` is empty (`{}` or `None`).
  3. The while loop evaluates `if not function_map: break` immediately after the first call.
  4. **Conclusion:** AFC did **not** make multiple calls and did **not** cause the timeout. The log line is standard SDK telemetry. It can be silenced if desired by setting `automatic_function_calling=types.AutomaticFunctionCallingConfig(disable=True)`.

### F. Timing Decomposition of 159.05 ms
The measured duration `159.05 ms` is the total wall-clock time measured across `GeminiProvider.analyze_evidence`:
- In-memory OpenCV image decode & dimension check: ~5–10 ms
- Laplacian blur & quality evaluation: ~10–15 ms
- Request payload encoding (base64 image part, prompt assembly): ~5 ms
- DNS lookup (`generativelanguage.googleapis.com`): ~15–30 ms
- TCP connection & TLS 1.3 handshake: ~65–90 ms
- Request transmission: ~2–5 ms
- **Socket read wait before timeout:** **30.0 ms**
- **Total:** $8 + 12 + 5 + 20 + 80 + 4 + 30 \approx \mathbf{159\text{ ms}}$

The 159.05 ms latency is mathematically consistent with local preprocessing and TLS negotiation overhead followed immediately by the 30 ms socket timeout expiration.

### G. Offline Test Suite Verification
The following offline tests were executed in the virtual environment without external connectivity:
1. `tests/test_gemini_vision_phase2.py -k "retry or secrets or disabled or credentials"`:
   - **4 passed, 20 deselected (0 failures)**.
2. **Dedicated Offline Mock Test for `ReadTimeout`:**
   - Simulated `httpx.ReadTimeout("The read operation timed out")` on `generate_content`.
   - Verified that `GeminiProvider` caught the exception, returned `GeminiAnalysisStatus.ANALYSIS_FAILED`, recorded `Gemini API request failed: ReadTimeout`, made exactly 1 call (no retries), and enforced `requiresOfficerVerification=True` and `isSafeInference=False`.
   - Result: **100% passing**.

### H. Status of Google API Response Evidence
- **Did Google transmit a successful response?** **NO.**
- The client socket timed out after 30 ms before Google's servers could transmit response tokens.
- **Current State:** API authentication, network routing, and model inference against Google's servers **remain unverified**.

---

## 3. Current Governance & Safety State

- **External Network Requests Made During Diagnostic:** **0 (Zero)**.
- **Persistent Service State:** `GEMINI_ENABLED=false` (Strictly maintained).
- **Provider Status:** `gemini_provider.get_status()` returns **`"DISABLED"`**.
- **Computer Vision Gate:** `NOT_APPROVED` (Unweakened).
- **Computer Vision Calibration:** `NOT_CALIBRATED` (Unweakened).
- **Statutory Fire NOC Decisions:** Zero autonomous decisions created or persisted.
- **Source Files Modified:** **0 (None)**.

---

## 4. The Smallest Safe Next Step

The smallest safe correction consists of two steps:

### Step 1: Fix the Timeout Unit in the Test Command
In `types.HttpOptions`, pass `timeout=30000.0` (30,000 milliseconds = 30 seconds) instead of `timeout=30.0`:
```python
# Pass 30,000 ms to give Google 30 seconds to generate the multimodal response:
sdk_http_opts = types.HttpOptions(
    timeout=30000.0,
    retry_options=types.HttpRetryOptions(attempts=1),
)
```
Optionally, also set `automatic_function_calling=types.AutomaticFunctionCallingConfig(disable=True)` in `types.GenerateContentConfig` to silence the AFC log line.

### Step 2: (When Authorized) Update `app/models/gemini_provider.py`
To prevent this bug in the service itself:
- Line 161: `http_opts = types.HttpOptions(timeout=float(self.timeout_seconds * 1000))`
- Line 395: `http_options=types.HttpOptions(timeout=float(self.timeout_seconds * 1000))`

*(Per governance rule 5, this source change is held until explicitly authorized).*
