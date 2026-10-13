# FireSphere.ai — Gemini VLM Integration: Phase 1 Foundation

## 1. Overview & Objectives

Phase 1 establishes a safe, maintainable, and backward-compatible foundation for integrating Google Gemini Vision-Language Models (VLM) into the existing FireSphere.ai AI service.

In accordance with strict architectural governance:
- **Phase 1 Scope**: Configuration management, official Google GenAI SDK integration, provider abstraction, health check readiness integration, unit testing, and interface contracts for future visual analysis.
- **Strict Boundary**: Phase 1 **DOES NOT** perform live vision inference, prompt engineering, hazard detection, conflict resolution, or autonomous Fire NOC decisions.
- **Fail-Closed Governance**: Gemini is disabled by default (`GEMINI_ENABLED=false`). The service starts cleanly without remote credentials or internet connectivity.
- **CV Model Gate Preserved**: The existing reference CV model (Phase 8.4) remains `NOT_APPROVED` and fail-closed. Gemini integration does not bypass or alter any existing safety gates.

---

## 2. Architecture & Components

```
                      +---------------------------------------+
                      |       FireSphere Configuration        |
                      |   (GEMINI_ENABLED, GEMINI_API_KEY,    |
                      |    GEMINI_MODEL, TIMEOUTS, RETRIES)   |
                      +-------------------+-------------------+
                                          |
                                          v
                      +---------------------------------------+
                      |         GeminiProvider Adapter        |
                      |    (app/models/gemini_provider.py)    |
                      +-------------------+-------------------+
                                          |
                       +------------------+------------------+
                       |                                     |
                       v                                     v
         +----------------------------+       +-----------------------------+
         |     Official GenAI SDK     |       |   Phase 2 Contract Types    |
         |       (google-genai)       |       |  - GeminiObservation        |
         |  - Lazy client init        |       |  - GeminiAnalysisResult     |
         |  - Sanitized secret logs   |       |  - Structured observations  |
         +----------------------------+       +-----------------------------+
```

### Components Implemented:
1. **Official SDK Dependency**: Added `google-genai>=2.0.0` to `requirements.txt` and installed package in the virtual environment.
2. **Centralized Configuration**: Added `GEMINI_ENABLED`, `GEMINI_API_KEY`, `GEMINI_MODEL`, `GEMINI_TIMEOUT_SECONDS`, and `GEMINI_MAX_RETRIES` to `app.core.config.Settings`.
3. **Provider Abstraction**: Created `app.models.gemini_provider.GeminiProvider` encapsulating:
   - Safe client initialization without module import side-effects.
   - Strict credential validation (raising `GeminiConfigurationException` if enabled without credentials).
   - Sanitized exception reporting that redacts secrets from logs and responses.
   - Component readiness reporting for `/ready` endpoint.
   - Dependency injection support for offline unit testing and mocking.
4. **Future-Proof Interface & Schemas**:
   - `GeminiObservation`: Contract for visual observation candidates (object category, hazard suggestion, bounding box, evidence status, caveats, officer review requirement).
   - `GeminiAnalysisResult`: Contract envelope for multi-observation analysis.
   - `analyze_evidence()`: Provider method signature. In Phase 1, it raises `NotImplementedError` when enabled, strictly preserving Phase 1 boundaries without generating synthetic or fake findings.

---

## 3. Configuration & Local Setup

### Environment Variables

| Variable | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `GEMINI_ENABLED` | boolean | `false` | Enables/disables Gemini VLM provider. Must be `false` by default. |
| `GEMINI_API_KEY` | string | `None` | Google Gemini API key. Required only when `GEMINI_ENABLED=true`. |
| `GEMINI_MODEL` | string | `gemini-2.5-flash` | Configured Gemini model ID supporting multimodal input & structured output. |
| `GEMINI_TIMEOUT_SECONDS` | integer | `30` | Bounded HTTP/SDK request timeout in seconds (1–300). |
| `GEMINI_MAX_RETRIES` | integer | `3` | Bounded retries for transient API network errors (0–10). |

### Configuring Locally
1. Copy `.env.example` to `.env` (if not already created):
   ```bash
   cp .env.example .env
   ```
