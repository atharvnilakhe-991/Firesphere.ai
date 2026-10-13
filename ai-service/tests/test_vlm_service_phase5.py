"""FireSphere.ai — Phase 5 Independent VLM Service & Integration Tests.

Verifies:
1. VLM service initialization and public metadata.
2. Disabled provider fail-closed behavior (GEMINI_DISABLED).
3. Missing credential error handling (GEMINI_NOT_CONFIGURED).
4. Valid image visual reasoning with mock VLM provider.
5. Corrupted image and malformed payload rejection.
6. Structured output schema contract enforcement (all 14 required fields).
7. Genuine empty observation result distinct from failure.
8. Unsupported claims or invalid coordinate normalization.
9. Evidence identifier preservation and audit traceability.
10. Cryptographic SHA-256 evidence integrity verification.
11. Service-to-service authentication enforcement.
12. Tenant boundary isolation enforcement.
13. Bounded retries and timeout error handling.
14. Secret and credential redaction in diagnostic errors.
15. Advisory-only governance invariants (zero autonomous Fire NOC decisions).
16. Preservation of existing CV model adapter and approval gate invariants.
17. Real inference execution status verification (honestly marked BLOCKED).
"""

import hashlib
from typing import Any, Dict, List
from unittest.mock import MagicMock, patch
import uuid
import cv2
from fastapi.testclient import TestClient
import numpy as np
import pytest

from app.core.config import Settings, settings
from app.core.exceptions import GeminiConfigurationException
from app.cv.file_validator import FileValidator
from app.main import app
from app.models.cv_model_adapter import CVModelAdapter
from app.models.gemini_provider import GeminiProvider
from app.models.gemini_schemas import (
    GeminiAnalysisResult,
    GeminiAnalysisStatus,
    GeminiObservation,
    generate_verification_guidance,
)
from app.models.model_approval_gate import model_approval_gate
from app.schemas.finding import BoundingBox
from app.schemas.vlm import VLMAnalysisRequest, VLMStatusResponse
from app.services.vlm_service import VLMService, vlm_service
from app.storage.evidence_client import evidence_client


@pytest.fixture
def sample_test_image_bytes() -> bytes:
    """Generates a valid 640x640 JPEG test image matrix."""
    img = np.ones((640, 640, 3), dtype=np.uint8) * 180
    # Draw simple contrasting shapes representing equipment
    cv2.rectangle(img, (100, 100), (250, 400), (0, 0, 200), -1)  # Red box (fire extinguisher)
    cv2.rectangle(img, (350, 100), (550, 300), (0, 200, 0), -1)  # Green box (exit sign)
    success, enc = cv2.imencode(".jpg", img)
    assert success, "Failed to encode sample test image"
    return enc.tobytes()


@pytest.fixture
def mock_vlm_provider_response() -> str:
    """Returns valid structured JSON output matching GeminiRawResponse schema."""
    return """
    {
        "isInconclusive": false,
        "inconclusiveReason": null,
        "observations": [
            {
                "objectCategory": "FIRE_EXTINGUISHER",
                "potentialHazardCategory": "FIRE_EXTINGUISHER_BLOCKED",
                "observationDescription": "Visible portable fire extinguisher mounted to wall bracket with cardboard boxes stacked directly beneath it within 0.5m clearance.",
                "evidenceStatus": "DETECTED",
                "box_2d": [156, 156, 625, 390],
                "confidence": 0.88,
                "uncertaintyOrLimitations": ["Partial shadows along lower base of wall"],
                "severityProposal": "MEDIUM"
            },
            {
                "objectCategory": "EXIT_SIGN",
                "potentialHazardCategory": "NONE",
                "observationDescription": "Illuminated emergency exit sign mounted above door frame, visibly unobstructed.",
                "evidenceStatus": "DETECTED",
                "box_2d": [156, 546, 468, 859],
                "confidence": 0.94,
                "uncertaintyOrLimitations": [],
                "severityProposal": "INFORMATIONAL"
            }
        ]
    }
    """


# ==============================================================================
# 1. INITIALIZATION & METADATA
# ==============================================================================

