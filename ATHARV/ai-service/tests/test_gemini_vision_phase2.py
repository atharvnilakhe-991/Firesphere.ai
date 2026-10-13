"""FireSphere.ai — Phase 2 Gemini Vision Analysis Tests.

Comprehensive test suite verifying:
- Section A: Configuration and fail-closed defaults.
- Section B: Successful visual analysis and observation mapping.
- Section C: Genuine empty result distinction (ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS vs failure).
- Section D: Inconclusive visual analysis (ANALYSIS_INCONCLUSIVE).
- Section E: Invalid output, malformed JSON, and corrupted responses (ANALYSIS_INVALID_RESPONSE).
- Section F: Bounding box normalization (0-1000 scale, dictionary, boundary clamping, rejection of invalid boxes).
- Section G: Provider errors, non-retryable vs transient retryable failures, timeouts.
- Section H: Security, credential protection, corrupted image rejection, and traversal protection.
- Section I: Regulatory boundaries, CV model gate preservation, and prohibition of autonomous Fire NOC decisions.
"""

import json
from unittest.mock import MagicMock, patch
import cv2
import numpy as np
import pytest

from app.core.config import Settings
from app.models.cv_model_adapter import CVModelAdapter
from app.models.gemini_provider import (
    GeminiAnalysisResult,
    GeminiAnalysisStatus,
    GeminiObservation,
    GeminiProvider,
)
from app.models.gemini_schemas import (
    parse_and_validate_bounding_box,
)
from app.models.model_approval_gate import model_approval_gate
from app.schemas.finding import BoundingBox, Finding, FindingSeverity


def create_test_jpeg_bytes(width: int = 640, height: int = 480) -> bytes:
    """Generates valid in-memory JPEG bytes with geometric features."""
    img = np.full((height, width, 3), (120, 130, 140), dtype=np.uint8)
    cv2.rectangle(img, (50, 50), (200, 300), (0, 0, 200), -1)
    cv2.circle(img, (400, 240), 80, (0, 255, 0), 2)
    cv2.putText(img, "FIRESPHERE TEST", (30, 450), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 255), 2)
    success, buf = cv2.imencode(".jpg", img)
    assert success
    return buf.tobytes()


def create_mock_genai_response(payload: dict) -> MagicMock:
    """Creates a mock GenAI response object containing serialized JSON text."""
    mock_resp = MagicMock()
    mock_resp.text = json.dumps(payload)
    return mock_resp


# ==============================================================================
# SECTION A: Configuration & Fail-Closed Behavior
# ==============================================================================

def test_gemini_disabled_by_default_makes_no_network_calls():
    """Provider disabled by default returns GEMINI_DISABLED without invoking SDK."""
    cfg = Settings(gemini_enabled=False)
    mock_client = MagicMock()
    provider = GeminiProvider(cfg, client=mock_client)

    img_bytes = create_test_jpeg_bytes()
    result = provider.analyze_evidence(evidence_id="ev-101", image_data=img_bytes)

    assert result.status == GeminiAnalysisStatus.GEMINI_DISABLED
    assert result.observations == []
    assert result.requiresOfficerVerification is True
    assert result.isSafeInference is False
    # Verified: SDK client was never called
    mock_client.models.generate_content.assert_not_called()


def test_gemini_enabled_missing_credentials_fails_gracefully():
    """Provider enabled without credentials returns GEMINI_NOT_CONFIGURED without crash."""
    mock_settings = MagicMock()
    mock_settings.gemini_enabled = True
    mock_settings.gemini_api_key = None
    mock_settings.gemini_model = "gemini-2.5-flash"
    mock_settings.gemini_timeout_seconds = 30
    mock_settings.gemini_max_retries = 3

    provider = GeminiProvider(mock_settings)
    img_bytes = create_test_jpeg_bytes()
    result = provider.analyze_evidence(evidence_id="ev-102", image_data=img_bytes)

    assert result.status == GeminiAnalysisStatus.GEMINI_NOT_CONFIGURED
    assert result.observations == []
    assert "GEMINI_API_KEY is not configured" in (result.error or "")


# ==============================================================================
# SECTION B: Successful Visual Analysis & Schema Normalization
# ==============================================================================

