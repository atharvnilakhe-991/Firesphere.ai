# FireSphere.ai — Phase 2: Gemini Vision Analysis Implementation

## 1. Overview & Objectives

Phase 2 implements the visual analysis capability of the Google Gemini Vision-Language Model (VLM) within FireSphere.ai's AI service.

In accordance with strict architectural governance:
- **Phase 2 Scope**: Submitting validated inspection image evidence to Gemini, requesting structured JSON output using official Google GenAI Python SDK contracts, robustly parsing observations, strictly validating coordinate bounding boxes, handling transient retries and timeouts, and returning typed, unambiguous result envelopes.
- **Strict Boundary**: Phase 2 **DOES NOT** perform cross-model orchestration, CV-to-VLM conflict resolution, evidence reconciliation, officer verification workflows, or automated Fire NOC certification. Those capabilities belong strictly to Phase 3.
- **Fail-Closed Governance**: Gemini remains disabled by default (`GEMINI_ENABLED=false`). When enabled, valid credentials are required; unconfigured states never become silent zero-finding successes.
- **CV Model Gate Preserved**: The existing reference CV model (Phase 8.4) remains `NOT_APPROVED` and fail-closed. Gemini integration does not bypass or alter any existing safety gates.
- **Regulatory Boundary**: All observations are advisory only. Autonomous decisions (`AI_APPROVED` / `AI_REJECTED`) are strictly prohibited.

---

## 2. Files Changed & Added

| File Path | Status | Purpose |
| :--- | :--- | :--- |
| [ai-service/app/models/gemini_prompts.py](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_prompts.py) | **New** | Structured system instructions (`GEMINI_SYSTEM_INSTRUCTION`) and inspection domain prompt (`GEMINI_INSPECTION_PROMPT`). |
| [ai-service/app/models/gemini_schemas.py](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_schemas.py) | **New** | Structured raw schemas (`GeminiRawResponse`, `GeminiRawObservation`), explicit status enum (`GeminiAnalysisStatus`), typed observations (`GeminiObservation`), result envelope (`GeminiAnalysisResult`), and coordinate parser (`parse_and_validate_bounding_box`). |
| [ai-service/app/models/gemini_provider.py](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_provider.py) | **Updated** | Implemented `analyze_evidence()`: image payload resolution, binary magic check, quality check, bounded retry loop (`_call_gemini_with_retries`), structured output parsing, and sanitized error reporting. |
| [ai-service/app/models/\_\_init\_\_.py](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/__init__.py) | **Updated** | Exported `GeminiAnalysisStatus`, `parse_and_validate_bounding_box`, `GeminiRawObservation`, and `GeminiRawResponse`. |
| [ai-service/tests/test_gemini_foundation_phase1.py](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/tests/test_gemini_foundation_phase1.py) | **Updated** | Updated contract boundary check to verify that `analyze_evidence` fails safely on invalid binary payloads in Phase 2. |
| [ai-service/tests/test_gemini_vision_phase2.py](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/tests/test_gemini_vision_phase2.py) | **New** | 22 comprehensive Phase 2 unit/integration tests covering all failure, retry, schema, bounding box, and security cases. |
| [ai-service/docs/gemini_phase2_vision_analysis.md](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/docs/gemini_phase2_vision_analysis.md) | **New** | Comprehensive Phase 2 architectural and operational documentation. |

---

## 3. Actual Gemini Vision Analysis Flow

```
Raw Evidence (ID or Bytes)
          |
          v
1. Provider Gate Check (Disabled? Missing API key?) -> Early Return GEMINI_DISABLED / GEMINI_NOT_CONFIGURED
          |
          v
2. Evidence Validation (FileValidator) -> Detect JPEG/PNG/WebP magic signatures, check size limit
          |
          v
3. Quality & Geometry Check (ImageQualityValidator) -> Reject corrupted, zero-size, or sub-resolution image
          |
          v
4. GenAI Content Preparation -> Build image Part + Prompt + GenerateContentConfig (structured output)
          |
          v
5. Bounded Retry Execution (_call_gemini_with_retries) -> Up to GEMINI_MAX_RETRIES with backoff for transient errors
          |
          v
6. Structured Response Parsing -> Extract JSON, fallback sanitize, validate against GeminiRawResponse
          |
          +-----------------------+-----------------------+
          |                       |                       |
          v                       v                       v
[isInconclusive=true]     [observations=[]]       [observations=[...]]
          |                       |                       |
          v                       v                       v
ANALYSIS_INCONCLUSIVE    ANALYSIS_COMPLETED_     ANALYSIS_COMPLETED_
                        WITH_NO_OBSERVATIONS    WITH_OBSERVATIONS
```

---

## 4. Result Statuses & Semantics

The implementation strictly distinguishes seven independent lifecycle outcomes:

| Status Enum | Semantics | Safety Implications |
| :--- | :--- | :--- |
| `GEMINI_DISABLED` | Provider is disabled in config (`GEMINI_ENABLED=false`). | Fail-closed; no network calls attempted. |
| `GEMINI_NOT_CONFIGURED` | Provider is enabled but `GEMINI_API_KEY` is missing or empty. | Unconfigured state; never converted to successful empty result. |
| `ANALYSIS_COMPLETED_WITH_OBSERVATIONS` | Model completed inference and identified >=1 valid supported observations. | Advisory findings produced; mandatory human officer verification required. |
| `ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS` | Model completed inference on clear evidence and observed 0 supported defects. | **NOT proof of safety**. `isSafeInference=False` enforced. Human officer verification still required. |
| `ANALYSIS_INCONCLUSIVE` | Evidence is blurry, dark, occluded, or model reported insufficient visual data. | **NOT proof of compliance**. Direct physical re-inspection or additional photography required. |
| `ANALYSIS_FAILED` | Network timeout, transient error exhaustion, or bad image payload. | Service error recorded; unrelated manual inspection workflow unaffected. |
| `ANALYSIS_INVALID_RESPONSE` | Model output was malformed, corrupted JSON, or violated schema rules. | Controlled validation error; never converted to empty success. |

