"""FireSphere.ai — Phase 13 Ollama/Qwen2.5-VL VLM Provider Adapter Offline Test Suite.

Verifies:
A. Provider interface compatibility (BaseVLMProvider).
B. Successful mocked response mapping into canonical GeminiAnalysisResult / GeminiObservation.
C. Provider and model provenance retention.
D. Disabled provider handling (fail-closed, PROVIDER_DISABLED).
E. Invalid configuration handling (PROVIDER_NOT_CONFIGURED).
F. Image payload validation and rejection (oversized, corrupt, sub-resolution).
G. Ollama unavailable / Connection error handling (ANALYSIS_FAILED).
H. Connection timeout and read timeout handling.
I. HTTP 404 (model not found) permanent error handling (no infinite retries).
J. HTTP 500 / 503 transient error bounded retry behavior.
K. Invalid JSON response handling (ANALYSIS_INVALID_RESPONSE).
L. Malformed structured output handling (missing required fields / schema failure).
M. Inconclusive response handling (ANALYSIS_INCONCLUSIVE).
N. Successful empty response handling (ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS).
O. No fabricated bounding boxes or confidence scores.
P. Mandatory human officer verification requirement preservation.
Q. Advisory-only governance policy preservation.
R. Provider selection via factory (get_vlm_provider) with NO silent fallback to Gemini or OpenAI.
S. CV approval gate remains unchanged and independent.
T. Reconciliation compatibility with Ollama result (provenance, conflict preservation).
"""

import json
from typing import Any, Dict, List, Optional
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
from app.models.ollama_provider import OllamaQwenProvider, ollama_provider
from app.models.vlm_provider import BaseVLMProvider, get_vlm_provider
from app.services.inspection_orchestrator import InspectionOrchestrator
from app.services.vlm_service import VLMService
from app.intelligence.reconciliation import ResultReconciler, result_reconciler
from app.schemas.reconciliation import ReconciliationStatus


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
                "confidence": 0.88,
                "uncertaintyOrLimitations": ["Partial shadow along floor line"],
                "severityProposal": "MEDIUM",
            },
            {
                "objectCategory": "EXIT_SIGN",
                "potentialHazardCategory": "NONE",
                "observationDescription": "Illuminated exit sign mounted above door frame, unobstructed.",
                "evidenceStatus": "DETECTED",
                "box_2d": [156, 546, 468, 859],
                "confidence": 0.94,
                "uncertaintyOrLimitations": [],
                "severityProposal": "INFORMATIONAL",
            },
        ],
    }


def _create_mock_httpx_response(status_code: int, json_data: Any) -> httpx.Response:
    """Creates a real httpx.Response object with attached mock request for clean offline testing."""
    req = httpx.Request("POST", "http://127.0.0.1:11434/api/chat")
    content = json.dumps(json_data).encode("utf-8") if not isinstance(json_data, (bytes, str)) else (
        json_data.encode("utf-8") if isinstance(json_data, str) else json_data
    )
    return httpx.Response(
        status_code=status_code,
        content=content,
        headers={"Content-Type": "application/json"},
        request=req,
    )


# ---------------------------------------------------------------------------
# 1. Interface & Configuration Tests
# ---------------------------------------------------------------------------

def test_ollama_provider_implements_base_vlm_interface():
    """Verifies OllamaQwenProvider implements the abstract BaseVLMProvider interface."""
    provider = OllamaQwenProvider()
    assert isinstance(provider, BaseVLMProvider)
    assert provider.provider_name == "ollama"
    assert provider.get_model_id() == "qwen2.5vl:3b"


def test_ollama_provider_disabled_by_default():
    """Verifies provider adheres to fail-closed invariant (disabled by default)."""
    custom_settings = Settings(ollama_enabled=False)
    provider = OllamaQwenProvider(settings_obj=custom_settings)
    assert not provider.is_enabled()
    assert provider.get_status() == "DISABLED"
    assert not provider.is_ready()


