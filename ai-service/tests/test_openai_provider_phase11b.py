"""FireSphere.ai — Phase 11B OpenAI VLM Provider Adapter Offline Test Suite.

Verifies:
A. Provider interface compatibility (BaseVLMProvider).
B. Successful mocked response.
C. Correct mapping into the existing response schema (GeminiAnalysisResult / GeminiObservation).
D. Missing API key handling.
E. Invalid image input rejection.
F. Malformed structured output handling.
G. Schema-validation failure handling.
H. Connection failure handling.
I. Timeout handling.
J. HTTP 429 rate limit handling.
K. HTTP 500 or 503 server error handling.
L. Retry bounds and duplicate-request prevention.
M. No observations fabricated after failure.
N. Evidence ID preservation.
O. Model/provider provenance.
P. Mandatory officer verification.
Q. Advisory-only output.
R. CV approval and calibration gates remain unchanged.
"""

import json
from unittest.mock import MagicMock, patch
import uuid
import cv2
import httpx
import numpy as np
import pytest

from app.core.config import Settings
from app.models.model_approval_gate import model_approval_gate
from app.models.gemini_schemas import (
    GeminiAnalysisResult,
    GeminiAnalysisStatus,
    GeminiObservation,
)
from app.models.openai_provider import OpenAIProvider, openai_provider
from app.models.vlm_provider import BaseVLMProvider
from app.services.inspection_orchestrator import InspectionOrchestrator
from app.services.vlm_service import VLMService
from app.storage.evidence_client import evidence_client


@pytest.fixture
def sample_test_image_bytes() -> bytes:
    """Generates valid in-memory JPEG bytes representing equipment."""
    img = np.ones((640, 640, 3), dtype=np.uint8) * 160
    cv2.rectangle(img, (100, 100), (250, 400), (0, 0, 200), -1)  # Red box (fire extinguisher)
    cv2.rectangle(img, (350, 100), (550, 300), (0, 200, 0), -1)  # Green box (exit sign)
    success, enc = cv2.imencode(".jpg", img)
    assert success
    return enc.tobytes()


@pytest.fixture
def valid_mock_payload() -> dict:
    """Returns valid structured dictionary matching FireSphere perception taxonomy."""
    return {
        "isInconclusive": False,
        "inconclusiveReason": None,
        "observations": [
            {
                "objectCategory": "FIRE_EXTINGUISHER",
                "potentialHazardCategory": "FIRE_EXTINGUISHER_BLOCKED",
                "observationDescription": "Portable fire extinguisher mounted to bracket with storage boxes within 0.5m.",
                "evidenceStatus": "DETECTED",
                "box_2d": [156, 156, 625, 390],
                "confidence": 0.89,
                "uncertaintyOrLimitations": ["Partial shadow along floor line"],
                "severityProposal": "MEDIUM",
            },
            {
                "objectCategory": "EXIT_SIGN",
                "potentialHazardCategory": "NONE",
                "observationDescription": "Illuminated exit sign mounted above door frame, unobstructed.",
                "evidenceStatus": "DETECTED",
                "box_2d": [156, 546, 468, 859],
                "confidence": 0.95,
                "uncertaintyOrLimitations": [],
                "severityProposal": "INFORMATIONAL",
            },
        ],
    }


def create_mock_openai_response(payload: dict) -> MagicMock:
    """Creates a mock OpenAI chat completion response object."""
    mock_resp = MagicMock()
    mock_choice = MagicMock()
    mock_choice.message.content = json.dumps(payload)
    mock_choice.finish_reason = "stop"
    mock_resp.choices = [mock_choice]
    mock_resp.model = "gpt-4o-mini"
    mock_resp.id = "chatcmpl-test-uuid"
    return mock_resp


# ==============================================================================
# A. PROVIDER INTERFACE COMPATIBILITY
# ==============================================================================

def test_openai_provider_interface_compatibility():
    """Requirement A: Verifies provider implements BaseVLMProvider and exposes standard methods."""
    provider = OpenAIProvider()
    assert isinstance(provider, BaseVLMProvider)
    assert provider.provider_name == "openai"
    assert hasattr(provider, "is_enabled")
    assert hasattr(provider, "is_configured")
    assert hasattr(provider, "is_ready")
    assert hasattr(provider, "get_status")
    assert hasattr(provider, "get_model_id")
    assert hasattr(provider, "get_provider_info")
    assert hasattr(provider, "analyze_evidence")
    assert hasattr(provider, "analyze_image")


