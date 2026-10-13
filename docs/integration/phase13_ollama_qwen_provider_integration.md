# Phase 13: M4 Local Ollama/Qwen2.5-VL Provider Integration Report

**Project**: FireSphere.ai  
**Workspace**: `C:\Users\athar\OneDrive\Desktop\Firesphere.ai`  
**AI Service Subsystem**: `C:\Users\athar\OneDrive\Desktop\Firesphere.ai\ai-service`  
**Execution Timestamp**: 2026-10-12T23:48:00+05:30  
**Phase**: Phase 13 — M4 Local Ollama/Qwen2.5-VL Provider Integration  
**Final Verdict**: `PASS_OFFLINE_AND_LOCAL_INTEGRATION`

---

## 1. Pre-Implementation Findings

Prior to making any code modifications, a comprehensive inspection of the existing workspace, previous integration reports (Phase 11B, Phase 11C.1, Phase 12), and core provider architecture was conducted:

1. **Provider Abstraction (`BaseVLMProvider`)**:
   `app/models/vlm_provider.py` defines the canonical contract for VLM adapters (`analyze_evidence`, `get_provider_info`, `is_ready`, `is_enabled`, `is_configured`, `get_status`). All providers strictly enforce fail-closed defaults, zero autonomous Fire NOC certification, and mandatory human officer review (`requiresOfficerVerification = True`).
2. **Unified Schema Envelopes (`GeminiAnalysisResult` / `GeminiObservation`)**:
   In `app/models/gemini_schemas.py`, `GeminiAnalysisResult` (aliased to `VLMAnalysisResult`) serves as the universal result envelope across all VLM providers. It distinguishes `ANALYSIS_COMPLETED_WITH_OBSERVATIONS`, `ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS`, `ANALYSIS_INCONCLUSIVE`, `ANALYSIS_INVALID_RESPONSE`, `ANALYSIS_FAILED`, `PROVIDER_DISABLED`, and `PROVIDER_NOT_CONFIGURED`.
3. **Provider Factory (`get_vlm_provider`)**:
   Located in `app/models/vlm_provider.py`, the factory maps `vlm_provider` string configuration to concrete adapters (`gemini_provider`, `openai_provider`).
4. **Reconciliation Engine (`ResultReconciler`)**:
   In `app/intelligence/reconciliation.py`, the deterministic 12-case cross-model reconciliation matrix operates generically on `GeminiAnalysisResult` and preserves model provenance, conflict states, and individual provider findings.
5. **Phase 12 Verification State**:
   Phase 12 confirmed that Ollama v0.40.3 and `qwen2.5vl:3b` (digest `fb90415cde1e...`) were operational on the local host with 100% GPU offload on the NVIDIA GeForce RTX 4050 Laptop GPU (6GB VRAM).

---

## 2. Files Added and Modified

Minimal changes were implemented strictly within the existing architecture:

| File Path | Type | Role in Phase 13 Integration |
| :--- | :--- | :--- |
| `ai-service/app/core/exceptions.py` | Modified | Added domain exceptions `OllamaConfigurationException` and `OllamaProviderException`. |
| `ai-service/app/core/config.py` | Modified | Added Ollama configuration parameters (`ollama_enabled`, `ollama_base_url`, `ollama_model`, `ollama_timeout_seconds`, `ollama_connect_timeout_seconds`, `ollama_max_retries`) and sanitization validator. Updated `vlm_provider` description. |
| `ai-service/app/models/ollama_prompts.py` | **Added** | System instructions and domain perception taxonomy prompts for Ollama/Qwen multimodal visual inspection. |
| `ai-service/app/models/ollama_provider.py` | **Added** | `OllamaQwenProvider(BaseVLMProvider)` implementing local HTTP API communication, image decoding/quality checks, bounded retries, and schema mapping. |
| `ai-service/app/models/vlm_provider.py` | Modified | Registered `ollama` / `qwen` / `ollama-qwen` in the `get_vlm_provider` factory, with support for injected `settings_obj`. |
| `ai-service/app/services/vlm_service.py` | Modified | Updated `get_status()` to provide honest Ollama-specific blocker messages and default modelVersion. |
| `ai-service/tests/test_ollama_provider_phase13.py` | **Added** | Comprehensive 19-test offline unit test suite mocking local HTTP transport. |
| `docs/integration/phase13_ollama_qwen_provider_integration.md` | **Added** | Phase 13 comprehensive integration and verification report. |

