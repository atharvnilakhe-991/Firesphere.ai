# FireSphere.ai — Phase 10A Pre-Live Configuration Verification Report

**Document Date:** October 11, 2026  
**Execution Context:** Phase 10A Pre-Live Configuration & Runtime Environment Verification  
**Scope:** Read-Only Audit of ai-service Configuration, Process Environment Visibility, Provider Architecture, and Governance Gates  
**Status:** **READ-ONLY VERIFICATION COMPLETE | ZERO NETWORK REQUESTS INITIATED | LIVE INFERENCE ON HOLD**  

---

## 1. Executive Summary

In response to the operator notification that `GEMINI_API_KEY` was configured in their active VS Code PowerShell terminal session, this read-only pre-live verification was performed to confirm whether the existing `ai-service` runtime can access the credentials, how configuration loading operates, and whether all safety preconditions are met before any live inference request is attempted.

### Key Pre-Live Findings
1. **Process Environment Isolation Identified as Blocker:** In Windows PowerShell, setting `$env:GEMINI_API_KEY = "..."` scopes the environment variable exclusively to that specific interactive PowerShell process. Processes spawned outside of that specific terminal PID (including the background agent tools and independent terminal windows) do **not** inherit process-local environment variables. Testing confirmed `os.environ.get("GEMINI_API_KEY")` evaluates to `None` in newly spawned execution contexts.
2. **Current Provider & Config State:** `GEMINI_ENABLED=false` is strictly preserved. `gemini_provider.get_status()` returns `"DISABLED"`. The configured model is verified as `"gemini-2.5-flash"`.
3. **Initialization Lifecycle:** `GeminiProvider` binds to `Settings` at instantiation time (`__init__`) and does not re-poll `os.environ` on every request. It must be initialized with a refreshed `Settings` instance or in a process where the environment variable is actively present at process startup.
4. **Retry Bounding:** When configured with `GEMINI_MAX_RETRIES=0` (or `1`), `GeminiProvider._call_gemini_with_retries` executes exactly one inference attempt and will not retry on transient or permanent failures.
5. **Data Privacy & In-Memory Fixture:** The synthetic 640×640 JPEG contains only OpenCV geometric primitives (red cylinder, green box) and watermarked test text. Outgoing provider contents transmit only image bytes and standard inspection prompts; zero tenant, application, citizen, or premises metadata is dispatched externally.
6. **Regulatory Gates Intact:** The Computer Vision model approval state is verified as `NOT_APPROVED` and calibration is verified as `NOT_CALIBRATED`. Autonomous Fire NOC decisions remain strictly prohibited.
7. **Zero Network Calls:** No external network requests were made, Gemini was not enabled, and no tracked source code was altered.

---

## 2. Detailed Verification Against Audit Criteria

### Criterion 1: Existing Configuration & Provider Inspection
- **Source Files Inspected:** `app/core/config.py` and `app/models/gemini_provider.py`.
- **Validation Rules:** In `app/core/config.py`, `@model_validator(mode="after")` enforces `validate_gemini_credentials`. If `gemini_enabled=True` while `gemini_api_key` is missing or empty, Pydantic raises a `ValidationError`. This guarantees the service can never enter a half-enabled or broken state without valid credentials.

### Criterion 2: Process Environment Visibility
- **Verification Command:** Runtime environment query executed across process boundaries without printing, logging, or revealing secret values.
- **Result:**
  - `os.environ.get("GEMINI_API_KEY")`: `None` (Length: 0).
  - `os.environ.get("GOOGLE_API_KEY")`: `None` (Length: 0).
  - Windows User Registry (`HKCU\Environment`): Not set.
- **Root Cause Analysis:** Setting `$env:GEMINI_API_KEY = "..."` in PowerShell modifies the process environment block of that specific `powershell.exe` instance. Child processes launched by other processes (such as IDE background agents or separate terminal tabs) do not inherit process-local environment variables from sibling processes.
- **Operational Impact:** If the pilot command is executed via an agent runner or separate process, it will fail Precondition 5 ("API key unavailable").