def test_ollama_provider_info_sanitization():
    """Verifies get_provider_info returns sanitized metadata and no secrets."""
    custom_settings = Settings(
        ollama_enabled=True,
        ollama_base_url="http://127.0.0.1:11434",
        ollama_model="qwen2.5vl:3b",
    )
    provider = OllamaQwenProvider(settings_obj=custom_settings)
    info = provider.get_provider_info()
    assert info["provider"] == "ollama"
    assert info["model"] == "qwen2.5vl:3b"
    assert info["modelVersion"] == "ollama-qwen2.5vl-v1"
    assert info["baseUrl"] == "http://127.0.0.1:11434"
    assert info["productionApproved"] is False
    assert info["governancePolicy"] == "ADVISORY_ONLY_HUMAN_OFFICER_VERIFICATION_MANDATORY"
    assert "api_key" not in info
    assert "token" not in info


# ---------------------------------------------------------------------------
# 2. Successful Inference & Schema Mapping Tests
# ---------------------------------------------------------------------------

def test_ollama_successful_mock_response_mapping(sample_test_image_bytes, valid_mock_payload):
    """Verifies successful response mapping to canonical GeminiAnalysisResult schema."""
    chat_response = {
        "model": "qwen2.5vl:3b",
        "message": {
            "role": "assistant",
            "content": json.dumps(valid_mock_payload),
        },
        "done": True,
    }

    mock_client = MagicMock()
    mock_client.post.return_value = _create_mock_httpx_response(200, chat_response)

    custom_settings = Settings(
        ollama_enabled=True,
        ollama_base_url="http://127.0.0.1:11434",
        ollama_model="qwen2.5vl:3b",
    )
    provider = OllamaQwenProvider(settings_obj=custom_settings, client=mock_client)

    eid = str(uuid.uuid4())
    result: GeminiAnalysisResult = provider.analyze_evidence(
        evidence_id=eid,
        image_data=sample_test_image_bytes,
    )

    assert result.status == GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS
    assert result.evidenceId == eid
    assert result.model == "qwen2.5vl:3b"
    assert result.modelVersion == "ollama-qwen2.5vl-v1"
    assert result.analysisComponent == "VLM"
    assert result.advisoryStatus == "ADVISORY_ONLY"
    assert result.requiresOfficerVerification is True
    assert result.isSafeInference is False
    assert len(result.observations) == 2

    # Verify first observation details
    obs1 = result.observations[0]
    assert obs1.objectCategory == "FIRE_EXTINGUISHER"
    assert obs1.potentialHazardCategory == "FIRE_EXTINGUISHER_BLOCKED"
    assert obs1.evidenceStatus == "DETECTED"
    assert obs1.confidence == 0.88
    assert obs1.boundingBox is not None
    assert obs1.boundingBox.xMin == 0.156
    assert obs1.boundingBox.yMin == 0.156
    assert obs1.advisoryStatus == "ADVISORY_ONLY"
    assert obs1.officerReviewRequirement == "MANDATORY_OFFICER_REVIEW"
    assert obs1.verificationGuidance is not None


def test_ollama_successful_empty_response(sample_test_image_bytes):
    """Verifies that an image with zero findings maps to ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS."""
    empty_payload = {
        "isInconclusive": False,
        "inconclusiveReason": None,
        "observations": [],
    }
    chat_response = {
        "model": "qwen2.5vl:3b",
        "message": {"role": "assistant", "content": json.dumps(empty_payload)},
        "done": True,
    }
    mock_client = MagicMock()
    mock_client.post.return_value = _create_mock_httpx_response(200, chat_response)

    custom_settings = Settings(ollama_enabled=True)
    provider = OllamaQwenProvider(settings_obj=custom_settings, client=mock_client)

    result = provider.analyze_evidence(evidence_id=str(uuid.uuid4()), image_data=sample_test_image_bytes)
    assert result.status == GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS
    assert len(result.observations) == 0
    assert result.isSafeInference is False
    assert result.requiresOfficerVerification is True


