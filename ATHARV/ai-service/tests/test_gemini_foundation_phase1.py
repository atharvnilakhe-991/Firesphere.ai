"""Phase 1 Foundation Tests for Google Gemini VLM Integration.

Verifies:
1. Gemini is disabled by default (fail-closed).
2. Clean application import and startup without Gemini credentials or network access.
3. Explicit enablement without credentials produces clear, sanitized configuration error.
4. Valid configuration parsing and configurable model IDs.
5. Secrets and API keys are strictly protected and never exposed in info, logs, or error payloads.
6. Client lifecycle can be safely mocked for offline testing.
7. Future-proof observation schema and interface boundaries.
8. Preservation of existing CV model approval gate and prohibition of autonomous Fire NOC decisions.
"""

from unittest.mock import MagicMock, patch
import numpy as np
import pytest
from fastapi.testclient import TestClient


from app.core.config import Settings
from app.core.exceptions import GeminiConfigurationException, GeminiProviderException
from app.main import app
from app.models.cv_model_adapter import CVModelAdapter
from app.models.gemini_provider import (
    GeminiAnalysisResult,
    GeminiObservation,
    GeminiProvider,
    gemini_provider,
)
from app.models.model_approval_gate import model_approval_gate
from app.schemas.finding import BoundingBox, Finding, FindingSeverity


def test_gemini_disabled_by_default():
    """Requirement: Gemini must be disabled by default and fail closed."""
    default_settings = Settings()
    assert default_settings.gemini_enabled is False
    assert default_settings.gemini_api_key is None
    assert default_settings.gemini_model == "gemini-2.5-flash"
    assert default_settings.gemini_timeout_seconds == 30
    assert default_settings.gemini_max_retries == 3

    provider = GeminiProvider(default_settings)
    assert provider.is_enabled() is False
    assert provider.is_configured() is False
    assert provider.is_ready() is False
    assert provider.get_status() == "DISABLED"


def test_app_imports_and_serves_health_without_credentials():
    """Requirement: The service starts with Gemini disabled without network calls or credentials."""
    client = TestClient(app)

    # Liveness probe
    health_resp = client.get("/health")
    assert health_resp.status_code == 200
    assert health_resp.json()["status"] == "HEALTHY"

    # Readiness probe
    ready_resp = client.get("/ready")
    assert ready_resp.status_code == 200
    data = ready_resp.json()
    assert data["status"] == "READY"
    assert "geminiProvider" in data["components"]
    assert data["components"]["geminiProvider"] == "DISABLED"
    assert data["components"]["modelRunner"] == "READY"


def test_enabled_without_credentials_raises_configuration_error():
    """Requirement: If explicitly enabled without credentials, return a clear error without leaking secrets."""
    with pytest.raises(ValueError) as exc_info:
        Settings(gemini_enabled=True, gemini_api_key=None)

    error_msg = str(exc_info.value)
    assert "GEMINI_ENABLED is set to true" in error_msg
    assert "GEMINI_API_KEY is missing or empty" in error_msg

    # Also test empty/whitespace string
    with pytest.raises(ValueError):
        Settings(gemini_enabled=True, gemini_api_key="   ")


def test_provider_initialization_with_missing_key():
    """Requirement: Provider initialize() enforces credential check when enabled."""
    mock_settings = MagicMock()
    mock_settings.gemini_enabled = True
    mock_settings.gemini_api_key = None
    mock_settings.gemini_model = "gemini-2.5-flash"
    mock_settings.gemini_timeout_seconds = 30
    mock_settings.gemini_max_retries = 3

    provider = GeminiProvider(mock_settings)
    assert provider.is_enabled() is True
    assert provider.is_configured() is False
    assert provider.get_status() == "NOT_CONFIGURED"

    with pytest.raises(GeminiConfigurationException) as exc_info:
        provider.initialize()

    assert "GEMINI_API_KEY is missing or empty" in exc_info.value.message


def test_valid_configuration_and_model_id_selection():
    """Requirement: Model ID and parameters must be configurable."""
    cfg = Settings(
        gemini_enabled=True,
        gemini_api_key="test-mock-secret-key-9999",
        gemini_model="gemini-2.5-pro",
        gemini_timeout_seconds=60,
        gemini_max_retries=5,
    )

    assert cfg.gemini_enabled is True
    assert cfg.gemini_model == "gemini-2.5-pro"
    assert cfg.gemini_timeout_seconds == 60
    assert cfg.gemini_max_retries == 5

    provider = GeminiProvider(cfg)
    assert provider.get_model_id() == "gemini-2.5-pro"
    assert provider.is_configured() is True


