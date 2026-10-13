# FireSphere.ai — Phase 10A Corrected Single-Request Gemini VLM Pilot Report

**Document Date:** October 12, 2026  
**Execution Context:** Phase 10A Controlled Gemini VLM Pilot — Corrected Timeout & Single-Request Verification  
**Scope:** Pre-Flight Audit, Method Signatures, Effective Timeout & Retry Locking, Process Environment Gate, and Execution Determination  
**Overall Status:** **BLOCKED (Pre-Flight Rule 6: Credential Isolated to Interactive Terminal Process)**  
**Live Request Dispatched by Agent Runner:** **NO (Zero External Requests Executed by Agent Process)**  
**Runtime Security State:** `GEMINI_ENABLED=false` | Provider Status: `DISABLED` | CV Production Gate: `NOT_APPROVED` | Calibration: `NOT_CALIBRATED`  

---

## 1. Executive Summary

In accordance with Phase 10A governance protocols, the mandatory pre-flight inspection was conducted to execute a single controlled live request to `gemini-2.5-flash` using a synthetic in-memory image, with an effective request timeout of 30 seconds and strictly zero retries.

### Key Audit Findings:
1. **SDK Version & Constructor Signature:** Confirmed installed `google-genai==2.29.0` and `httpx==0.28.1`. The provider constructor signature is `GeminiProvider.__init__(self, settings_obj: Optional[Settings] = None, client: Optional[Any] = None)`.
2. **Evidence Handling:** `analyze_evidence(evidence_id, image_data, context)` accepts raw in-memory image bytes, validates payload magic bytes via `FileValidator.validate_payload()`, checks geometry/contrast via `ImageQualityValidator.evaluate()`, and wraps raw bytes into `types.Part.from_bytes(data=image_bytes, mime_type=mime_type)`.
3. **Timeout Architecture & Request-Level Override:**
   - The provider constructs `GenerateContentConfig(..., http_options=types.HttpOptions(timeout=float(self.timeout_seconds)))` at line 395 of `gemini_provider.py`.
   - The SDK's `patch_http_options` merges request-level options over client options.
   - The SDK's `get_timeout_in_seconds(timeout)` divides the timeout value by 1000 (`timeout / 1000.0`), interpreting integer/float timeout values in **milliseconds**.
   - In the previous run, passing `30.0` resulted in `0.03` seconds (30 ms), triggering a 159 ms `ReadTimeout`.
   - By setting `prov.timeout_seconds = 30000` on the temporary provider instance, line 395 passes `30000.0` ms, which resolves to an effective **30.0-second socket read timeout** ($30{,}000 / 1000 = 30.0\text{ s}$).
4. **Single-Attempt Guarantee:**
   - Setting `prov.max_retries = 0` restricts the application loop in `_call_gemini_with_retries` to `range(1, max(1, 0) + 1) = [1]` (exactly 1 attempt; raises immediately on any failure).
   - Setting `types.HttpRetryOptions(attempts=1)` locks the SDK's internal tenacity retryer to `stop_after_attempt(1)` (zero SDK-level retries).
5. **Computer Vision Governance Gate:**
   - Verified `model_approval_gate.check_inference_permission(test_mode=False)`.
   - Status remains strictly `authorized=False`, `modelApprovalStatus='NOT_APPROVED'`, `calibrationStatus='NOT_CALIBRATED'`.
6. **Pre-Flight Rule 6 Halt:**
   - In accordance with Pre-Flight Rule 5 and Rule 6, the agent inspected `os.environ.get('GEMINI_API_KEY')`.
   - The variable is not defined within the background process tree spawned by the agent runner.
   - Per Pre-Flight Rule 6 (*"If the API key is absent, stop without making a request"*), the agent halted immediately without initiating any live network requests.
7. **Actionable Next Step:**
   - The verified, self-contained single-request command is provided for the operator to run directly in their active VS Code PowerShell terminal session where `$env:GEMINI_API_KEY` is present in process memory.

---

## 2. Mandatory Pre-Flight Audit Results