def test_successful_analysis_with_structured_observations():
    """Valid image and valid structured response produces ANALYSIS_COMPLETED_WITH_OBSERVATIONS."""
    cfg = Settings(gemini_enabled=True, gemini_api_key="mock-api-key")
    mock_client = MagicMock()

    mock_payload = {
        "isInconclusive": False,
        "inconclusiveReason": None,
        "observations": [
            {
                "objectCategory": "FIRE_EXTINGUISHER",
                "potentialHazardCategory": "FIRE_EXTINGUISHER_BLOCKED",
                "observationDescription": "CO2 fire extinguisher access obstructed by cardboard storage pallets.",
                "evidenceStatus": "DETECTED",
                "box_2d": [150, 200, 750, 600],  # 0-1000 scale: ymin, xmin, ymax, xmax
                "confidence": 0.94,
                "uncertaintyOrLimitations": ["Partial optical occlusion by pallet"],
                "severityProposal": "HIGH",
            },
            {
                "objectCategory": "EXIT_SIGN",
                "potentialHazardCategory": "NONE",
                "observationDescription": "Illuminated exit signage clearly visible above door frame.",
                "evidenceStatus": "DETECTED",
                "box_2d": [50, 400, 150, 800],
                "confidence": 0.88,
                "uncertaintyOrLimitations": [],
                "severityProposal": None,
            },
        ],
    }

    mock_client.models.generate_content.return_value = create_mock_genai_response(mock_payload)
    provider = GeminiProvider(cfg, client=mock_client)

    img_bytes = create_test_jpeg_bytes()
    result = provider.analyze_evidence(evidence_id="ev-201", image_data=img_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS
    assert len(result.observations) == 2
    assert result.requiresOfficerVerification is True
    assert result.isSafeInference is False

    # Verify first observation
    obs1 = result.observations[0]
    assert obs1.objectCategory == "FIRE_EXTINGUISHER"
    assert obs1.potentialHazardCategory == "FIRE_EXTINGUISHER_BLOCKED"
    assert obs1.severityProposal == "HIGH"
    assert obs1.confidence == 0.94
    assert obs1.boundingBox is not None
    # Converted from [ymin=150, xmin=200, ymax=750, xmax=600] to normalized [0.0 - 1.0]
    assert obs1.boundingBox.xMin == 0.2
    assert obs1.boundingBox.yMin == 0.15
    assert obs1.boundingBox.xMax == 0.6
    assert obs1.boundingBox.yMax == 0.75

    # Verify second observation with no defect (NONE mapped to None)
    obs2 = result.observations[1]
    assert obs2.objectCategory == "EXIT_SIGN"
    assert obs2.potentialHazardCategory is None
    assert obs2.severityProposal is None


# ==============================================================================
# SECTION C: Genuine Empty Result Distinction
# ==============================================================================

def test_genuine_empty_result_is_distinct_from_failure():
    """A clear inspection area with zero defects returns ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS.

    Must NOT be confused with an error, inconclusive image, or proof of safety.
    """
    cfg = Settings(gemini_enabled=True, gemini_api_key="mock-api-key")
    mock_client = MagicMock()

    mock_payload = {
        "isInconclusive": False,
        "inconclusiveReason": None,
        "observations": [],
    }

    mock_client.models.generate_content.return_value = create_mock_genai_response(mock_payload)
    provider = GeminiProvider(cfg, client=mock_client)

    img_bytes = create_test_jpeg_bytes()
    result = provider.analyze_evidence(evidence_id="ev-301", image_data=img_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS
    assert len(result.observations) == 0
    assert result.error is None
    # Crucial safety invariant: Absence of observations is NOT proof of building safety
    assert result.isSafeInference is False
    assert result.requiresOfficerVerification is True


# ==============================================================================
# SECTION D: Inconclusive Analysis
# ==============================================================================

def test_inconclusive_model_response():
    """Model-reported insufficient evidence returns ANALYSIS_INCONCLUSIVE."""
    cfg = Settings(gemini_enabled=True, gemini_api_key="mock-api-key")
    mock_client = MagicMock()

    mock_payload = {
        "isInconclusive": True,
        "inconclusiveReason": "Image is extremely dark and fire equipment markings cannot be discerned.",
        "observations": [],
    }

    mock_client.models.generate_content.return_value = create_mock_genai_response(mock_payload)
    provider = GeminiProvider(cfg, client=mock_client)

    img_bytes = create_test_jpeg_bytes()
    result = provider.analyze_evidence(evidence_id="ev-401", image_data=img_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_INCONCLUSIVE
    assert len(result.observations) == 0
    assert "extremely dark" in (result.inconclusiveReason or "")
    assert result.isSafeInference is False


# ==============================================================================
# SECTION E: Invalid Output, Malformed JSON, & Schema Violations
# ==============================================================================

def test_empty_response_text_returns_invalid_response():
    """Empty model response string returns ANALYSIS_INVALID_RESPONSE."""
    cfg = Settings(gemini_enabled=True, gemini_api_key="mock-api-key")
    mock_client = MagicMock()

    mock_resp = MagicMock()
    mock_resp.text = ""
    mock_client.models.generate_content.return_value = mock_resp
    provider = GeminiProvider(cfg, client=mock_client)

    img_bytes = create_test_jpeg_bytes()
    result = provider.analyze_evidence(evidence_id="ev-501", image_data=img_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_INVALID_RESPONSE
    assert "empty response" in (result.error or "").lower()


def test_malformed_json_returns_invalid_response():
    """Unparseable or corrupted JSON response returns ANALYSIS_INVALID_RESPONSE."""
    cfg = Settings(gemini_enabled=True, gemini_api_key="mock-api-key")
    mock_client = MagicMock()

    mock_resp = MagicMock()
    mock_resp.text = '{"isInconclusive": false, "observations": [ {"objectCategory": '  # Truncated
    mock_client.models.generate_content.return_value = mock_resp
    provider = GeminiProvider(cfg, client=mock_client)

    img_bytes = create_test_jpeg_bytes()
    result = provider.analyze_evidence(evidence_id="ev-502", image_data=img_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_INVALID_RESPONSE
    assert "failed schema validation" in (result.error or "").lower()


def test_fenced_markdown_json_fallback_parsed_successfully():
    """Handles response wrapped in ```json ... ``` code fence cleanly."""
    cfg = Settings(gemini_enabled=True, gemini_api_key="mock-api-key")
    mock_client = MagicMock()

    raw_json = json.dumps({
        "isInconclusive": False,
        "observations": [
            {
                "objectCategory": "EXIT_SIGN",
                "potentialHazardCategory": "NONE",
                "observationDescription": "Standard exit sign",
                "evidenceStatus": "DETECTED",
            }
        ],
    })
    mock_resp = MagicMock()
    mock_resp.text = f"```json\n{raw_json}\n```"
    mock_client.models.generate_content.return_value = mock_resp
    provider = GeminiProvider(cfg, client=mock_client)

    img_bytes = create_test_jpeg_bytes()
    result = provider.analyze_evidence(evidence_id="ev-503", image_data=img_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS
    assert len(result.observations) == 1


# ==============================================================================
# SECTION F: Bounding Box Coordinates & Normalization
# ==============================================================================

def test_bounding_box_coordinate_conversion_0_to_1000_scale():
    """Converts standard Gemini 0-1000 scale [ymin, xmin, ymax, xmax] to normalized [0.0 - 1.0]."""
    raw_box = [100, 200, 900, 800]  # ymin=100, xmin=200, ymax=900, xmax=800
    box = parse_and_validate_bounding_box(raw_box)

    assert box is not None
    assert box.xMin == 0.2
    assert box.yMin == 0.1
    assert box.xMax == 0.8
    assert box.yMax == 0.9


def test_bounding_box_coordinate_already_normalized():
    """Accepts coordinates that are already normalized [0.0 - 1.0]."""
    raw_box = [0.15, 0.25, 0.75, 0.85]
    box = parse_and_validate_bounding_box(raw_box)

    assert box is not None
    assert box.xMin == 0.25
    assert box.yMin == 0.15
    assert box.xMax == 0.85
    assert box.yMax == 0.75


def test_bounding_box_dictionary_format():
    """Accepts coordinate dictionary with ymin, xmin, ymax, xmax."""
    raw_dict = {"ymin": 300, "xmin": 150, "ymax": 700, "xmax": 450}
    box = parse_and_validate_bounding_box(raw_dict)

    assert box is not None
    assert box.xMin == 0.15
    assert box.yMin == 0.30
    assert box.xMax == 0.45
    assert box.yMax == 0.70


def test_bounding_box_clamping_minor_rounding_errors():
    """Clamps minor boundary deviations (-0.005 to 0.0, 1.005 to 1.0)."""
    raw_box = [-2.0, 10.0, 1005.0, 990.0]  # Minor out-of-bounds in 0-1000 scale
    box = parse_and_validate_bounding_box(raw_box)

    assert box is not None
    assert box.yMin == 0.0
    assert box.xMin == 0.01
    assert box.yMax == 1.0
    assert box.xMax == 0.99


def test_bounding_box_rejection_of_invalid_geometries():
    """Rejects inverted coordinates, zero/negative area, and non-finite values."""
    # xmin >= xmax
    assert parse_and_validate_bounding_box([100, 500, 800, 200]) is None

    # ymin >= ymax
    assert parse_and_validate_bounding_box([800, 200, 100, 500]) is None

    # Severely out of bounds (> 1.2 in float scale)
    assert parse_and_validate_bounding_box([0.1, 0.2, 0.5, 2.5]) is None

    # Non-finite values (NaN / Inf)
    assert parse_and_validate_bounding_box([float("nan"), 0.2, 0.5, 0.8]) is None
    assert parse_and_validate_bounding_box([0.1, float("inf"), 0.5, 0.8]) is None

    # Empty / null input returns None without error
    assert parse_and_validate_bounding_box(None) is None
    assert parse_and_validate_bounding_box([]) is None


def test_bounding_box_rejection_of_ambiguous_scales():
    """Rejects ambiguous coordinate systems rather than guessing scale."""
    # Values between 1.05 and 10.0 cannot be definitively classified as 0-1000 or normalized
    assert parse_and_validate_bounding_box([3.0, 4.0, 7.0, 8.0]) is None

    # Values exceeding 1050 without verified image dimensions are ambiguous
    assert parse_and_validate_bounding_box([1500.0, 1600.0, 2200.0, 2400.0]) is None


def test_bounding_box_explicit_conventions():
    """Enforces explicit coordinate conventions when configured."""
    # Strict normalized convention
    norm_box = parse_and_validate_bounding_box([0.1, 0.2, 0.5, 0.6], coordinate_convention="normalized")
    assert norm_box is not None
    assert norm_box.xMin == 0.2
    assert norm_box.yMin == 0.1

    # Strict normalized rejects values > 1.05
    assert parse_and_validate_bounding_box([100.0, 200.0, 500.0, 600.0], coordinate_convention="normalized") is None

    # Absolute pixel convention with verified image dimensions (w=640, h=480)
    # [ymin=120, xmin=160, ymax=360, xmax=480] -> xMin=160/640=0.25, yMin=120/480=0.25, xMax=480/640=0.75, yMax=360/480=0.75
    px_box = parse_and_validate_bounding_box(
        [120, 160, 360, 480],
        image_dims=(640, 480),
        coordinate_convention="absolute_pixels",
    )
    assert px_box is not None
    assert px_box.xMin == 0.25
    assert px_box.yMin == 0.25
    assert px_box.xMax == 0.75
    assert px_box.yMax == 0.75



# ==============================================================================
# SECTION G: Provider Errors, Retries & Timeouts
# ==============================================================================

def test_non_retryable_auth_error_aborts_immediately():
    """Permanent authentication errors (401/403) fail immediately without retrying."""
    cfg = Settings(gemini_enabled=True, gemini_api_key="bad-key", gemini_max_retries=3)
    mock_client = MagicMock()
    mock_client.models.generate_content.side_effect = RuntimeError("401 Unauthorized API_KEY_INVALID")

    provider = GeminiProvider(cfg, client=mock_client)
    img_bytes = create_test_jpeg_bytes()
    result = provider.analyze_evidence(evidence_id="ev-601", image_data=img_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert "Gemini API request failed" in (result.error or "")
    # Should have executed only 1 attempt because it was non-retryable
    assert mock_client.models.generate_content.call_count == 1


def test_transient_error_retries_and_recovers():
    """Transient network error (e.g., 503) retries and succeeds on subsequent attempt."""
    cfg = Settings(gemini_enabled=True, gemini_api_key="mock-key", gemini_max_retries=3)
    mock_client = MagicMock()

    success_payload = {
        "isInconclusive": False,
        "observations": [
            {
                "objectCategory": "FIRE_EXTINGUISHER",
                "potentialHazardCategory": "NONE",
                "observationDescription": "Extinguisher visible",
                "evidenceStatus": "DETECTED",
            }
        ],
    }

    # First attempt fails with 503, second attempt succeeds
    mock_client.models.generate_content.side_effect = [
        RuntimeError("503 Service Unavailable"),
        create_mock_genai_response(success_payload),
    ]

    provider = GeminiProvider(cfg, client=mock_client)
    img_bytes = create_test_jpeg_bytes()

    with patch("time.sleep") as mock_sleep:
        result = provider.analyze_evidence(evidence_id="ev-602", image_data=img_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS
    assert len(result.observations) == 1
    assert mock_client.models.generate_content.call_count == 2
    mock_sleep.assert_called_once()


def test_exhausted_retries_returns_analysis_failed():
    """Persistent transient errors exhaust retries and return ANALYSIS_FAILED."""
    cfg = Settings(gemini_enabled=True, gemini_api_key="mock-key", gemini_max_retries=2)
    mock_client = MagicMock()
    mock_client.models.generate_content.side_effect = RuntimeError("503 Service Unavailable")

    provider = GeminiProvider(cfg, client=mock_client)
    img_bytes = create_test_jpeg_bytes()

    with patch("time.sleep"):
        result = provider.analyze_evidence(evidence_id="ev-603", image_data=img_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert "Gemini API request failed" in (result.error or "")
    assert mock_client.models.generate_content.call_count == 2


# ==============================================================================
# SECTION H: Security, Corrupted Payloads & Traversal Protection
# ==============================================================================

def test_corrupted_image_payload_fails_safely():
    """Corrupted binary bytes reject safely without sending request to Gemini."""
    cfg = Settings(gemini_enabled=True, gemini_api_key="mock-key")
    mock_client = MagicMock()
    provider = GeminiProvider(cfg, client=mock_client)

    corrupted_bytes = b"\xff\xd8\xffCORRUPTED_TRUNCATED_BINARY_BYTES"
    result = provider.analyze_evidence(evidence_id="ev-701", image_data=corrupted_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert "Image validation rejected" in (result.error or "")
    mock_client.models.generate_content.assert_not_called()


def test_sub_resolution_image_rejected():
    """Image below minimum acceptable dimensions is rejected by quality check."""
    cfg = Settings(gemini_enabled=True, gemini_api_key="mock-key")
    mock_client = MagicMock()
    provider = GeminiProvider(cfg, client=mock_client)

    tiny_bytes = create_test_jpeg_bytes(width=16, height=16)
    result = provider.analyze_evidence(evidence_id="ev-702", image_data=tiny_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert "quality" in (result.error or "").lower()
    mock_client.models.generate_content.assert_not_called()


def test_unsupported_media_type_fails_safely():
    """Unsupported non-image bytes (e.g. text/html) are rejected."""
    cfg = Settings(gemini_enabled=True, gemini_api_key="mock-key")
    mock_client = MagicMock()
    provider = GeminiProvider(cfg, client=mock_client)

    html_bytes = b"<html><body>Not an image</body></html>"
    result = provider.analyze_evidence(evidence_id="ev-703", image_data=html_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert "Image validation rejected" in (result.error or "")
    mock_client.models.generate_content.assert_not_called()


def test_secrets_never_leak_in_error_payloads():
    """Sensitive API keys never appear in returned result errors or representations."""
    secret_key = "AIzaSySuperSecretKeyDoNotExpose12345"
    cfg = Settings(gemini_enabled=True, gemini_api_key=secret_key)
    mock_client = MagicMock()
    mock_client.models.generate_content.side_effect = RuntimeError(f"SDK error with {secret_key}")

    provider = GeminiProvider(cfg, client=mock_client)
    img_bytes = create_test_jpeg_bytes()
    result = provider.analyze_evidence(evidence_id="ev-704", image_data=img_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert secret_key not in (result.error or "")
    assert secret_key not in str(result)


# ==============================================================================
# SECTION I: CV Model Gate Preservation & Fire NOC Invariants
# ==============================================================================

def test_cv_model_approval_gate_remains_intact():
    """Gemini Phase 2 visual analysis does NOT alter or bypass the CV model approval gate."""
    # CV model approval gate remains NOT_APPROVED
    assert model_approval_gate.is_production_approved() is False
    perm = model_approval_gate.check_inference_permission(test_mode=False)
    assert perm["authorized"] is False
    assert perm["modelApprovalStatus"] == "NOT_APPROVED"

    # Baseline CV adapter still fails closed
    adapter = CVModelAdapter()
    dummy_array = np.zeros((640, 640, 3), dtype=np.float32)
    dets = adapter.predict(evidence_id="ev-801", preprocessed_image=dummy_array, test_mode=False)
    assert dets == []


def test_ai_prohibited_from_issuing_autonomous_fire_noc_decision():
    """Enforces regulatory invariant: AI cannot autonomously issue AI_APPROVED / AI_REJECTED."""
    with pytest.raises(ValueError) as exc:
        Finding(
            evidenceId="b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22",
            findingType="FIRE_EXTINGUISHER_MISSING",
            confidence=0.99,
            severity=FindingSeverity.HIGH,
            explanation="Autonomous NOC approval attempt",
            model="gemini-2.5-flash",
            modelVersion="gemini-genai-v2",
            complianceStatus="AI_APPROVED",
        )

    assert "AI cannot autonomously approve or reject" in str(exc.value)
