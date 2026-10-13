# FireSphere.ai — Phase 10A Corrected Timeout Pre-Flight Verification Report

**Document Date:** October 12, 2026  
**Execution Context:** Phase 10A Controlled Gemini Pilot — Pre-Flight Timeout Architecture & Override Verification  
**Scope:** Offline Inspection of GeminiProvider, Client Injection, Request-Level Override Mechanics, and Effective Timeout Semantics  
**Verification Conclusion:** **PASS (Conditioned on Provider Instance Timeout Alignment)**  
**External API Requests Made:** **0 (Zero)**  
**Runtime Security State:** `GEMINI_ENABLED=false` | Provider: `DISABLED` | CV Gate: `NOT_APPROVED` | Calibration: `NOT_CALIBRATED`  

---

## 1. Executive Summary

Following the diagnosis of the `ReadTimeout` in `phase10a_timeout_diagnostic_20261012.md`, a critical architectural question was investigated:
> *If `types.HttpOptions(timeout=30000.0)` is passed to the injected `google.genai.Client`, is that 30-second timeout actually preserved for the live inference request, or does `GeminiProvider.analyze_evidence` override it?*

Through offline inspection of the installed `google-genai==2.29.0` SDK and `ai-service/app/models/gemini_provider.py`, a critical override mechanism was uncovered:

1. **Client Injection is Used:** `GeminiProvider` directly uses the injected SDK client (`self._client`) without constructing a secondary client.
2. **Provider-Level Request Override Discovered:** In `GeminiProvider.analyze_evidence` (line 395), a request-level `GenerateContentConfig` is constructed with:
   ```python
   http_options=types.HttpOptions(timeout=float(self.timeout_seconds))
   ```
   Because `self.timeout_seconds` is loaded from `settings.gemini_timeout_seconds` (which defaults to `30`), line 395 passes `types.HttpOptions(timeout=30.0)` at the request level.
3. **SDK Precedence Rules Override the Injected Client:** In `google.genai._api_client._build_request`, the SDK merges client options with request options via `patch_http_options(self._http_options, http_options)`. The request-level timeout (`30.0`) **takes precedence and overwrites** the client-level timeout (`30000.0`).
4. **Impact:** If only the injected client is initialized with `timeout=30000.0`, the actual request is overridden at line 395 back to `30.0` ms ($0.03\text{ s}$), which would trigger another `ReadTimeout` after 30 ms!
5. **The Verified Solution:** By explicitly aligning `prov.timeout_seconds = 30000` on the provider instance in the runner script (or multiplying by 1000 at line 395 when source edits are authorized), line 395 passes `timeout=30000.0`. `patch_http_options` merges `30000.0`, and `get_timeout_in_seconds(30000.0)` produces an **effective 30.0-second socket timeout**.
6. **Single-Attempt Limit Remains 100% Intact:** `retry_options=HttpRetryOptions(attempts=1)` from the client is preserved by `patch_http_options` because line 395 does not set `retry_options`. Both application retries (`prov.max_retries = 0`) and SDK retries (`tenacity.stop_after_attempt(1)`) remain strictly locked to 1 attempt.

---

## 2. Detailed Technical Determinations

### Determination 1: Does `GeminiProvider` use the injected SDK client or construct another client?
- **Finding:** **Uses the injected client directly.**
- **Code Evidence (`app/models/gemini_provider.py`):**
  - Line 87–88:
    ```python
    self._client: Optional[Any] = client
    self._initialized: bool = client is not None
    ```
  - Line 156–157 (`initialize`):
    ```python
    if self._client is not None:
        return self._client
    ```
  - Line 194 (`_call_gemini_with_retries`):
    ```python
    response = self._client.models.generate_content(...)
    ```
- **Conclusion:** When `client` is provided to `GeminiProvider.__init__`, it is stored in `self._client`, marked as initialized, and used directly for all model calls. It never instantiates a new client.

### Determination 2: Is the injected client's timeout preserved for the actual request?
- **Finding:** **NO (if set only on the client).**
- **Code Evidence:**
  - In `app/models/gemini_provider.py` (lines 390–396):
    ```python
    gen_config = types.GenerateContentConfig(
        system_instruction=GEMINI_SYSTEM_INSTRUCTION,
        response_mime_type="application/json",
        response_schema=GeminiRawResponse,
        temperature=0.1,
        http_options=types.HttpOptions(timeout=float(self.timeout_seconds)),
    )
    ```
  - In `google.genai._api_client._build_request` (lines 1367–1372):
    ```python
    if http_options:
        patched_http_options = patch_http_options(
            self._http_options,
            http_options,
        )
    ```
  - In `patch_http_options`: Request-level `http_options.timeout` (from `gen_config`) is non-None, so it **overwrites** `self._http_options.timeout`.