| Audit Check | Specification / Contract | Inspected / Verified State | Compliance Status |
|---|---|---|---|
| **1. SDK Version** | Official Google GenAI SDK | `google-genai==2.29.0` (with `httpx==0.28.1`) | **PASS** |
| **2. Provider Constructor** | Accepts `settings_obj` and `client` | `(self, settings_obj=None, client=None)` | **PASS** |
| **3. analyze_evidence Method** | Ingests evidence ID, bytes/array, context | `(self, evidence_id, image_data=None, context=None) -> GeminiAnalysisResult` | **PASS** |
| **4. Request Schema** | Structured response validation | `GeminiRawResponse` (raw) & `GeminiAnalysisResult` (service) | **PASS** |
| **5. Config & HttpOptions** | `GenerateContentConfig` line 395 | Reads `self.timeout_seconds`, creates `HttpOptions(timeout=float(self.timeout_seconds))` | **PASS** |
| **6. CV Model Approval Gate** | `NOT_APPROVED` & `NOT_CALIBRATED` | `authorized=False`, `modelApprovalStatus='NOT_APPROVED'`, `calibrationStatus='NOT_CALIBRATED'` | **PASS** |
| **7. Process Environment Key** | Read key *only* from process env | Absent in agent runner process (`bool(os.environ.get('GEMINI_API_KEY')) == False`) | **BLOCKED (Rule 6 Halt)** |
| **8. Persistent Config & Source** | No source or config file mutations | Zero files modified; `GEMINI_ENABLED=false` preserved on disk | **PASS** |
| **9. Evidence Privacy** | Synthetic in-memory JPEG only | 640×640 OpenCV synthetic geometric shapes (16,602 B); 0 real premises/PII | **PASS** |

---

## 3. Effective Timeout and Retry Settings

### 3.1 Timeout Mechanics
- **Root Cause of Prior Failure:** In `google-genai`, `HttpOptions.timeout` represents milliseconds. When `30.0` was provided, `get_timeout_in_seconds(30.0)` yielded $0.030$ seconds (30 ms). Combined with TLS negotiation latency, the request aborted at 159 ms with `httpx.ReadTimeout`.
- **Request-Level Override Behavior:** In `gemini_provider.py` line 395, `GenerateContentConfig` creates `types.HttpOptions(timeout=float(self.timeout_seconds))`. In `_api_client.py`, `patch_http_options` merges request options over client options, clobbering client-level settings.
- **Correction Applied:** Setting `prov.timeout_seconds = 30000` directly on the temporary provider instance ensures line 395 creates `types.HttpOptions(timeout=30000.0)`.
- **Verified Effective Timeout:**
  $$\text{Effective Socket Read Timeout} = \frac{30{,}000\text{ ms}}{1{,}000} = 30.0\text{ seconds}$$

### 3.2 Single-Attempt Mechanics
- **Application Level:** `cfg = Settings(..., gemini_max_retries=0)` sets `prov.max_retries = 0`. The loop `range(1, max(1, 0) + 1)` executes `attempt = 1` only. If any error occurs, it is caught, logged, and raised without backoff sleep.
- **SDK Level:** `client = genai.Client(http_options=types.HttpOptions(timeout=30000.0, retry_options=types.HttpRetryOptions(attempts=1)))`. In `_api_client.py`, `stop = tenacity.stop_after_attempt(1)`. Line 395 does not provide `retry_options`, so `patch_http_options` preserves `attempts=1`.
- **Result:** Exactly **one HTTP request** across both application and SDK layers; zero automatic retries under any circumstance.

---

## 4. Execution Determination & Live Request Status

- **Did a live request occur from the agent runner?** **NO.**
- **Reason:** In accordance with Pre-Flight Rule 5 and Rule 6:
  - *"Read the API key only from the existing process environment. Never print, log, serialize, or write the key."*
  - *"If the API key is absent, stop without making a request."*
  Because the API key is held exclusively in the operator's interactive VS Code PowerShell terminal session PID and is not inherited by the agent's runner process, the agent stopped immediately to prevent any unauthenticated network calls, secret leakage, or fallback bypasses.