def test_secrets_redacted_in_provider_metadata():
    """Requirement: Never log or expose API keys or secrets."""
    secret_key = "mock-secret-key-never-expose-in-logs"
    cfg = Settings(
        gemini_enabled=True,
        gemini_api_key=secret_key,
        gemini_model="gemini-2.5-flash",
    )
    provider = GeminiProvider(cfg)

    info = provider.get_provider_info()
    serialized = str(info)

    # API key MUST NOT appear anywhere in the provider info payload
    assert secret_key not in serialized
    assert "provider" in info
    assert info["model"] == "gemini-2.5-flash"
    assert info["governancePolicy"] == "ADVISORY_ONLY_HUMAN_OFFICER_VERIFICATION_MANDATORY"


def test_mocked_client_initialization_offline():
    """Requirement: Tests do not require real API key or live network access."""
    mock_client = MagicMock()
    cfg = Settings(
        gemini_enabled=True,
        gemini_api_key="mock-api-key-safe",
        gemini_model="gemini-2.5-flash",
    )

    provider = GeminiProvider(cfg, client=mock_client)
    assert provider.is_ready() is True
    assert provider.get_status() == "READY"
    assert provider.get_client() is mock_client

    # Verify initialize returns the injected client without network calls
    res = provider.initialize()
    assert res is mock_client


def test_client_initialization_error_handling_sanitizes_secrets():
    """Requirement: Client initialization errors are sanitized without leaking secrets."""
    cfg = Settings(
        gemini_enabled=True,
        gemini_api_key="secret-sensitive-token",
        gemini_model="gemini-2.5-flash",
    )
    provider = GeminiProvider(cfg)

    with patch("app.models.gemini_provider.genai.Client", side_effect=RuntimeError("Internal SDK failure secret-sensitive-token")):
        with pytest.raises(GeminiProviderException) as exc_info:
            provider.initialize()

        # The exception message must NOT include the raw secret
        assert "secret-sensitive-token" not in exc_info.value.message
        assert "RuntimeError" in exc_info.value.message


def test_future_proof_interface_boundaries():
    """Requirement: Future-proof interface prepared; Phase 1 enforces strict boundaries."""
    # 1. When disabled, returns clean DISABLED result envelope
    cfg_disabled = Settings(gemini_enabled=False)
    provider_disabled = GeminiProvider(cfg_disabled)
    res = provider_disabled.analyze_evidence(evidence_id="ev-100", image_data=b"test-bytes")

    assert isinstance(res, GeminiAnalysisResult)
    assert res.status == "DISABLED"
    assert res.observations == []
    assert res.requiresOfficerVerification is True

    # 2. When enabled with invalid payload, Phase 2 fails safely without fabricating data
    cfg_enabled = Settings(gemini_enabled=True, gemini_api_key="mock-key")
    provider_enabled = GeminiProvider(cfg_enabled)
    res_invalid = provider_enabled.analyze_evidence(evidence_id="ev-100", image_data=b"test-bytes")
    assert res_invalid.status == "ANALYSIS_FAILED"
    assert res_invalid.observations == []


    # 3. Observation schema contract validation
    obs = GeminiObservation(
        imageReference="ev-100",
        objectCategory="FIRE_EXTINGUISHER",
        potentialHazardCategory="FIRE_EXTINGUISHER_BLOCKED",
        observationDescription="Fire extinguisher observed behind cardboard storage boxes.",
        boundingBox=BoundingBox(xMin=0.1, yMin=0.2, xMax=0.5, yMax=0.8),
        modelName="gemini-2.5-flash",
        uncertaintyOrLimitations=["Partial optical occlusion by pallet"],
    )
    assert obs.objectCategory == "FIRE_EXTINGUISHER"
    assert obs.officerReviewRequirement == "MANDATORY_OFFICER_REVIEW"


def test_existing_cv_model_approval_gate_unaltered():
    """Requirement: Existing CV model approval gate remains intact and NOT_APPROVED."""
    # Baseline model check
    assert model_approval_gate.is_production_approved() is False
    perm = model_approval_gate.check_inference_permission(test_mode=False)
    assert perm["authorized"] is False
    assert perm["modelApprovalStatus"] == "NOT_APPROVED"

    # Adapter fail-closed execution
    adapter = CVModelAdapter()
    dummy_array = np.zeros((640, 640, 3), dtype=np.float32)
    dets = adapter.predict(evidence_id="ev-999", preprocessed_image=dummy_array, test_mode=False)
    assert dets == []



def test_prohibition_of_autonomous_fire_noc_decisions():
    """Requirement: AI must never autonomously issue AI_APPROVED or AI_REJECTED Fire NOC decisions."""
    with pytest.raises(ValueError) as exc_info:
        Finding(
            evidenceId="a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
            findingType="FIRE_EXTINGUISHER_MISSING",
            confidence=0.95,
            severity=FindingSeverity.HIGH,
            explanation="Test finding",
            model="gemini-2.5-flash",
            modelVersion="gemini-genai-v1",
            complianceStatus="AI_APPROVED",
        )

    assert "AI cannot autonomously approve or reject" in str(exc_info.value)