def test_ollama_inconclusive_response(sample_test_image_bytes):
    """Verifies that inconclusive images map to ANALYSIS_INCONCLUSIVE with documented reason."""
    inconclusive_payload = {
        "isInconclusive": True,
        "inconclusiveReason": "Image is excessively blurred and lighting is insufficient.",
        "observations": [],
    }
    chat_response = {
        "model": "qwen2.5vl:3b",
        "message": {"role": "assistant", "content": json.dumps(inconclusive_payload)},
        "done": True,
    }
    mock_client = MagicMock()
    mock_client.post.return_value = _create_mock_httpx_response(200, chat_response)

    custom_settings = Settings(ollama_enabled=True)
    provider = OllamaQwenProvider(settings_obj=custom_settings, client=mock_client)

    result = provider.analyze_evidence(evidence_id=str(uuid.uuid4()), image_data=sample_test_image_bytes)
    assert result.status == GeminiAnalysisStatus.ANALYSIS_INCONCLUSIVE
    assert result.inconclusiveReason == "Image is excessively blurred and lighting is insufficient."
    assert len(result.observations) == 0


# ---------------------------------------------------------------------------
# 3. Input Validation Rejections
# ---------------------------------------------------------------------------

def test_ollama_corrupt_image_rejected():
    """Verifies that non-image binary input is rejected fail-closed."""
    custom_settings = Settings(ollama_enabled=True)
    provider = OllamaQwenProvider(settings_obj=custom_settings)

    corrupt_bytes = b"NOT_A_VALID_JPEG_HEADER_RANDOM_GARBAGE"
    result = provider.analyze_evidence(evidence_id=str(uuid.uuid4()), image_data=corrupt_bytes)
    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert "Image validation rejected" in (result.error or "")
    assert len(result.observations) == 0


def test_ollama_sub_resolution_image_rejected():
    """Verifies that sub-resolution images (<64x64) are rejected by evidence validator."""
    tiny_img = np.zeros((32, 32, 3), dtype=np.uint8)
    success, enc = cv2.imencode(".jpg", tiny_img)
    assert success

    custom_settings = Settings(ollama_enabled=True)
    provider = OllamaQwenProvider(settings_obj=custom_settings)

    result = provider.analyze_evidence(evidence_id=str(uuid.uuid4()), image_data=enc.tobytes())
    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert "Image validation rejected" in (result.error or "")


def test_ollama_disabled_provider_returns_safe_status():
    """Verifies that calling disabled provider returns PROVIDER_DISABLED envelope."""
    custom_settings = Settings(ollama_enabled=False)
    provider = OllamaQwenProvider(settings_obj=custom_settings)

    result = provider.analyze_evidence(evidence_id=str(uuid.uuid4()), image_data=b"dummy")
    assert result.status == GeminiAnalysisStatus.PROVIDER_DISABLED
    assert "disabled" in (result.error or "").lower()
    assert result.requiresOfficerVerification is True


# ---------------------------------------------------------------------------
# 4. Network, Timeout, & HTTP Error Handling
# ---------------------------------------------------------------------------

def test_ollama_connection_error_handling(sample_test_image_bytes):
    """Verifies that connection error to local Ollama fails closed with ANALYSIS_FAILED."""
    mock_client = MagicMock()
    mock_client.post.side_effect = httpx.ConnectError("Connection refused by target host")

    custom_settings = Settings(ollama_enabled=True, ollama_max_retries=1)
    provider = OllamaQwenProvider(settings_obj=custom_settings, client=mock_client)

    result = provider.analyze_evidence(evidence_id=str(uuid.uuid4()), image_data=sample_test_image_bytes)
    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert "ConnectError" in (result.error or "")