- **Conclusion:** The injected client's timeout is clobbered by the request-level `GenerateContentConfig.http_options` created on line 395 of `gemini_provider.py`.

### Determination 3: Does any provider-level timeout override the SDK setting?
- **Finding:** **YES.**
- **Code Evidence:** Line 395 reads `float(self.timeout_seconds)`. Because `self.timeout_seconds` is initialized from `Settings.gemini_timeout_seconds` (which defaults to `30`), line 395 creates `types.HttpOptions(timeout=30.0)`. This provider-level value overrides the client setting.
- **Pydantic Constraint Note:** In `app/core/config.py` line 219, `gemini_timeout_seconds` has validation constraint `le=300` (max 300 seconds). Passing `gemini_timeout_seconds=30000` to `Settings()` is rejected by Pydantic validation (`less_than_equal`). Therefore, the millisecond value must be set on `prov.timeout_seconds = 30000` on the provider instance, or converted at line 395 (`self.timeout_seconds * 1000`).

### Determination 4: Does `timeout=30000.0` result in an effective 30-second timeout?
- **Finding:**
  - If configured **only** on `genai.Client`: **NO** (overridden back to 30.0 ms by line 395).
  - If configured on **both** `genai.Client` and `prov.timeout_seconds = 30000`: **YES**.
- **Offline Intercept Verification:**
  ```python
  prov.timeout_seconds = 30000
  # Intercepted config.http_options.timeout: 30000.0
  # Intercepted patched_http_options.timeout: 30000.0
  # Effective seconds (timeout / 1000.0): 30.0 seconds
  ```
- **Conclusion:** When `prov.timeout_seconds = 30000`, line 395 generates `HttpOptions(timeout=30000.0)`. `get_timeout_in_seconds(30000.0)` produces exactly `30.0` seconds for `httpx`.

### Determination 5: Does the configured single-attempt limit remain effective?
- **Finding:** **YES (100% Verified).**
- **Code Evidence:**
  1. **Application Layer:** `max_attempts = max(1, self.max_retries)`. When `gemini_max_retries=0`, `max_attempts = 1`. The loop runs for `attempt = 1` only; any failure immediately raises without retrying.
  2. **SDK Layer:** In `patch_http_options`, line 395 sets only `timeout`, leaving `retry_options=None`. `patch_http_options` therefore preserves `client._http_options.retry_options` (`attempts=1`). Tenacity enforces `stop_after_attempt(1)` with `reraise=True`.
- **Offline Intercept Verification:**
  ```python
  assert patched_http_options.retry_options.attempts == 1
  assert prov.max_retries == 0
  ```
- **Conclusion:** Both layers remain strictly bound to at most 1 attempt.

---

## 3. Offline Verification Test Matrix

All tests were executed locally in the Python 3.13 virtual environment without external network connectivity:

| Test Case | Scope | Expected Outcome | Actual Outcome | Status |
|---|---|---|---|---|
| **Test 1: Client-Only Timeout** | Inject `Client(timeout=30000)` without changing `prov.timeout_seconds` | Request timeout overridden to 30ms ($0.03\text{ s}$) | Effective seconds: `0.03` | **CONFIRMED** |
| **Test 2: Provider Timeout Alignment** | Set `prov.timeout_seconds = 30000` | Effective seconds: `30.0` | Effective seconds: `30.0` | **CONFIRMED** |
| **Test 3: Retry Option Preservation** | Verify `patch_http_options` preserves `retry_options(attempts=1)` | Tenacity `attempts=1` preserved | `attempts: 1` preserved | **CONFIRMED** |
| **Test 4: Pydantic Settings Invariant** | Attempt `Settings(gemini_timeout_seconds=30000)` | Rejected by `le=300` | Raised `ValidationError` | **CONFIRMED** |
| **Test 5: Offline Generation Mock** | Full mock pass with `prov.timeout_seconds = 30000` | Response parsed without error | `ANALYSIS_INCONCLUSIVE` (clean) | **CONFIRMED** |

---

## 4. Current Governance & Safety State

- **External Network Requests Made:** **0 (Zero)**.
- **Service Configuration:** `GEMINI_ENABLED=false` strictly preserved.
- **Provider Status:** `gemini_provider.get_status()` returns **`"DISABLED"`**.
- **CV Model Gate:** `NOT_APPROVED` (Unweakened).
- **CV Calibration Gate:** `NOT_CALIBRATED` (Unweakened).
- **Autonomous Fire NOC Decisions:** Zero decisions created or persisted.
- **Source Files Modified:** **0 (None)**.