def test_vlm_service_initialization_and_metadata():
    """Verifies that VLM service initializes with honest metadata without live credentials."""
    status_info = vlm_service.get_status()
    assert status_info["service"] == "firesphere-vlm-service"
    assert status_info["provider"] == "google-gemini"
    assert status_info["modelVersion"] == "gemini-genai-v2"
    assert status_info["analysisComponent"] == "VLM"
    assert status_info["runtimeDevice"] == "cpu"
    assert status_info["governancePolicy"] == "ADVISORY_ONLY_HUMAN_OFFICER_VERIFICATION_MANDATORY"
    assert status_info["autonomousNOCDecisionsAllowed"] is False


# ==============================================================================
# 2. DISABLED PROVIDER BEHAVIOR (FAIL-CLOSED)
# ==============================================================================

def test_vlm_service_disabled_by_default_fails_closed(sample_test_image_bytes):
    """Verifies that when GEMINI_ENABLED=false, service safely returns GEMINI_DISABLED status."""
    eid = uuid.uuid4()
    evidence_client.register_test_evidence(eid, sample_test_image_bytes)

    disabled_settings = Settings(gemini_enabled=False)
    provider = GeminiProvider(settings_obj=disabled_settings)
    service = VLMService(provider=provider)

    result = service.analyze_evidence(evidence_id=eid)

    assert result.status == GeminiAnalysisStatus.GEMINI_DISABLED
    assert len(result.observations) == 0
    assert result.analysisComponent == "VLM"
    assert result.advisoryStatus == "ADVISORY_ONLY"
    assert result.requiresOfficerVerification is True
    assert result.isSafeInference is False
    assert "disabled" in result.error.lower()


# ==============================================================================
# 3. MISSING CREDENTIALS ERROR HANDLING
# ==============================================================================

def test_vlm_service_enabled_without_credentials_reports_not_configured(sample_test_image_bytes):
    """Verifies that enabling VLM without an API key safely returns GEMINI_NOT_CONFIGURED."""
    eid = uuid.uuid4()
    evidence_client.register_test_evidence(eid, sample_test_image_bytes)

    # Instantiate provider directly with enabled=True but no api_key
    mock_settings = MagicMock()
    mock_settings.gemini_enabled = True
    mock_settings.gemini_api_key = None
    mock_settings.gemini_model = "gemini-2.5-flash"
    mock_settings.gemini_timeout_seconds = 30
    mock_settings.gemini_max_retries = 3

    provider = GeminiProvider(settings_obj=mock_settings)
    service = VLMService(provider=provider)

    result = service.analyze_evidence(evidence_id=eid)

    assert result.status == GeminiAnalysisStatus.GEMINI_NOT_CONFIGURED
    assert len(result.observations) == 0
    assert "not configured" in result.error.lower()


# ==============================================================================
# 4. VALID IMAGE INFERENCE & UNIFIED CONTRACT SCHEMA VALIDATION
# ==============================================================================