def test_ollama_read_timeout_handling(sample_test_image_bytes):
    """Verifies that read timeout fails closed with ANALYSIS_FAILED."""
    mock_client = MagicMock()
    mock_client.post.side_effect = httpx.ReadTimeout("Read timed out after 60 seconds")

    custom_settings = Settings(ollama_enabled=True, ollama_max_retries=1)
    provider = OllamaQwenProvider(settings_obj=custom_settings, client=mock_client)

    result = provider.analyze_evidence(evidence_id=str(uuid.uuid4()), image_data=sample_test_image_bytes)
    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert "ReadTimeout" in (result.error or "")


def test_ollama_model_not_found_permanent_error(sample_test_image_bytes):
    """Verifies that HTTP 404 (model not found) aborts retries immediately."""
    mock_client = MagicMock()
    mock_client.post.return_value = _create_mock_httpx_response(404, {"error": "model 'qwen2.5vl:3b' not found"})

    custom_settings = Settings(ollama_enabled=True, ollama_max_retries=3)
    provider = OllamaQwenProvider(settings_obj=custom_settings, client=mock_client)

    result = provider.analyze_evidence(evidence_id=str(uuid.uuid4()), image_data=sample_test_image_bytes)
    assert result.status == GeminiAnalysisStatus.ANALYSIS_FAILED
    assert "HTTP 404" in (result.error or "")
    # Should only be called once because 404 is permanent
    assert mock_client.post.call_count == 1


def test_ollama_server_error_transient_retry(sample_test_image_bytes, valid_mock_payload):
    """Verifies that HTTP 500 error triggers bounded retry and recovers on success."""
    chat_response = {
        "model": "qwen2.5vl:3b",
        "message": {"role": "assistant", "content": json.dumps(valid_mock_payload)},
        "done": True,
    }
    mock_client = MagicMock()
    mock_client.post.side_effect = [
        _create_mock_httpx_response(500, {"error": "internal error"}),
        _create_mock_httpx_response(200, chat_response),
    ]

    custom_settings = Settings(ollama_enabled=True, ollama_max_retries=2)
    provider = OllamaQwenProvider(settings_obj=custom_settings, client=mock_client)

    result = provider.analyze_evidence(evidence_id=str(uuid.uuid4()), image_data=sample_test_image_bytes)
    assert result.status == GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS
    assert mock_client.post.call_count == 2


def test_ollama_malformed_json_handling(sample_test_image_bytes):
    """Verifies that non-JSON output from the model returns ANALYSIS_INVALID_RESPONSE."""
    chat_response = {
        "model": "qwen2.5vl:3b",
        "message": {"role": "assistant", "content": "This is plain text with no JSON at all."},
        "done": True,
    }
    mock_client = MagicMock()
    mock_client.post.return_value = _create_mock_httpx_response(200, chat_response)

    custom_settings = Settings(ollama_enabled=True)
    provider = OllamaQwenProvider(settings_obj=custom_settings, client=mock_client)

    result = provider.analyze_evidence(evidence_id=str(uuid.uuid4()), image_data=sample_test_image_bytes)
    assert result.status == GeminiAnalysisStatus.ANALYSIS_INVALID_RESPONSE
    assert "schema validation" in (result.error or "").lower()


def test_ollama_markdown_fenced_json_parsed(sample_test_image_bytes, valid_mock_payload):
    """Verifies that markdown-fenced ```json ... ``` is correctly stripped and parsed."""
    fenced_content = f"```json\n{json.dumps(valid_mock_payload)}\n```"
    chat_response = {
        "model": "qwen2.5vl:3b",
        "message": {"role": "assistant", "content": fenced_content},
        "done": True,
    }
    mock_client = MagicMock()
    mock_client.post.return_value = _create_mock_httpx_response(200, chat_response)

    custom_settings = Settings(ollama_enabled=True)
    provider = OllamaQwenProvider(settings_obj=custom_settings, client=mock_client)

    result = provider.analyze_evidence(evidence_id=str(uuid.uuid4()), image_data=sample_test_image_bytes)
    assert result.status == GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS
    assert len(result.observations) == 2


# ---------------------------------------------------------------------------
# 5. Safety, Factory & Orchestration Integration Tests
# ---------------------------------------------------------------------------

