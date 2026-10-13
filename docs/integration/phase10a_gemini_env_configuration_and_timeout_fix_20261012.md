# FireSphere.ai — Phase 10A Gemini .env Configuration and Timeout Fix Report

**Document Date:** October 12, 2026  
**Execution Context:** Phase 10A — Persistent Gemini VLM Local Configuration & Timeout Alignment  
**Scope:** Local `.env` Setup, CWD-Independent Configuration Loading, Millisecond Timeout Conversion, Offline Testing, and Governance Invariants  
**Configuration & Offline Test Status:** **PASS**  
**Live API Inference Status:** **NOT ATTEMPTED (Offline Task Scope Strictly Enforced)**  
**Runtime Governance State:** CV Model Gate: `NOT_APPROVED` | CV Calibration: `NOT_CALIBRATED` | Statutory Decisions: Human-Only (`requiresOfficerVerification=True`)  

---

## 1. Executive Summary

This report documents the completion of the persistent Gemini Vision-Language Model (VLM) configuration task for FireSphere.ai. The implementation:
1. Created an untracked, local `ai-service/.env` file configured with the required Gemini VLM settings.
2. Updated `app/core/config.py` to resolve `.env` relative to the AI service module root (`AI_SERVICE_DIR`), eliminating reliance on the process current working directory.
3. Implemented fail-closed credential validation: when `GEMINI_API_KEY` is absent or empty, the provider safely remains `DISABLED` without crashing the application on startup.
4. Resolved the SDK millisecond timeout mismatch across both client initialization and request generation (`timeout_ms = float(self.timeout_seconds * 1000)`), preserving the semantic meaning of `GEMINI_TIMEOUT_SECONDS=30` while delivering an effective 30.0-second socket timeout in `google-genai`.
5. Bounded SDK-level retry attempts via `types.HttpRetryOptions(attempts=max(1, self.max_retries))` to prevent retry multiplication.
6. Added 5 dedicated regression tests in `tests/test_gemini_foundation_phase1.py` covering timeout conversion, effective SDK duration, request-level option preservation, bounded retries, and credential fail-closed behavior.
7. Verified 86/86 passing tests across the entire Gemini test suite with 0 regressions.
8. Preserved all governance boundaries: Computer Vision model approval remains `NOT_APPROVED`, calibration remains `NOT_CALIBRATED`, and statutory fire-safety decisions remain strictly reserved for human fire officers.

---

## 2. Summary of Changed and Created Files