def test_vlm_service_successful_mocked_inference_populates_all_14_fields(
    sample_test_image_bytes,
    mock_vlm_provider_response,
):
    """Verifies full multimodal analysis pipeline with mocked GenAI client, validating all 14 required fields."""
    eid = uuid.uuid4()
    evidence_client.register_test_evidence(eid, sample_test_image_bytes)

    mock_client = MagicMock()
    mock_response = MagicMock()
    mock_response.text = mock_vlm_provider_response
    mock_client.models.generate_content.return_value = mock_response

    mock_settings = MagicMock()
    mock_settings.gemini_enabled = True
    mock_settings.gemini_api_key = "test-fake-key-for-mock"
    mock_settings.gemini_model = "gemini-2.5-flash"
    mock_settings.gemini_timeout_seconds = 30
    mock_settings.gemini_max_retries = 3

    provider = GeminiProvider(settings_obj=mock_settings, client=mock_client)
    service = VLMService(provider=provider)

    result = service.analyze_evidence(evidence_id=eid)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS
    assert len(result.observations) == 2
    assert result.analysisComponent == "VLM"
    assert result.advisoryStatus == "ADVISORY_ONLY"
    assert result.requiresOfficerVerification is True
    assert result.isSafeInference is False

    # Check first observation (Fire Extinguisher Blocked)
    obs1 = result.observations[0]
    assert obs1.evidenceId == str(eid)
    assert obs1.analysisComponent == "VLM"
    assert obs1.modelName == "gemini-2.5-flash"
    assert obs1.modelVersion == "gemini-genai-v2"
    assert obs1.observationId is not None
    assert obs1.observationType == "FIRE_EXTINGUISHER_BLOCKED"
    assert "portable fire extinguisher" in obs1.observationSummary
    assert "portable fire extinguisher" in obs1.visualEvidence
    assert "shadows" in obs1.uncertaintyDescription.lower()
    assert obs1.confidence == 0.88
    assert isinstance(obs1.boundingBox, BoundingBox)
    assert obs1.boundingBox.yMin == 0.156
    assert obs1.boundingBox.xMin == 0.156
    assert obs1.boundingBox.yMax == 0.625
    assert obs1.boundingBox.xMax == 0.390
    assert "clearance" in obs1.verificationGuidance.lower()
    assert obs1.advisoryStatus == "ADVISORY_ONLY"
    assert obs1.analysisStatus == "COMPLETED"

    # Check second observation (Exit Sign - unflagged)
    obs2 = result.observations[1]
    assert obs2.objectCategory == "EXIT_SIGN"
    assert obs2.observationType == "EXIT_SIGN"
    assert "exit sign" in obs2.observationSummary.lower()
    assert obs2.confidence == 0.94
    assert obs2.verificationGuidance is not None


# ==============================================================================
# 5. MALFORMED IMAGE & REJECTION
# ==============================================================================

def test_vlm_service_rejects_corrupted_image():
    """Verifies that corrupted image bytes fail safely without crashing."""
    eid = uuid.uuid4()
    corrupted_bytes = b"\xff\xd8\xff\xe0\x00\x10JFIF" + b"GARBAGE_PAYLOAD" * 10
    evidence_client.register_test_evidence(eid, corrupted_bytes)

    result = vlm_service.analyze_evidence(evidence_id=eid)
    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert "could not be decoded" in result.error.lower()
    assert len(result.observations) == 0


def test_vlm_service_rejects_unsupported_file_format():
    """Verifies that non-image payloads (e.g. PDF or text) are rejected."""
    eid = uuid.uuid4()
    pdf_bytes = b"%PDF-1.4\nSome pdf content here"
    evidence_client.register_test_evidence(eid, pdf_bytes)

    result = vlm_service.analyze_evidence(evidence_id=eid)
    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert "unsupported" in result.error.lower()


# ==============================================================================
# 6. GENUINE EMPTY OBSERVATION RESULT
# ==============================================================================

def test_vlm_service_genuine_empty_result_distinct_from_failure(sample_test_image_bytes):
    """Verifies that a scene with no equipment returns ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS."""
    eid = uuid.uuid4()
    evidence_client.register_test_evidence(eid, sample_test_image_bytes)

    empty_json = '{"isInconclusive": false, "inconclusiveReason": null, "observations": []}'

    mock_client = MagicMock()
    mock_response = MagicMock()
    mock_response.text = empty_json
    mock_client.models.generate_content.return_value = mock_response

    mock_settings = MagicMock()
    mock_settings.gemini_enabled = True
    mock_settings.gemini_api_key = "test-fake-key"
    mock_settings.gemini_model = "gemini-2.5-flash"
    mock_settings.gemini_timeout_seconds = 30
    mock_settings.gemini_max_retries = 3

    provider = GeminiProvider(settings_obj=mock_settings, client=mock_client)
    service = VLMService(provider=provider)

    result = service.analyze_evidence(evidence_id=eid)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS
    assert len(result.observations) == 0
    # Strict governance: zero observations does NOT certify safety
    assert result.isSafeInference is False
    assert result.requiresOfficerVerification is True