---

## 5. Bounding Box Coordinate Conversion & Validation

Gemini models emit 2D bounding boxes as `[ymin, xmin, ymax, xmax]` in an integer space of `0 - 1000`.

FireSphere internal schema requires normalized floats `[0.0 - 1.0]` with explicit keys `xMin, yMin, xMax, yMax`.

### Transformation & Validation Logic ([parse_and_validate_bounding_box](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_schemas.py#L98)):
1. **Format Handling**: Accepts 4-element sequence `[ymin, xmin, ymax, xmax]` or coordinate dictionaries (`ymin/xmin/ymax/xmax` or `yMin/xMin/yMax/xMax`).
2. **Coordinate Normalization**: If coordinates are in `0 - 1000` integer space (values with `max >= 10.0`), they are divided by `1000.0`.
3. **Finite Check**: Rejects any coordinates containing `NaN` or `Infinity`.
4. **Rounding Clamping**: Clamps minor rounding deviations (`-0.01 to 0.0 -> 0.0`; `1.0 to 1.01 -> 1.0`).
5. **Geometric Integrity**: Enforces `xmin < xmax`, `ymin < ymax`, and positive area `(xmax - xmin) * (ymax - ymin) > 0`.
6. **No Fabrication**: If the model provides `null` or invalid coordinates, `boundingBox = None`. Bounding boxes are never fabricated for non-spatial observations.

---

## 6. Retries, Timeouts, and Error Handling

- **Bounded Retries**: Uses `GEMINI_MAX_RETRIES` (default `3`).
- **Non-Retryable Errors**: HTTP 400 (Bad Request), 401 (Unauthorized), 403 (Forbidden) abort immediately on the first attempt without wasting retries.
- **Retryable Transient Errors**: HTTP 429 (Rate Limit), 500, 502, 503, 504, and connection dropouts retry with exponential backoff (`min(1.0 * 2^(attempt-1), 5.0)` seconds).
- **Timeouts**: Bounded via `GEMINI_TIMEOUT_SECONDS` (default `30s`), passed into the SDK via `types.HttpOptions(timeout=...)`.
- **Sanitized Logging**:
  - API keys are NEVER logged or included in error payloads.
  - Raw image bytes and base64 strings are NEVER written to application logs.

---

## 7. Configuration & Testing

### Environment Variables (.env)

```env
# Gemini VLM Integration (Phase 2)
GEMINI_ENABLED=false
GEMINI_API_KEY=
GEMINI_MODEL=gemini-2.5-flash
GEMINI_TIMEOUT_SECONDS=30
GEMINI_MAX_RETRIES=3
```

### Running Tests

#### Run Focused Phase 2 Vision Analysis Tests:
```bash
pytest tests/test_gemini_vision_phase2.py -v
```

#### Run Full Test Suite (Phase 1 through Phase 10 regression):
```bash
pytest
```

All 376 tests run offline without network access or live API credentials using mock client injection.

### Controlled Live Smoke Test (Optional in Dev Environment)
When valid credentials and network access are available:
```python
from app.core.config import Settings
from app.models.gemini_provider import GeminiProvider

cfg = Settings(
    gemini_enabled=True,
    gemini_api_key="your_live_gemini_api_key",
    gemini_model="gemini-2.5-flash",
)
provider = GeminiProvider(cfg)
with open("sample_inspection.jpg", "rb") as f:
    img_bytes = f.read()

result = provider.analyze_evidence(evidence_id="live-smoke-test-1", image_data=img_bytes)
print("Status:", result.status)
print("Observations count:", len(result.observations))
print("Officer verification required:", result.requiresOfficerVerification)
```

---

## 8. Privacy & Data-Transfer Governance

1. **Third-Party Processing**: Enabling Gemini transmits inspection photography to Google's Gemini API endpoints.
2. **Access Control**: Evidence retrieval adheres to existing tenant and S3 object storage authorization.
3. **No Local Persistence**: The provider does not persist copies of image bytes or API responses to local disks for debugging.
4. **Zero Secret Leakage**: API tokens and client headers are strictly redacted.

---

## 9. Regulatory Boundaries & Limitations

1. **NOT Production-Approved**: Neither the CV model nor the Gemini VLM is production-approved.
2. **CV Model Gate Unchanged**: The reference CV model (Phase 8.4) remains `NOT_APPROVED` and `NOT_CALIBRATED`.
3. **No Autonomous Certification**: Findings are advisory. The AI service will never issue `AI_APPROVED` or `AI_REJECTED` Fire NOC decisions.
4. **Phases 3 & 4 Pending**:
   - Cross-model orchestration and finding reconciliation belong to **Phase 3**.
   - Officer verification APIs and report generation belong to **Phase 3**.
   - Formal benchmark evaluation on held-out test sets belongs to **Phase 4**.