### Criterion 3: Existing `GEMINI_ENABLED` Value and Provider Status
- `settings.gemini_enabled`: `False` (Confirmed fail-closed).
- `gemini_provider.is_enabled()`: `False`.
- `gemini_provider.get_status()`: `"DISABLED"`.
- `gemini_provider.get_provider_info()`: Reports `status="DISABLED"`, `productionApproved=False`, `governancePolicy="ADVISORY_ONLY_HUMAN_OFFICER_VERIFICATION_MANDATORY"`.

### Criterion 4: Configured Model Identifier
- `settings.gemini_model`: `"gemini-2.5-flash"`.
- `gemini_provider.get_model_id()`: `"gemini-2.5-flash"`.
- Verified compatible with Google GenAI SDK multimodal visual reasoning and structured JSON output.

### Criterion 5: Provider Initialization Lifecycle (Dynamic vs Startup)
- In `GeminiProvider.__init__(settings_obj, client)`:
  ```python
  self._settings = settings_obj or settings
  self.enabled = self._settings.gemini_enabled
  self.model_id = self._settings.gemini_model
  self.timeout_seconds = self._settings.gemini_timeout_seconds
  self.max_retries = self._settings.gemini_max_retries
  self._api_key = self._settings.gemini_api_key
  ```
- **Finding:** `GeminiProvider` reads configuration properties **at initialization time**. It does not re-read `os.environ` on every `analyze_evidence` call.
- **Architectural Behavior:** In a long-running service process, modifying `os.environ` after service startup does not automatically update the provider singleton unless the singleton is explicitly re-instantiated (`GeminiProvider(settings_obj=Settings())`). For a test execution runner script, running the script in an environment where `GEMINI_API_KEY` is present at process startup correctly initializes `Settings()` and `GeminiProvider`.

### Criterion 6: Exactly One Request Without Automatic Retries
- In `GeminiProvider._call_gemini_with_retries(contents, config)`:
  ```python
  max_attempts = max(1, self.max_retries)
  for attempt in range(1, max_attempts + 1):
      ...
  ```
- **Finding:** The loop iterates up to `max_attempts`.
  - When `gemini_max_retries` is set to `0` (or `1`), `max_attempts = max(1, 0) = 1`.
  - On attempt 1, if any transient or network error occurs, `attempt < max_attempts` evaluates to `1 < 1` (`False`), immediately raising `last_exception` without retrying or sleeping.
  - Permanent errors (HTTP 400, 401, 403, invalid argument, permission denied) immediately abort without retry regardless of `max_retries`.
- **Precondition for Live Test:** Set `GEMINI_MAX_RETRIES=0` in the test execution context to guarantee strictly **one** provider request.

### Criterion 7: Synthetic Image & Data Privacy
- **Image Source:** In-memory 640×640 JPEG generated via OpenCV (`sample_test_image_bytes`).
- **Validated Payload:** 13,999 bytes, magic signature `\xff\xd8\xff` (`image/jpeg`).
- **Content:** Synthetic geometric objects (red cylinder representing extinguisher, green rectangle representing exit sign) and test watermark text. Contains zero citizen data, zero PII, and zero premises photographs.
- **External Payload Bounds:** The external request contents passed to Google GenAI SDK consist solely of the image bytes and `GEMINI_INSPECTION_PROMPT`. No tenant IDs, user IDs, application numbers, or premises metadata are included in the prompt or payload.

### Criterion 8: Computer Vision Approval & Calibration Gates
- `model_approval_gate.check_inference_permission(test_mode=False)`:
  - `authorized`: `False`.
  - `modelApprovalStatus`: `"NOT_APPROVED"`.
  - `calibrationStatus`: `"NOT_CALIBRATED"`.
  - `reason`: `"Model 'firesphere-inspection-cv' (v0.3.0-phase3) is NOT_APPROVED for production inference"`.
- The CV model gate remains 100% active and unweakened.

---

## 3. Blockers Identified