---

## 5. Verification Conclusion & Smallest Safe Next Step

### Conclusion: **PASS** (with `prov.timeout_seconds = 30000` instance alignment)

The exact execution command must include `prov.timeout_seconds = 30000` so that `GeminiProvider.analyze_evidence` line 395 does not overwrite the 30-second timeout.

### Corrected PowerShell Command:
```powershell
$cmd = @'
import os, sys, uuid, time, cv2, numpy as np
sys.path.insert(0, r'C:\Users\athar\OneDrive\Desktop\Firesphere.ai\ai-service')
from google import genai
from google.genai import types
from app.core.config import Settings
from app.models.gemini_provider import GeminiProvider
from app.models.model_approval_gate import model_approval_gate

key = os.environ.get('GEMINI_API_KEY', '').strip()
assert key, 'FAIL: GEMINI_API_KEY is not set or empty in this terminal session.'

cv_gate = model_approval_gate.check_inference_permission(test_mode=False)
assert cv_gate['modelApprovalStatus'] == 'NOT_APPROVED'
assert cv_gate['calibrationStatus'] == 'NOT_CALIBRATED'
assert cv_gate['authorized'] == False

cfg = Settings(gemini_enabled=True, gemini_api_key=key, gemini_model='gemini-2.5-flash', gemini_max_retries=0, gemini_timeout_seconds=30)
sdk_http_opts = types.HttpOptions(timeout=30000.0, retry_options=types.HttpRetryOptions(attempts=1))
client = genai.Client(api_key=key, http_options=sdk_http_opts)
prov = GeminiProvider(settings_obj=cfg, client=client)

# CRITICAL FIX: Align provider instance timeout with SDK millisecond semantics
# (Prevents line 395 from overwriting the client timeout back to 30 milliseconds):
prov.timeout_seconds = 30000

img = np.ones((640, 640, 3), dtype=np.uint8) * 180
cv2.rectangle(img, (100, 100), (250, 400), (0, 0, 200), -1)
cv2.rectangle(img, (350, 100), (550, 300), (0, 200, 0), -1)
cv2.putText(img, 'FIRESPHERE TEST', (30, 450), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 255), 2)
_, enc = cv2.imencode('.jpg', img)
raw_bytes = enc.tobytes()
eid = str(uuid.uuid4())

print('=' * 70)
print('FIRESPHERE.AI — CONTROLLED SINGLE-IMAGE GEMINI VLM LIVE TEST')
print('=' * 70)
print('Provider Model       :', prov.get_model_id())
print('App Max Retries      :', prov.max_retries, '(strictly 1 attempt)')
print('Effective Timeout    : 30.0 seconds (30000 ms)')
print('SDK Retry Attempts   :', sdk_http_opts.retry_options.attempts, '(tenacity stop_after_attempt=1)')
print('CV Gate Status       :', cv_gate['modelApprovalStatus'], '| Calibration:', cv_gate['calibrationStatus'])
print('Synthetic Evidence ID:', eid)
print('Dispatching single live inference request to Google Gemini API...')

t0 = time.perf_counter()
res = prov.analyze_evidence(eid, image_data=raw_bytes)
dt = round((time.perf_counter() - t0) * 1000, 2)

del key; del client; del cfg; del prov
post_prov = GeminiProvider()
assert post_prov.get_status() == 'DISABLED'

print('-' * 70)
print('EXECUTION RESULTS:')
print('Status               :', res.status.value)
print('Execution Latency    :', f'{dt} ms')
print('Observation Count    :', len(res.observations))
print('Officer Review Req   :', res.requiresOfficerVerification)
print('Premises Safe Cert   :', res.isSafeInference)
print('Error Diagnostic     :', res.error or 'None')
if res.observations:
    print('Sample Observation   :', res.observations[0].objectCategory, '| Severity:', res.observations[0].severityProposal)
    print('Visual Description   :', res.observations[0].observationDescription)
    print('Verification Guidance:', res.observations[0].verificationGuidance)
if res.rawResponse and 'usageMetadata' in res.rawResponse:
    print('Token Usage Metadata :', res.rawResponse['usageMetadata'])
print('Post-Test State      : GEMINI_ENABLED=false verified | Provider=DISABLED')
print('=' * 70)
'@
& "C:\Users\athar\OneDrive\Desktop\Firesphere.ai\ai-service\.venv\Scripts\python.exe" -c $cmd
```