---

## 3. Provider Configuration and Selection Behavior

### Configuration Parameters
The following settings were integrated into `app/core/config.py`:
- `vlm_provider`: Active VLM provider (`"gemini"`, `"openai"`, or `"ollama"`). Default: `"gemini"` (preserves existing system default).
- `ollama_enabled`: Boolean flag governing activation (default: `False`, fail-closed).
- `ollama_base_url`: Base URL for local Ollama daemon (default: `"http://127.0.0.1:11434"`). Trailing slashes are automatically sanitized.
- `ollama_model`: Configured model identifier (default: `"qwen2.5vl:3b"`).
- `ollama_timeout_seconds`: HTTP request timeout (default: `60` seconds, bounded 1–600s).
- `ollama_connect_timeout_seconds`: Connection timeout (default: `10` seconds, bounded 1–120s).
- `ollama_max_retries`: Maximum bounded retry attempts (default: `2`, bounded 0–10).

### Selection & Fallback Behavior
- The factory `get_vlm_provider(provider_name, settings_obj)` checks the requested provider name or `settings.vlm_provider`.
- If `"ollama"`, `"qwen"`, `"ollama-qwen"`, or `"ollama_qwen"` is specified, `OllamaQwenProvider` is returned.
- **Strictly No Silent Fallback**: If Ollama is selected but disabled or unreachable, the provider returns a controlled failure envelope (`PROVIDER_DISABLED` or `ANALYSIS_FAILED`). It **never** silently falls back to Google Gemini or OpenAI.

---

## 4. Ollama API Integration Details

The provider communicates with Ollama's local REST API:
- **Endpoint**: `POST /api/chat`
- **Transport**: `httpx.Client` with explicit connect and read timeouts (`httpx.Timeout(timeout=60.0, connect=10.0)`).
- **Payload Structure**:
  ```json
  {
    "model": "qwen2.5vl:3b",
    "messages": [
      {
        "role": "system",
        "content": "<OLLAMA_SYSTEM_INSTRUCTION>"
      },
      {
        "role": "user",
        "content": "<OLLAMA_INSPECTION_PROMPT>",
        "images": ["<base64_encoded_image_bytes>"]
      }
    ],
    "format": "json",
    "stream": false,
    "options": {
      "temperature": 0.1
    }
  }
  ```
- **Grammar-Enforced JSON**: Using Ollama's native `"format": "json"` ensures the model's output conforms strictly to JSON format.

---

## 5. Canonical Schema Mapping

The raw response string is parsed and mapped into the canonical FireSphere schemas:
1. **JSON Cleaning & Validation**: Markdown code fencing (````json ... ````) is stripped if present, and content is validated against `GeminiRawResponse`.
2. **Inconclusive Handling**: If `isInconclusive=True`, status is marked `ANALYSIS_INCONCLUSIVE` with `inconclusiveReason` preserved.
3. **Zero Detections**: If `observations` is empty and `isInconclusive=False`, status is marked `ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS` with `isSafeInference=False`.
4. **Observations Mapping**:
   - `objectCategory` and `potentialHazardCategory` are normalized against `SUPPORTED_OBJECT_CATEGORIES` and `SUPPORTED_HAZARD_CATEGORIES`.
   - `boundingBox` is parsed and validated using `parse_and_validate_bounding_box(raw_box, image_dims=(w, h), coordinate_convention="gemini_0_1000")`. Ambiguous, invalid, or missing coordinates resolve to `None`. **No synthetic bounding boxes are fabricated.**
   - `confidence` is retained only if the model supplied a valid finite probability in `[0.0, 1.0]`. **No synthetic confidence scores are invented.**
   - Advisory guidance is generated via `generate_verification_guidance`.
   - Governance invariants are attached: `analysisComponent="VLM"`, `modelVersion="ollama-qwen2.5vl-v1"`, `advisoryStatus="ADVISORY_ONLY"`, `requiresOfficerVerification=True`.

---

## 6. Error Handling and Retry Policy

The provider enforces bounded error handling:
- **Permanent Errors (No Retry)**:
  - HTTP 400 (Bad Request).
  - HTTP 404 (Model Not Found).
  - HTTP 422 (Unprocessable Entity).
  - Image decode / quality validation rejections.
  These abort immediately without wasting retries.