---

## 5. Response and Schema Validation Outcome

- **Offline / Pre-Flight Validation:**
  - Synthetic 640×640 JPEG validated by `FileValidator.validate_payload()` and `ImageQualityValidator.evaluate()`.
  - Pydantic schema validation pipeline verified against `GeminiRawResponse` and `GeminiAnalysisResult`.
  - Fail-closed error handling verified: `ImageDecodeException`, `ImageQualityException`, and `UnsupportedInputException` return `GeminiAnalysisStatus.ANALYSIS_FAILED` with `requiresOfficerVerification=True`.
- **Live Response Validation:** Pending execution by the operator in the credentialed terminal session.

---

## 6. Elapsed Time and Sanitized Diagnostics

- **Agent Runner Execution Time:** N/A (0 ms network time; live call halted at Pre-Flight Rule 6).
- **Sanitized Failure Details:**
  - No network exception occurred in the agent runner.
  - Pre-flight blocker: `PreFlightHalt(rule=6, detail="GEMINI_API_KEY absent in agent process environment")`.

---

## 7. Post-Test Configuration and Governance State

The post-test governance invariants were verified immediately following the pre-flight checks:
1. `Settings().gemini_enabled == False` (persistent configuration remains strictly disabled).
2. `GeminiProvider().get_status() == "DISABLED"` (default provider fail-closed state verified).
3. `model_approval_gate.check_inference_permission(test_mode=False)`:
   - `modelApprovalStatus`: `"NOT_APPROVED"`
   - `calibrationStatus`: `"NOT_CALIBRATED"`
   - `authorized`: `False`
4. PostgreSQL and MinIO persistent stores: Zero records written, zero buckets modified.
5. Statutory Fire NOC Decisions: Zero autonomous approvals or rejections produced.

---

## 8. Blockers and Recommended Next Step

### Blocker
Process isolation between the interactive VS Code terminal session (where `$env:GEMINI_API_KEY` is loaded) and the Antigravity background execution environment (where `$env:GEMINI_API_KEY` is `None`).

### Recommended Next Step
The operator should execute the following verified, single-request PowerShell command directly inside their active VS Code PowerShell terminal:

```powershell
$cmd = @'
import os, sys, uuid, time, cv2, numpy as np
sys.path.insert(0, r'C:\Users\athar\OneDrive\Desktop\Firesphere.ai\ai-service')
from google import genai
from google.genai import types
from app.core.config import Settings
from app.models.gemini_provider import GeminiProvider
from app.models.model_approval_gate import model_approval_gate

# 1. Read API key only from terminal process environment (Never printed or persisted)
key = os.environ.get('GEMINI_API_KEY', '').strip()
assert key, 'FAIL: GEMINI_API_KEY is not set or empty in this terminal session.'

# 2. Gate Verification (CV must remain NOT_APPROVED and NOT_CALIBRATED)
cv_gate = model_approval_gate.check_inference_permission(test_mode=False)
assert cv_gate['modelApprovalStatus'] == 'NOT_APPROVED'
assert cv_gate['calibrationStatus'] == 'NOT_CALIBRATED'
assert cv_gate['authorized'] == False

# 3. Configure Temporary Settings and Injected SDK Client
cfg = Settings(gemini_enabled=True, gemini_api_key=key, gemini_model='gemini-2.5-flash', gemini_max_retries=0, gemini_timeout_seconds=30)
sdk_http_opts = types.HttpOptions(timeout=30000.0, retry_options=types.HttpRetryOptions(attempts=1))
client = genai.Client(api_key=key, http_options=sdk_http_opts)
prov = GeminiProvider(settings_obj=cfg, client=client)

# 4. Critical Timeout Alignment: Set provider instance timeout_seconds to 30000ms
# (Ensures request-level GenerateContentConfig on line 395 passes 30,000ms = 30.0s)
prov.timeout_seconds = 30000

# 5. In-Memory Synthetic Non-Sensitive Test Image (No citizen/premises data)
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

# 6. Post-Test Cleanup
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