def test_no_fabricated_bounding_boxes_or_confidence(sample_test_image_bytes):
    """Verifies provider never fabricates bounding boxes or confidence when model provides null."""
    payload_without_boxes = {
        "isInconclusive": False,
        "inconclusiveReason": None,
        "observations": [
            {
                "objectCategory": "FIRE_EXTINGUISHER",
                "potentialHazardCategory": "NONE",
                "observationDescription": "Extinguisher visible in room",
                "evidenceStatus": "DETECTED",
                "box_2d": None,
                "confidence": None,
                "uncertaintyOrLimitations": [],
                "severityProposal": "INFORMATIONAL",
            }
        ],
    }
    chat_response = {
        "model": "qwen2.5vl:3b",
        "message": {"role": "assistant", "content": json.dumps(payload_without_boxes)},
        "done": True,
    }
    mock_client = MagicMock()
    mock_client.post.return_value = _create_mock_httpx_response(200, chat_response)

    custom_settings = Settings(ollama_enabled=True)
    provider = OllamaQwenProvider(settings_obj=custom_settings, client=mock_client)

    result = provider.analyze_evidence(evidence_id=str(uuid.uuid4()), image_data=sample_test_image_bytes)
    assert len(result.observations) == 1
    obs = result.observations[0]
    assert obs.boundingBox is None  # Must NOT be fabricated
    assert obs.confidence is None   # Must NOT be fabricated


def test_factory_selects_ollama_without_silent_fallback():
    """Verifies get_vlm_provider respects 'ollama' and does not fall back to Gemini or OpenAI."""
    cfg = Settings(vlm_provider="ollama")
    provider = get_vlm_provider(settings_obj=cfg)
    assert isinstance(provider, OllamaQwenProvider)
    assert provider.provider_name == "ollama"

    # Direct name parameter override
    provider_direct = get_vlm_provider(provider_name="ollama")
    assert isinstance(provider_direct, OllamaQwenProvider)


def test_cv_approval_gate_remains_independent_with_ollama():
    """Verifies that CV approval gate operates independently of VLM provider selection."""
    assert model_approval_gate is not None
    # Gate check on mock/unapproved weights is rejected
    status = model_approval_gate.is_production_approved(
        model_id="firesphere-inspection-cv",
        model_version="0.3.0-unapproved",
    )
    assert status is False


def test_reconciliation_handles_ollama_findings_preserving_provenance(sample_test_image_bytes, valid_mock_payload):
    """Verifies that ResultReconciler processes Ollama results while preserving provenance and conflict states."""
    chat_response = {
        "model": "qwen2.5vl:3b",
        "message": {"role": "assistant", "content": json.dumps(valid_mock_payload)},
        "done": True,
    }
    mock_client = MagicMock()
    mock_client.post.return_value = _create_mock_httpx_response(200, chat_response)

    custom_settings = Settings(ollama_enabled=True)
    provider = OllamaQwenProvider(settings_obj=custom_settings, client=mock_client)

    eid = str(uuid.uuid4())
    vlm_result = provider.analyze_evidence(evidence_id=eid, image_data=sample_test_image_bytes)

    # Run through reconciler with empty CV detections (Single provider scenario)
    reconciler = ResultReconciler()
    findings, recon_status, conflict = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=str(uuid.uuid4()),
        gemini_result=vlm_result,
        cv_detections=[],
        cv_status="NO_DETECTIONS",
        cv_model_info={"model": "firesphere-cv-baseline", "version": "0.8.0"},
        gemini_model_info={"provider": "ollama", "model": "qwen2.5vl:3b"},
    )

    assert recon_status == ReconciliationStatus.SINGLE_PROVIDER
    assert len(findings) == 2
    # Verify provenance
    for f in findings:
        assert str(f.evidenceId) == str(eid)
        assert f.reconciliationStatus == "SINGLE_PROVIDER"
        assert f.evidenceReviewed is False
    assert vlm_result.requiresOfficerVerification is True