2. By default, Gemini remains disabled:
   ```env
   GEMINI_ENABLED=false
   ```
3. To enable Gemini for testing in development:
   ```env
   GEMINI_ENABLED=true
   GEMINI_API_KEY=your_actual_gemini_api_key_here
   GEMINI_MODEL=gemini-2.5-flash
   GEMINI_TIMEOUT_SECONDS=30
   GEMINI_MAX_RETRIES=3
   ```
   > **SECURITY WARNING**: Never commit `.env` or real API keys to source control. Never embed API keys in client-facing applications.

---

## 4. Health and Readiness Checks

The service readiness endpoint (`GET /ready`) honestly reflects provider status:

```json
{
  "status": "READY",
  "service": "firesphere-ai-service",
  "version": "0.3.0-phase3",
  "components": {
    "modelRunner": "READY",
    "evidenceStorage": "NOT_CONFIGURED",
    "preprocessor": "READY",
    "geminiProvider": "DISABLED"
  },
  "timestamp": "2026-10-09T18:00:00Z"
}
```

When `GEMINI_ENABLED=true` and credentials are configured, `geminiProvider` transitions to `"READY"`.

---

## 5. Verification & Testing

Tests are located in `tests/test_gemini_foundation_phase1.py` and require no network access and no real API keys.

### Run Phase 1 Tests
```bash
pytest tests/test_gemini_foundation_phase1.py -v
```

### Run Full Regression Test Suite
```bash
pytest
```

### Coverage Scope:
- `test_gemini_disabled_by_default`: Confirms fail-closed startup defaults.
- `test_app_imports_and_serves_health_without_credentials`: Ensures zero startup crashes when unconfigured.
- `test_enabled_without_credentials_raises_configuration_error`: Verifies clear, non-leaking configuration validation.
- `test_provider_initialization_with_missing_key`: Validates runtime exception when key is missing.
- `test_valid_configuration_and_model_id_selection`: Confirms dynamic model ID selection (`gemini-2.5-flash`, `gemini-2.5-pro`).
- `test_secrets_redacted_in_provider_metadata`: Validates that metadata and logs never expose API keys.
- `test_mocked_client_initialization_offline`: Verifies client injection without network access.
- `test_client_initialization_error_handling_sanitizes_secrets`: Confirms exception messages are sanitized.
- `test_future_proof_interface_boundaries`: Asserts `analyze_evidence` does not fabricate data during Phase 1.
- `test_existing_cv_model_approval_gate_unaltered`: Proves Phase 8.4 CV model remains `NOT_APPROVED`.
- `test_prohibition_of_autonomous_fire_noc_decisions`: Re-verifies strict AI governance boundary.

---

## 6. Safety & Governance Boundaries

1. **Human Officer Invariant**: AI findings are strictly advisory. AI cannot issue `AI_APPROVED` or `AI_REJECTED` Fire NOC decisions.
2. **Existing CV Model Approval Gate**: Remains untouched and fail-closed. Phase 8.4 model checkpoint remains `NOT_APPROVED` and `NOT_CALIBRATED`.
3. **No Synthetic Detections**: The provider does not fabricate placeholder findings on API failure.
4. **Secret Sanitization**: API keys are excluded from `get_provider_info()` and error messages.

---

## 7. Known Limitations & Roadmap

### Known Limitations (Phase 1):
- `analyze_evidence()` is an interface stub that raises `NotImplementedError` when enabled; vision inference prompts and image transmission are intentionally deferred to Phase 2.
- The system is **NOT production-approved**; no models are certified for production use.
- Officer review UI and conflict resolution workflows are not implemented in Phase 1.

### Roadmap for Subsequent Phases:
- **Phase 2 (Gemini Vision Analysis)**: Implement prompt engineering, image encoding, multimodal API requests, structured observation extraction, and error handling.
- **Phase 3 (Orchestration & Verification)**: Evidence validation, discrepancy resolution between Gemini and CV model, officer verification API, and verified inspection report generation.
- **Phase 4 (Evaluation & Rollout)**: Quantitative benchmark against held-out evidence, latency analysis, and controlled deployment gate.