| File Path | Type | Summary of Changes |
|---|---|---|
| [`ai-service/app/core/config.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/core/config.py) | Modified | Added `AI_SERVICE_DIR` and `AI_SERVICE_ENV_FILE` for CWD-independent `.env` loading. Enhanced `validate_gemini_credentials` to fail closed gracefully when `GEMINI_API_KEY` is empty, while raising `ValueError` if explicitly passed without credentials in Python kwargs. Normalized empty/whitespace keys to `None`. |
| [`ai-service/app/models/gemini_provider.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_provider.py) | Modified | Fixed timeout unit mismatch at lines 161 (client initialization) and 395 (`analyze_evidence`). Converted `self.timeout_seconds * 1000` to milliseconds (`timeout_ms`). Added bounded `types.HttpRetryOptions(attempts=max(1, self.max_retries))` to both client and request `HttpOptions`. |
| [`ai-service/.env`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/.env) | Created | Untracked local configuration file with `GEMINI_ENABLED=true`, `GEMINI_API_KEY=`, `GEMINI_MODEL=gemini-2.5-flash`, `GEMINI_TIMEOUT_SECONDS=30`, `GEMINI_MAX_RETRIES=3`. |
| [`ai-service/.gitignore`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/.gitignore) | Created | Explicitly ignores `.env`, `.env.local`, `*.env`, Python caches, and virtual environments at the service level. |
| [`ai-service/.env.example`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/.env.example) | Verified | Preserved template placeholder configuration with zero secrets. |
| [`ai-service/tests/test_gemini_foundation_phase1.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/tests/test_gemini_foundation_phase1.py) | Modified | Added 5 new tests verifying timeout conversion, effective SDK duration, request-level options preservation, bounded retries, and missing credential fail-closed behavior. |

---

## 3. Configuration Loading & Credential Lifecycle Architecture

### 3.1 CWD-Independent Path Resolution
In `app/core/config.py`, the previous configuration used `env_file=".env"`, which resolved relative to the current working directory of whichever process launched Python. When running commands from the workspace root (`C:\Users\athar\OneDrive\Desktop\Firesphere.ai`), `ai-service/.env` was ignored.

**Resolution:**
```python
AI_SERVICE_DIR = Path(__file__).resolve().parent.parent.parent
AI_SERVICE_ENV_FILE = AI_SERVICE_DIR / ".env"

model_config = SettingsConfigDict(
    env_file=(str(AI_SERVICE_ENV_FILE), ".env"),
    env_file_encoding="utf-8",
    case_sensitive=False,
    populate_by_name=True,
    extra="ignore",
)
```
This guarantees that `ai-service/.env` is reliably loaded regardless of whether the execution command runs from workspace root, `ai-service`, or an external script directory.

### 3.2 Fail-Closed Credential Validation
When `ai-service/.env` specifies `GEMINI_ENABLED=true` but `GEMINI_API_KEY=` is empty (awaiting operator credential entry):
1. The previous behavior would raise a fatal `ValueError` on module import (`settings = Settings()`), crashing the FastAPI application, health checks, and test runner.
2. The corrected implementation enforces:
   - **Explicit code invocation:** If a caller explicitly passes `Settings(gemini_enabled=True, gemini_api_key=None)` in code kwargs, it raises a sanitized `ValueError` as expected by configuration safety tests.
   - **Environment loading:** When loaded from `.env` with an empty/missing key, `validate_gemini_credentials` logs a sanitized warning and safely sets `self.gemini_enabled = False` and `self.gemini_api_key = None`.
   - **Component status:** The provider reports `get_status() == "DISABLED"` and does not initialize any SDK client or make external network calls.
   - **Key presence:** As soon as the operator enters their valid API key into `ai-service/.env`, `gemini_enabled` resolves to `True`, and the provider status transitions to `CONFIGURED`.

---

## 4. Timeout Unit Mismatch Resolution

### 4.1 Root Cause & Impact
The `google-genai` SDK (`_api_client.py`) interprets `HttpOptions.timeout` in **milliseconds**:
```python
def get_timeout_in_seconds(timeout: Union[float, int, None]) -> Optional[float]:
    if timeout is not None:
        return timeout / 1000.0
    return None
```
When `self.timeout_seconds = 30` was passed directly as `HttpOptions(timeout=float(self.timeout_seconds))`, `get_timeout_in_seconds(30.0)` produced `0.030` seconds (**30 milliseconds**), causing premature `httpx.ReadTimeout` errors within 159 ms during live network calls.

### 4.2 Explicit Millisecond Conversion
In both `GeminiProvider.initialize()` and `GeminiProvider.analyze_evidence()`:
```python
timeout_ms = float(self.timeout_seconds * 1000)
http_retry_opts = types.HttpRetryOptions(attempts=max(1, self.max_retries)) if (types and hasattr(types, "HttpRetryOptions")) else None
http_opts = types.HttpOptions(
    timeout=timeout_ms,
    retry_options=http_retry_opts,
)
```

**Verification:**
$$\text{Configured Timeout} = 30\text{ seconds} \implies \text{SDK Timeout} = 30{,}000.0\text{ ms} \implies \text{Effective Socket Read Timeout} = \frac{30{,}000.0}{1{,}000.0} = 30.0\text{ seconds}$$

### 4.3 Request-Level Override Protection
In `analyze_evidence()`, `GenerateContentConfig` now sets `http_options=types.HttpOptions(timeout=timeout_ms, retry_options=http_retry_opts)`. When the SDK's `patch_http_options` merges request-level options into client-level options, both layers agree on `30000.0` ms, preventing request-level settings from accidentally overriding the client with raw seconds.

### 4.4 Bounded Retries
SDK retry options are explicitly bounded to `attempts=max(1, self.max_retries)` (default 3), preventing unbounded retries or uncontrolled exponential backoff loops.

---

## 5. Test Verification Matrix

All 86 tests passed with 0 failures:

| Test Suite | Total Tests | Status | Key Coverage |
|---|---|---|---|
| `tests/test_gemini_foundation_phase1.py` | 16 | **16/16 PASSED** | Timeout conversion to ms, effective 30.0s SDK timeout, request-level option preservation, bounded SDK retries, missing credential fail-closed, fail-closed defaults, zero autonomous decisions |
| `tests/test_gemini_vision_phase2.py` | 18 | **18/18 PASSED** | Multimodal image validation, bounding box normalization (0–1000 scale), transient retry handling, non-retryable error abort, secret redaction, CV gate protection |
| `tests/test_gemini_phase3_orchestration.py` | 52 | **52/52 PASSED** | Inspection pipeline orchestration, CV + VLM reconciliation, RBAC verification, tenant boundaries, finding ownership, human verification enforcement |
| **Total** | **86** | **86/86 PASSED** | **100% Pass Rate (0 Failures, 0 Regressions)** |

---

## 6. Security and Governance Verification

1. **Secret Leakage Prevention:**
   - `ai-service/.env` is ignored by Git at both workspace root (`.gitignore`) and service level (`ai-service/.gitignore`).
   - `GeminiProvider.get_provider_info()` strictly redacts API keys and tokens.
   - `ai-service/.env.example` contains only placeholder values (`GEMINI_API_KEY=`).
   - Zero keys or secrets were printed in terminal outputs, logs, or test reports.
2. **Computer Vision Governance Gate:**
   - `model_approval_gate.check_inference_permission(test_mode=False)` returns `authorized=False`, `modelApprovalStatus='NOT_APPROVED'`, `calibrationStatus='NOT_CALIBRATED'`.
   - CV model baseline adapter strictly fails closed.
3. **Statutory Fire NOC Authority:**
   - Finding schema validator unconditionally forbids `complianceStatus="AI_APPROVED"` or `"AI_REJECTED"`.
   - VLM analysis findings unconditionally enforce `requiresOfficerVerification=True` and `isSafeInference=False`.
4. **Storage & Database Isolation:**
   - Zero writes occurred to PostgreSQL, MinIO, or persistent audit tables.

---

## 7. Operational Instructions for the Operator

To enable live Gemini VLM inference for subsequent controlled pilot testing:

1. Open [`ai-service/.env`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/.env) in your editor.
2. Enter your Google Gemini API key on line 12:
   ```dotenv
   GEMINI_API_KEY=AIzaSyYourActualKeyHere
   ```
3. Save the file.
4. Because `ai-service/.env` is excluded by `.gitignore`, your key will remain strictly local and untracked.
5. With the key present, `Settings().gemini_enabled` will evaluate to `True`, and `GeminiProvider` will transition to status `CONFIGURED` / `READY`.

---

## 8. Remaining Blockers & Next Steps

* **Current Blocker:** Awaiting operator entry of the valid Gemini API key into `ai-service/.env`.
* **Recommended Next Step:** Once the key is saved in `ai-service/.env`, execute a controlled single-image pilot request using the corrected 30.0-second timeout configuration to verify live API transport and structured response parsing.