def test_openai_provider_info_never_leaks_secrets():
    """Requirement: Public metadata must never leak secrets or authorization headers."""
    cfg = Settings(
        openai_enabled=True,
        openai_api_key="sk-test-secret-key-super-confidential-9999",
        openai_model="gpt-4o-mini",
    )
    provider = OpenAIProvider(settings_obj=cfg)
    info = provider.get_provider_info()

    assert info["provider"] == "openai"
    assert info["model"] == "gpt-4o-mini"
    assert info["enabled"] is True
    assert "sk-test-secret-key" not in json.dumps(info)
    assert "apiKey" not in info
    assert "authorization" not in info


# ==============================================================================
# B & C. SUCCESSFUL MOCKED RESPONSE & SCHEMA MAPPING
# ==============================================================================

def test_openai_successful_mocked_response_and_schema_mapping(sample_test_image_bytes, valid_mock_payload):
    """Requirements B, C, N, O, P, Q: Verifies successful response maps cleanly into existing schema."""
    cfg = Settings(openai_enabled=True, openai_api_key="sk-test-key", openai_model="gpt-4o-mini")
    mock_client = MagicMock()
    mock_client.chat.completions.create.return_value = create_mock_openai_response(valid_mock_payload)

    provider = OpenAIProvider(settings_obj=cfg, client=mock_client)
    eid = str(uuid.uuid4())

    result = provider.analyze_evidence(evidence_id=eid, image_data=sample_test_image_bytes)

    # Status
    assert result.status == GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS
    assert len(result.observations) == 2

    # Evidence ID preservation
    assert str(result.evidenceId) == eid
    for obs in result.observations:
        assert obs.imageReference == eid
        assert obs.evidenceId == eid

    # Provenance
    assert result.model == "gpt-4o-mini"
    assert result.modelVersion == "openai-v1"
    assert result.analysisComponent == "VLM"

    # Governance invariants
    assert result.requiresOfficerVerification is True
    assert result.isSafeInference is False
    assert result.advisoryStatus == "ADVISORY_ONLY"

    # Check observations detail
    obs1 = result.observations[0]
    assert obs1.objectCategory == "FIRE_EXTINGUISHER"
    assert obs1.potentialHazardCategory == "FIRE_EXTINGUISHER_BLOCKED"
    assert obs1.boundingBox is not None
    assert obs1.confidence == 0.89
    assert obs1.severityProposal == "MEDIUM"
    assert obs1.officerReviewRequirement == "MANDATORY_OFFICER_REVIEW"
    assert obs1.verificationGuidance is not None

    obs2 = result.observations[1]
    assert obs2.objectCategory == "EXIT_SIGN"
    assert obs2.potentialHazardCategory is None
    assert obs2.confidence == 0.95