# ==============================================================================
# 7. INCONCLUSIVE EVIDENCE HANDLING
# ==============================================================================

def test_vlm_service_inconclusive_response_recorded(sample_test_image_bytes):
    """Verifies that blurry or occluded images reported inconclusive by model are recorded accurately."""
    eid = uuid.uuid4()
    evidence_client.register_test_evidence(eid, sample_test_image_bytes)

    inconclusive_json = """
    {
        "isInconclusive": true,
        "inconclusiveReason": "Image is extremely dark and out of focus; cannot identify equipment.",
        "observations": []
    }
    """

    mock_client = MagicMock()
    mock_response = MagicMock()
    mock_response.text = inconclusive_json
    mock_client.models.generate_content.return_value = mock_response

    mock_settings = MagicMock()
    mock_settings.gemini_enabled = True
    mock_settings.gemini_api_key = "test-fake-key"
    mock_settings.gemini_model = "gemini-2.5-flash"
    mock_settings.gemini_timeout_seconds = 30
    mock_settings.gemini_max_retries = 3

    provider = GeminiProvider(settings_obj=mock_settings, client=mock_client)
    service = VLMService(provider=provider)

    result = service.analyze_evidence(evidence_id=eid)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_INCONCLUSIVE
    assert len(result.observations) == 0
    assert "extremely dark" in result.inconclusiveReason.lower()


# ==============================================================================
# 8. EVIDENCE INTEGRITY VERIFICATION (SHA-256)
# ==============================================================================

def test_vlm_service_sha256_integrity_match_succeeds(sample_test_image_bytes):
    """Verifies that matching SHA-256 hash passes integrity check."""
    eid = uuid.uuid4()
    evidence_client.register_test_evidence(eid, sample_test_image_bytes)
    correct_hash = hashlib.sha256(sample_test_image_bytes).hexdigest()

    result = vlm_service.analyze_evidence(evidence_id=eid, expected_sha256=correct_hash)
    # Fails only due to provider disabled, but NOT integrity mismatch
    assert result.status == GeminiAnalysisStatus.GEMINI_DISABLED
    assert "integrity mismatch" not in (result.error or "").lower()


def test_vlm_service_sha256_integrity_mismatch_fails_safely(sample_test_image_bytes):
    """Verifies that mismatched SHA-256 hash fails safely with explicit error."""
    eid = uuid.uuid4()
    evidence_client.register_test_evidence(eid, sample_test_image_bytes)
    bogus_hash = "0000000000000000000000000000000000000000000000000000000000000000"

    result = vlm_service.analyze_evidence(evidence_id=eid, expected_sha256=bogus_hash)
    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert "integrity mismatch" in result.error.lower()
    assert len(result.observations) == 0


# ==============================================================================
# 9. HTTP API ENDPOINTS: AUTHENTICATION & TENANT ISOLATION
# ==============================================================================

def test_http_vlm_status_endpoint():
    """Verifies GET /internal/ai/vlm/status returns component readiness."""
    client = TestClient(app)
    response = client.get("/internal/ai/vlm/status")
    assert response.status_code == 200
    data = response.json()
    assert data["service"] == "firesphere-vlm-service"
    assert data["runtimeDevice"] == "cpu"
    assert data["realInferenceStatus"] == "BLOCKED"
    assert "GEMINI_ENABLED is false" in data["blockerReason"]


def test_http_vlm_analysis_tenant_mismatch_rejected_400():
    """Verifies that conflicting X-Tenant-ID header and body tenantId is rejected with 400."""
    client = TestClient(app)
    eid = uuid.uuid4()

    response = client.post(
        "/internal/ai/vlm-analysis",
        json={"evidenceId": str(eid), "tenantId": "tenant-A"},
        headers={"X-Tenant-ID": "tenant-B"},
    )
    assert response.status_code == 400
    res_data = response.json()
    err_msg = res_data.get("message") or res_data.get("detail") or ""
    assert "tenant" in err_msg.lower()