- **Transient Errors (Bounded Exponential Backoff)**:
  - Network connection failures (`httpx.ConnectError`).
  - Read/connect timeouts (`httpx.ReadTimeout`, `httpx.ConnectTimeout`).
  - Server errors (HTTP 500, 502, 503, 504).
  Retries up to `max_retries` with exponential backoff: `min(1.0 * (2 ** (attempt - 1)), 5.0)`.
- **Malformed / Empty Responses**:
  - Empty text returns `ANALYSIS_INVALID_RESPONSE`.
  - Schema parse failure returns `ANALYSIS_INVALID_RESPONSE` fail-closed.

---

## 7. Computer-Vision and VLM Reconciliation Behavior

The integration preserves the complete 12-case reconciliation matrix in `app/intelligence/reconciliation.py`:
- **Provenanced Findings**: Every finding generated through reconciliation preserves `gemini_model_info={"provider": "ollama", "model": "qwen2.5vl:3b"}` in its auditable provenance log.
- **Conflict Representation**: Disagreements between CV and Ollama are classified into explicit statuses (`AGREEMENT`, `SINGLE_PROVIDER`, `CONFLICT`, `DISCREPANCY`, `PARTIAL_INCONCLUSIVE`).
- **No Confidence Averaging**: Confidence scores from CV (detector) and Ollama (VLM) are never blended or averaged into synthetic composite metrics.
- **Safety**: An empty or unavailable Ollama result never certifies compliance or bypasses the CV approval gate.

---

## 8. Offline Automated Test Results

The new offline test suite (`ai-service/tests/test_ollama_provider_phase13.py`) was executed using pytest:

```bash
.\ai-service\.venv\Scripts\pytest.exe ai-service/tests/test_ollama_provider_phase13.py
```

### Results Summary
```
============================= test session starts =============================
collected 19 items

ai-service\tests\test_ollama_provider_phase13.py ...................     [100%]

======================= 19 passed, 2 warnings in 1.67s ========================
```

### Test Coverage Highlights
1. `test_ollama_provider_implements_base_vlm_interface`: Interface contract verified.
2. `test_ollama_provider_disabled_by_default`: Fail-closed default verified.
3. `test_ollama_provider_info_sanitization`: Verified zero credentials in metadata.
4. `test_ollama_successful_mock_response_mapping`: Schema mapping verified.
5. `test_ollama_successful_empty_response`: Zero-detection handling verified.
6. `test_ollama_inconclusive_response`: Inconclusive handling verified.
7. `test_ollama_corrupt_image_rejected`: Corrupt image rejection verified.
8. `test_ollama_sub_resolution_image_rejected`: Sub-resolution quality gate verified.
9. `test_ollama_disabled_provider_returns_safe_status`: Controlled failure envelope verified.
10. `test_ollama_connection_error_handling`: Network connection error handling verified.
11. `test_ollama_read_timeout_handling`: Read timeout handling verified.
12. `test_ollama_model_not_found_permanent_error`: 404 abort without retry verified.
13. `test_ollama_server_error_transient_retry`: HTTP 500 retry recovery verified.
14. `test_ollama_malformed_json_handling`: Schema failure invalid response verified.
15. `test_ollama_markdown_fenced_json_parsed`: Markdown fence strip parsing verified.
16. `test_no_fabricated_bounding_boxes_or_confidence`: Verified no hallucinated boxes/scores.
17. `test_factory_selects_ollama_without_silent_fallback`: Factory routing verified.
18. `test_cv_approval_gate_remains_independent_with_ollama`: CV gate independence verified.
19. `test_reconciliation_handles_ollama_findings_preserving_provenance`: Provenance preservation verified.

### Regression Suite Results
- `ai-service/tests/test_openai_provider_phase11b.py`: **20/20 passed** (0.31s).
- Core schema, confidence, cv preprocessing, and error handling suites: **24/24 passed** (0.27s).

---

## 9. Controlled Local Integration Test Result

A single controlled image inference request was executed against the local Ollama daemon using a procedurally synthesized 400x400 PNG test image (synthetic geometric red extinguisher body, yellow label, black handle and nozzle).

### Execution Parameters
- **Provider Instance**: `OllamaQwenProvider`
- **Configured Base URL**: `http://127.0.0.1:11434`
- **Configured Model**: `qwen2.5vl:3b`
- **External Calls**: **0** (strictly local HTTP request)
- **Database / MinIO Writes**: **0**