def test_openai_genuine_empty_observations(sample_test_image_bytes):
    """Requirement: Successful empty observation is distinct from failure."""
    empty_payload = {
        "isInconclusive": False,
        "inconclusiveReason": None,
        "observations": [],
    }
    cfg = Settings(openai_enabled=True, openai_api_key="sk-test-key")
    mock_client = MagicMock()
    mock_client.chat.completions.create.return_value = create_mock_openai_response(empty_payload)

    provider = OpenAIProvider(settings_obj=cfg, client=mock_client)
    eid = str(uuid.uuid4())

    result = provider.analyze_evidence(evidence_id=eid, image_data=sample_test_image_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS
    assert len(result.observations) == 0
    assert result.isSafeInference is False
    assert result.requiresOfficerVerification is True


def test_openai_inconclusive_response(sample_test_image_bytes):
    """Requirement: Model indicating insufficient evidence produces ANALYSIS_INCONCLUSIVE."""
    inconclusive_payload = {
        "isInconclusive": True,
        "inconclusiveReason": "Heavy smoke and darkness obscure fire safety equipment.",
        "observations": [],
    }
    cfg = Settings(openai_enabled=True, openai_api_key="sk-test-key")
    mock_client = MagicMock()
    mock_client.chat.completions.create.return_value = create_mock_openai_response(inconclusive_payload)

    provider = OpenAIProvider(settings_obj=cfg, client=mock_client)
    eid = str(uuid.uuid4())

    result = provider.analyze_evidence(evidence_id=eid, image_data=sample_test_image_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_INCONCLUSIVE
    assert len(result.observations) == 0
    assert result.inconclusiveReason == "Heavy smoke and darkness obscure fire safety equipment."


# ==============================================================================
# D. MISSING API KEY
# ==============================================================================

def test_openai_missing_api_key_returns_not_configured(sample_test_image_bytes):
    """Requirement D: Missing API key returns PROVIDER_NOT_CONFIGURED safely."""
    mock_cfg = MagicMock()
    mock_cfg.openai_enabled = True
    mock_cfg.openai_api_key = None
    mock_cfg.openai_model = "gpt-4o-mini"
    mock_cfg.openai_timeout_seconds = 30
    mock_cfg.openai_max_retries = 3
    mock_cfg.openai_structured_outputs = True

    provider = OpenAIProvider(settings_obj=mock_cfg)
    eid = str(uuid.uuid4())

    result = provider.analyze_evidence(evidence_id=eid, image_data=sample_test_image_bytes)

    assert result.status == GeminiAnalysisStatus.PROVIDER_NOT_CONFIGURED
    assert len(result.observations) == 0
    assert "not configured" in result.error.lower()
    assert result.requiresOfficerVerification is True


def test_openai_disabled_by_default_returns_disabled(sample_test_image_bytes):
    """Requirement: Provider disabled returns PROVIDER_DISABLED without network attempt."""
    cfg = Settings(openai_enabled=False)
    provider = OpenAIProvider(settings_obj=cfg)
    eid = str(uuid.uuid4())

    result = provider.analyze_evidence(evidence_id=eid, image_data=sample_test_image_bytes)

    assert result.status == GeminiAnalysisStatus.PROVIDER_DISABLED
    assert len(result.observations) == 0
    assert "disabled" in result.error.lower()


# ==============================================================================
# E. INVALID IMAGE INPUT
# ==============================================================================

def test_openai_invalid_image_corrupted_payload_fails_safely():
    """Requirement E, M: Corrupted or unreadable image bytes fail safely without fabricating observations."""
    cfg = Settings(openai_enabled=True, openai_api_key="sk-test-key")
    provider = OpenAIProvider(settings_obj=cfg, client=MagicMock())
    eid = str(uuid.uuid4())

    result = provider.analyze_evidence(evidence_id=eid, image_data=b"corrupted_non_image_bytes")

    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert len(result.observations) == 0
    assert result.error is not None
    assert "image validation rejected" in result.error.lower()


def test_openai_sub_resolution_image_rejected():
    """Requirement E: Images below 64x64 minimum resolution are rejected."""
    tiny_img = np.zeros((32, 32, 3), dtype=np.uint8)
    success, enc = cv2.imencode(".jpg", tiny_img)
    assert success

    cfg = Settings(openai_enabled=True, openai_api_key="sk-test-key")
    provider = OpenAIProvider(settings_obj=cfg, client=MagicMock())
    eid = str(uuid.uuid4())

    result = provider.analyze_evidence(evidence_id=eid, image_data=enc.tobytes())

    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert len(result.observations) == 0
    assert "image validation rejected" in result.error.lower()


# ==============================================================================
# F & G. MALFORMED STRUCTURED OUTPUT & SCHEMA-VALIDATION FAILURE
# ==============================================================================

def test_openai_malformed_json_returns_invalid_response(sample_test_image_bytes):
    """Requirement F: Non-JSON raw text returns ANALYSIS_INVALID_RESPONSE."""
    cfg = Settings(openai_enabled=True, openai_api_key="sk-test-key")
    mock_client = MagicMock()
    mock_resp = MagicMock()
    mock_resp.choices = [MagicMock(message=MagicMock(content="I cannot provide a JSON response right now."))]
    mock_client.chat.completions.create.return_value = mock_resp

    provider = OpenAIProvider(settings_obj=cfg, client=mock_client)
    eid = str(uuid.uuid4())

    result = provider.analyze_evidence(evidence_id=eid, image_data=sample_test_image_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_INVALID_RESPONSE
    assert len(result.observations) == 0
    assert "failed schema validation" in result.error.lower()


def test_openai_schema_validation_failure(sample_test_image_bytes):
    """Requirement G: Response with invalid types or missing required fields returns ANALYSIS_INVALID_RESPONSE."""
    invalid_schema_json = '{"observations": "invalid_string_not_list"}'
    cfg = Settings(openai_enabled=True, openai_api_key="sk-test-key")
    mock_client = MagicMock()
    mock_resp = MagicMock()
    mock_resp.choices = [MagicMock(message=MagicMock(content=invalid_schema_json))]
    mock_client.chat.completions.create.return_value = mock_resp

    provider = OpenAIProvider(settings_obj=cfg, client=mock_client)
    eid = str(uuid.uuid4())

    result = provider.analyze_evidence(evidence_id=eid, image_data=sample_test_image_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_INVALID_RESPONSE
    assert len(result.observations) == 0



def test_openai_markdown_fence_stripped_gracefully(sample_test_image_bytes, valid_mock_payload):
    """Requirement F: Markdown fences (```json ... ```) are stripped and parsed cleanly."""
    fenced_content = f"```json\n{json.dumps(valid_mock_payload)}\n```"
    cfg = Settings(openai_enabled=True, openai_api_key="sk-test-key")
    mock_client = MagicMock()
    mock_resp = MagicMock()
    mock_resp.choices = [MagicMock(message=MagicMock(content=fenced_content))]
    mock_client.chat.completions.create.return_value = mock_resp

    provider = OpenAIProvider(settings_obj=cfg, client=mock_client)
    eid = str(uuid.uuid4())

    result = provider.analyze_evidence(evidence_id=eid, image_data=sample_test_image_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS
    assert len(result.observations) == 2


# ==============================================================================
# H, I, J, K, L. ERRORS, RETRIES, AND BOUNDED RETRIES
# ==============================================================================

def test_openai_connection_failure_fails_safely(sample_test_image_bytes):
    """Requirement H, M: Connection failure exhausts retries and returns ANALYSIS_FAILED without fabricating findings."""
    cfg = Settings(openai_enabled=True, openai_api_key="sk-test-key", openai_max_retries=2)
    mock_client = MagicMock()
    mock_client.chat.completions.create.side_effect = httpx.ConnectError("Failed to establish connection to api.openai.com")

    provider = OpenAIProvider(settings_obj=cfg, client=mock_client)
    eid = str(uuid.uuid4())

    with patch("time.sleep", return_value=None):
        result = provider.analyze_evidence(evidence_id=eid, image_data=sample_test_image_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert len(result.observations) == 0
    assert "failed" in result.error.lower()
    assert mock_client.chat.completions.create.call_count == 2


def test_openai_timeout_fails_safely(sample_test_image_bytes):
    """Requirement I: Timeout exhausts retries and returns ANALYSIS_FAILED."""
    cfg = Settings(openai_enabled=True, openai_api_key="sk-test-key", openai_max_retries=2)
    mock_client = MagicMock()
    mock_client.chat.completions.create.side_effect = httpx.ReadTimeout("Request timed out after 30s")

    provider = OpenAIProvider(settings_obj=cfg, client=mock_client)
    eid = str(uuid.uuid4())

    with patch("time.sleep", return_value=None):
        result = provider.analyze_evidence(evidence_id=eid, image_data=sample_test_image_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert len(result.observations) == 0
    assert mock_client.chat.completions.create.call_count == 2


def test_openai_http_429_rate_limit_retries_and_exhaustion(sample_test_image_bytes):
    """Requirement J, L: HTTP 429 rate limit triggers bounded retries up to max_retries."""
    cfg = Settings(openai_enabled=True, openai_api_key="sk-test-key", openai_max_retries=3)
    mock_client = MagicMock()
    resp_429 = httpx.Response(status_code=429, request=httpx.Request("POST", "https://api.openai.com/v1/chat/completions"))
    mock_client.chat.completions.create.side_effect = httpx.HTTPStatusError("HTTP 429 Too Many Requests", request=resp_429.request, response=resp_429)

    provider = OpenAIProvider(settings_obj=cfg, client=mock_client)
    eid = str(uuid.uuid4())

    with patch("time.sleep", return_value=None):
        result = provider.analyze_evidence(evidence_id=eid, image_data=sample_test_image_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert len(result.observations) == 0
    assert mock_client.chat.completions.create.call_count == 3


def test_openai_http_500_or_503_fails_safely(sample_test_image_bytes):
    """Requirement K: HTTP 503 Service Unavailable triggers retries and fails safely."""
    cfg = Settings(openai_enabled=True, openai_api_key="sk-test-key", openai_max_retries=2)
    mock_client = MagicMock()
    resp_503 = httpx.Response(status_code=503, request=httpx.Request("POST", "https://api.openai.com/v1/chat/completions"))
    mock_client.chat.completions.create.side_effect = httpx.HTTPStatusError("HTTP 503 Service Unavailable", request=resp_503.request, response=resp_503)

    provider = OpenAIProvider(settings_obj=cfg, client=mock_client)
    eid = str(uuid.uuid4())

    with patch("time.sleep", return_value=None):
        result = provider.analyze_evidence(evidence_id=eid, image_data=sample_test_image_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert len(result.observations) == 0
    assert mock_client.chat.completions.create.call_count == 2


def test_openai_non_retryable_401_aborts_retries_immediately(sample_test_image_bytes):
    """Requirement L: Permanent HTTP 401 Unauthorized error aborts retries immediately (call count = 1)."""
    cfg = Settings(openai_enabled=True, openai_api_key="sk-invalid-key", openai_max_retries=3)
    mock_client = MagicMock()
    resp_401 = httpx.Response(status_code=401, request=httpx.Request("POST", "https://api.openai.com/v1/chat/completions"))
    mock_client.chat.completions.create.side_effect = httpx.HTTPStatusError("HTTP 401 Unauthorized", request=resp_401.request, response=resp_401)

    provider = OpenAIProvider(settings_obj=cfg, client=mock_client)
    eid = str(uuid.uuid4())

    with patch("time.sleep", return_value=None) as mock_sleep:
        result = provider.analyze_evidence(evidence_id=eid, image_data=sample_test_image_bytes)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert mock_client.chat.completions.create.call_count == 1
    assert mock_sleep.call_count == 0  # No retry delay on permanent error


# ==============================================================================
# R. CV APPROVAL & CALIBRATION GATES UNCHANGED
# ==============================================================================

def test_cv_gates_and_calibration_safeguards_remain_untouched():
    """Requirement R: Phase 11B OpenAI adapter does NOT alter CV detector approval or calibration states."""
    from app.models.model_approval_gate import ModelApprovalGate, ModelApprovalStatus

    assert ModelApprovalGate.is_production_approved() is False
    perm = ModelApprovalGate.check_inference_permission(test_mode=False)
    assert perm["authorized"] is False
    assert perm["modelApprovalStatus"] == ModelApprovalStatus.NOT_APPROVED.value
    assert perm["calibrationStatus"] == "NOT_CALIBRATED"


# ==============================================================================
# INTEGRATION: VLM SERVICE & INSPECTION ORCHESTRATOR
# ==============================================================================

def test_vlm_service_with_openai_provider(sample_test_image_bytes, valid_mock_payload):
    """Verifies that VLMService works seamlessly when injected with OpenAI provider."""
    cfg = Settings(openai_enabled=True, openai_api_key="sk-test-key", openai_model="gpt-4o-mini")
    mock_client = MagicMock()
    mock_client.chat.completions.create.return_value = create_mock_openai_response(valid_mock_payload)

    provider = OpenAIProvider(settings_obj=cfg, client=mock_client)
    service = VLMService(provider=provider)

    eid = uuid.uuid4()
    evidence_client.register_test_evidence(eid, sample_test_image_bytes)

    result = service.analyze_evidence(evidence_id=eid)

    assert result.status == GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS
    assert len(result.observations) == 2
    assert result.model == "gpt-4o-mini"
    assert result.modelVersion == "openai-v1"
    assert result.analysisComponent == "VLM"
    assert result.advisoryStatus == "ADVISORY_ONLY"
    assert result.requiresOfficerVerification is True

    # Test status method
    status_info = service.get_status()
    assert status_info["service"] == "firesphere-vlm-service"
    assert status_info["provider"] == "openai"
    assert status_info["model"] == "gpt-4o-mini"


def test_inspection_orchestrator_with_openai_provider(sample_test_image_bytes, valid_mock_payload):
    """Verifies that InspectionOrchestrator works seamlessly with OpenAI VLM provider."""
    cfg = Settings(openai_enabled=True, openai_api_key="sk-test-key", openai_model="gpt-4o-mini")
    mock_client = MagicMock()
    mock_client.chat.completions.create.return_value = create_mock_openai_response(valid_mock_payload)

    provider = OpenAIProvider(settings_obj=cfg, client=mock_client)
    orchestrator = InspectionOrchestrator(vlm_provider=provider)

    eid = uuid.uuid4()
    insp_id = uuid.uuid4()
    evidence_client.register_test_evidence(eid, sample_test_image_bytes)

    run = orchestrator.execute_run(
        inspection_id=insp_id,
        evidence_ids=[eid],
        test_mode=True,
    )

    assert run.status.value in ("COMPLETED", "PARTIAL")
    assert len(run.findings) >= 1
    # Check provenance and governance invariants on findings
    for finding in run.findings:
        assert finding.complianceStatus.value == "REQUIRES_OFFICER_VERIFICATION"
        for prov in finding.provenance:
            if prov.get("providerName") == "openai":
                assert prov.get("modelId") == "gpt-4o-mini"