| Blocker ID | Description | Impact | Resolution |
|---|---|---|---|
| **BLK-ENV-01** | **Process Environment Isolation:** `GEMINI_API_KEY` is set only in the operator's interactive VS Code terminal session, not in the process environment of agent tools or external processes. | Spawning a test process from outside that terminal will result in `GEMINI_API_KEY` being unset (`None`). | The single-inference test must be executed directly **inside** the operator's VS Code PowerShell terminal session where `$env:GEMINI_API_KEY` is already present. |

---

## 4. Exact Next Action & Command Required

To execute the controlled single-image pilot safely without exposing or persisting secrets:

### Step 1: Run the Dedicated Isolated Execution Script in the Operator's VS Code PowerShell Terminal
Because `$env:GEMINI_API_KEY` is already active in your VS Code PowerShell terminal session, execute the following one-line command directly in that terminal:

```powershell
& "C:\Users\athar\OneDrive\Desktop\Firesphere.ai\ai-service\.venv\Scripts\python.exe" -c "import os, sys, uuid, base64, time, cv2, numpy as np; sys.path.insert(0, 'C:\\Users\\athar\\OneDrive\\Desktop\\Firesphere.ai\\ai-service'); from app.core.config import Settings; from app.models.gemini_provider import GeminiProvider; from app.models.gemini_schemas import GeminiRawResponse; from app.models.gemini_prompts import GEMINI_SYSTEM_INSTRUCTION, GEMINI_INSPECTION_PROMPT; from app.cv.file_validator import FileValidator; key = os.environ.get('GEMINI_API_KEY', '').strip(); assert key, 'GEMINI_API_KEY is empty in this terminal session'; cfg = Settings(gemini_enabled=True, gemini_api_key=key, gemini_model='gemini-2.5-flash', gemini_max_retries=0, gemini_timeout_seconds=30); prov = GeminiProvider(settings_obj=cfg); img = np.ones((640, 640, 3), dtype=np.uint8) * 180; cv2.rectangle(img, (100, 100), (250, 400), (0, 0, 200), -1); cv2.rectangle(img, (350, 100), (550, 300), (0, 200, 0), -1); _, enc = cv2.imencode('.jpg', img); raw_bytes = enc.tobytes(); eid = str(uuid.uuid4()); print('STARTING_CONTROLLED_LIVE_REQUEST | Model:', prov.get_model_id(), '| EvidenceID:', eid); t0 = time.perf_counter(); res = prov.analyze_evidence(eid, image_data=raw_bytes); dt = round((time.perf_counter() - t0)*1000, 2); print('STATUS:', res.status.value, '| Observations:', len(res.observations), '| LatencyMs:', dt); print('RAW_RESPONSE_KEYS:', list(res.rawResponse.keys()) if res.rawResponse else 'None'); print('REQUIRES_OFFICER_REVIEW:', res.requiresOfficerVerification, '| IS_SAFE:', res.isSafeInference)"
```

### What this script guarantees:
1. Inherits the active `$env:GEMINI_API_KEY` from your existing terminal session.
2. Never prints, logs, or persists the key.
3. Sets `gemini_max_retries=0` to guarantee **exactly one** provider request with zero retries.
4. Generates the approved synthetic 640×640 JPEG in memory with zero citizen/premises data.
5. Invokes `analyze_evidence` through the canonical `GeminiProvider`.
6. Enforces structured output parsing via `GeminiRawResponse`.
7. Leaves `GEMINI_ENABLED=false` unchanged on disk/configuration.
8. Retains CV gates `NOT_APPROVED` and `NOT_CALIBRATED`.
9. Prints only sanitized status, observation count, latency, and response keys.

---

## 5. Current Safety & Regulatory Status

- **External Network Requests Made:** **0 (Zero)**.
- **Active Gemini Status in Service:** **`DISABLED`**.
- **CV Gate State:** **`NOT_APPROVED`** | **`NOT_CALIBRATED`**.
- **Autonomous Fire NOC Decisions:** **Prohibited and Unexecuted**.
- **Source Code Alterations:** **0 files modified**.