### Measured Observations & Schema Output
```json
{
  "status": "ANALYSIS_COMPLETED_WITH_OBSERVATIONS",
  "evidenceId": "c4c3faf4-cf17-412d-b4cf-44db4a160884",
  "model": "qwen2.5vl:3b",
  "modelVersion": "ollama-qwen2.5vl-v1",
  "provider": "ollama",
  "elapsed_seconds": 4.45,
  "executionDurationMs": 4449.78,
  "observation_count": 1,
  "observations": [
    {
      "observationId": "1f51ab6b-80ea-4e8b-ae3e-e35ad60bf0b6",
      "objectCategory": "FIRE_EXTINGUISHER",
      "potentialHazardCategory": "NONE",
      "description": "A red fire extinguisher with a yellow label is present.",
      "evidenceStatus": "DETECTED",
      "boundingBox": {
        "xMin": 0.244,
        "yMin": 0.356,
        "xMax": 0.799,
        "yMax": 0.569
      },
      "confidence": 0.9,
      "severityProposal": "INFORMATIONAL",
      "verificationGuidance": "Confirm visible extinguisher is accessible, undamaged, and properly maintained in accordance with building safety plan.",
      "advisoryStatus": "ADVISORY_ONLY",
      "officerReviewRequirement": "MANDATORY_OFFICER_REVIEW"
    }
  ],
  "inconclusiveReason": null,
  "error": null,
  "requiresOfficerVerification": true,
  "isSafeInference": false,
  "analysisComponent": "VLM",
  "advisoryStatus": "ADVISORY_ONLY"
}
```

---

## 10. Actual Measured Latency and Hardware Observations

| Telemetry / Metric | Measured Value | Notes |
| :--- | :--- | :--- |
| **Model Pre-Loaded State** | Already loaded in VRAM | Reused active runner (`runner: llamacpp`) |
| **End-to-End Latency** | `4.45 seconds` (`4,449.78 ms`) | Image encoding + HTTP roundtrip + token generation |
| **GPU Model** | NVIDIA GeForce RTX 4050 Laptop GPU | CUDA 13.1, Driver 592.82 |
| **Total GPU VRAM** | 6,141 MiB (~6.0 GB) | |
| **VRAM Allocated** | 4,143 MiB | Leaves 1,778 MiB free headroom |
| **GPU Utilization Peak** | 93% | Active during vision token evaluation |
| **Offload Percentage** | **100% GPU** | Zero CPU offload thrashing |
| **Execution Errors** | None | HTTP 200 OK |

---

## 11. Safety and Governance Confirmations

- **Zero Hosted AI Requests**: No calls were made to Google Gemini, OpenAI, or any remote AI endpoint.
- **Zero Persistence Writes**: Neither PostgreSQL/SQLite records nor MinIO storage objects were created or modified.
- **CV Gate Preserved**: The CV approval gate (`ModelApprovalGate.is_production_approved`) evaluated to `False`. Neither model was authorized for autonomous production deployment.
- **Advisory Output Only**: All output envelopes enforce `advisoryStatus="ADVISORY_ONLY"`, `requiresOfficerVerification=True`, and `isSafeInference=False`.

---

## 12. Known Limitations

1. **Context Window Allocation**: The Ollama runner allocates a 4,096 token context by default. Dense images or high-resolution multi-frame evidence will require tuning `num_ctx` in provider configuration.
2. **Local Workstation Concurrency**: Because Ollama runs as a single local server process with GPU offload, concurrent requests from multiple worker threads will queue sequentially.
3. **No Autonomous Certification**: The VLM model remains strictly advisory and cannot approve or reject Fire NOC applications.

---

## 13. Recommended Next Phase

**Final Verdict**: `PASS_OFFLINE_AND_LOCAL_INTEGRATION`

Both offline unit tests (19/19 passing) and the controlled local integration test (4.45s successful inference with valid canonical schema mapping) passed completely.

### Next Step: Phase 14 Boundary
The workspace is ready for **Phase 14: CV/VLM Joint Evaluation**. Phase 14 will evaluate CV and local VLM performance on a curated, labelled benchmark dataset to measure agreement rates, false positives, false negatives, uncertainty handling, and failure modes across diverse fire inspection scenes. Neither model is marked approved for production.