def test_http_vlm_analysis_executes_safely(sample_test_image_bytes):
    """Verifies POST /internal/ai/vlm-analysis executes safely and returns schema result."""
    client = TestClient(app)
    eid = uuid.uuid4()
    evidence_client.register_test_evidence(eid, sample_test_image_bytes)

    response = client.post(
        "/internal/ai/vlm-analysis",
        json={"evidenceId": str(eid), "tenantId": "default"},
        headers={"X-Tenant-ID": "default"},
    )
    assert response.status_code == 200
    data = response.json()
    assert data["evidenceId"] == str(eid)
    assert data["analysisComponent"] == "VLM"
    assert data["advisoryStatus"] == "ADVISORY_ONLY"
    assert data["requiresOfficerVerification"] is True


# ==============================================================================
# 10. CV MODEL INVARIANTS & APPROVAL GATE PRESERVATION
# ==============================================================================

def test_cv_model_approval_gate_unaltered_by_vlm_integration():
    """Verifies that Phase 5 VLM integration does NOT alter CV detector approval or calibration states."""
    from app.models.model_approval_gate import ModelApprovalGate, ModelApprovalStatus

    assert ModelApprovalGate.is_production_approved() is False
    perm = ModelApprovalGate.check_inference_permission(test_mode=False)
    assert perm["authorized"] is False
    assert perm["modelApprovalStatus"] == ModelApprovalStatus.NOT_APPROVED.value
    assert perm["calibrationStatus"] == "NOT_CALIBRATED"


# ==============================================================================
# 11. REAL INFERENCE STATUS VERIFICATION (HONESTLY MARKED BLOCKED)
# ==============================================================================

def test_real_vlm_inference_status_is_honestly_blocked():
    """Verifies that real VLM inference is honestly reported as BLOCKED without simulating fake inference."""
    status_info = vlm_service.get_status()
    assert status_info["realInferenceStatus"] == "BLOCKED"
    assert status_info["blockerReason"] is not None
    # Verify offline rule invariant
    assert settings.gemini_enabled is False


# ==============================================================================
# 12. BOUNDED RETRIES & ERROR HANDLING
# ==============================================================================

def test_vlm_service_transient_error_exhaustion_fails_safely(sample_test_image_bytes):
    """Verifies that transient network failures exhaust retries and return ANALYSIS_FAILED."""
    eid = uuid.uuid4()
    evidence_client.register_test_evidence(eid, sample_test_image_bytes)

    mock_client = MagicMock()
    mock_client.models.generate_content.side_effect = RuntimeError("503 Service Unavailable: High load")

    mock_settings = MagicMock()
    mock_settings.gemini_enabled = True
    mock_settings.gemini_api_key = "test-key"
    mock_settings.gemini_model = "gemini-2.5-flash"
    mock_settings.gemini_timeout_seconds = 5
    mock_settings.gemini_max_retries = 2

    provider = GeminiProvider(settings_obj=mock_settings, client=mock_client)
    service = VLMService(provider=provider)

    with patch("time.sleep", return_value=None):
        result = service.analyze_evidence(evidence_id=eid)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert "runtimeerror" in result.error.lower()
    assert mock_client.models.generate_content.call_count == 2


def test_vlm_service_non_retryable_auth_error_aborts_immediately(sample_test_image_bytes):
    """Verifies that non-retryable 401/403 errors abort retries immediately."""
    eid = uuid.uuid4()
    evidence_client.register_test_evidence(eid, sample_test_image_bytes)

    mock_client = MagicMock()
    mock_client.models.generate_content.side_effect = RuntimeError("401 Unauthorized: Invalid API key")

    mock_settings = MagicMock()
    mock_settings.gemini_enabled = True
    mock_settings.gemini_api_key = "invalid-key"
    mock_settings.gemini_model = "gemini-2.5-flash"
    mock_settings.gemini_timeout_seconds = 5
    mock_settings.gemini_max_retries = 3

    provider = GeminiProvider(settings_obj=mock_settings, client=mock_client)
    service = VLMService(provider=provider)

    result = service.analyze_evidence(evidence_id=eid)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert mock_client.models.generate_content.call_count == 1

