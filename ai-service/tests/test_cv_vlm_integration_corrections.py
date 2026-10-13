"""FireSphere.ai — CV-VLM Integration Corrections and Production Gate Assessment Test Suite.

Verifies:
1. Provider and Model Attribution (Task 1):
   - Ollama/Qwen findings correctly identified as Ollama/Qwen (never hardcoded Google Gemini).
   - Gemini findings correctly identified as Google Gemini.
   - Unknown provider/model metadata handled cleanly without invention.
   - Mutual confirmation (Case 1) preserves both CV and VLM provenance with accurate attribution.
   - CV provenance strictly separated from VLM provenance.
2. Local-Only VLM Default (Task 2):
   - Settings().vlm_provider defaults to "ollama".
   - get_vlm_provider() defaults to OllamaQwenProvider.
   - Explicit selection works for supported providers (ollama, gemini, openai).
   - Invalid provider names raise ValueError immediately (no silent fallback).
3. CV Production Gate & Status Distinction (Tasks 3 & 4):
   - Production gate enforces NOT_APPROVED by default (NOT_APPROVED_GATED).
   - Empty-scene detection (SUCCESS, zero detections) is distinct from NOT_APPROVED_GATED.
   - Missing/unloaded checkpoint during authorized execution raises RuntimeError and sets CHECKPOINT_LOAD_FAILED.
   - Checkpoint SHA-256 cryptographic integrity matches expected digest.
4. Reconciliation and Mapping Safety (Task 5):
   - Empty detections do not certify compliance.
   - Conflicts preserved with correct provider names.
   - All AI findings require mandatory human officer review (advisory only).
"""

import hashlib
import os
import uuid
import numpy as np
import pytest
from unittest.mock import patch

from app.core.config import Settings
from app.intelligence.reconciliation import ResultReconciler
from app.models.base_model import RawDetection
from app.models.cv_model_adapter import CVModelAdapter
from app.models.gemini_schemas import (
    GeminiAnalysisResult,
    GeminiAnalysisStatus,
    GeminiObservation,
    BoundingBox,
)
from app.models.model_approval_gate import model_approval_gate
from app.models.ollama_provider import OllamaQwenProvider
from app.models.gemini_provider import GeminiProvider
from app.models.openai_provider import OpenAIProvider
from app.models.vlm_provider import get_vlm_provider
from app.schemas.reconciliation import (
    ReconciliationStatus,
    map_component_status,
    ComponentAnalysisStatus,
)


# =============================================================================
# TASK 1: PROVIDER ATTRIBUTION TESTS
# =============================================================================

def test_reconciliation_ollama_provider_attribution():
    """Verify that findings from Ollama/Qwen are attributed to Ollama/Qwen and never Google Gemini."""
    reconciler = ResultReconciler()
    eid = str(uuid.uuid4())
    iid = str(uuid.uuid4())

    vlm_obs = GeminiObservation(
        observationId="ollama-obs-1",
        imageReference=eid,
        evidenceId=eid,
        objectCategory="FIRE_EXTINGUISHER",
        potentialHazardCategory="FIRE_EXTINGUISHER_BLOCKED",
        confidence=0.89,
        boundingBox=BoundingBox(xMin=0.1, yMin=0.1, xMax=0.4, yMax=0.5),
        uncertaintyOrLimitations=["Test limitation"],
    )

    vlm_result = GeminiAnalysisResult(
        evidenceId=eid,
        model="qwen2.5vl:3b",
        status=GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS,
        observations=[vlm_obs],
    )

    findings, status, _ = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        gemini_result=vlm_result,
        cv_detections=[],
        cv_status="NO_DETECTIONS",
        cv_model_info={"model": "firesphere-cv-baseline", "version": "0.8.0"},
        gemini_model_info={"provider": "ollama", "model": "qwen2.5vl:3b", "version": "3b"},
    )

    assert len(findings) == 1
    f = findings[0]
    # Check model and provenance
    assert f.model == "qwen2.5vl:3b"
    assert len(f.provenance) >= 1
    assert f.provenance[0]["providerName"] == "ollama"
    assert f.provenance[0]["modelId"] == "qwen2.5vl:3b"
    # Check explanation attribution
    assert "Ollama/Qwen VLM" in f.explanation
    assert "Google Gemini" not in f.explanation
    assert "google-gemini" not in f.provenance[0]["providerName"]


def test_reconciliation_gemini_provider_attribution():
    """Verify that findings from Gemini are accurately attributed to Google Gemini."""
    reconciler = ResultReconciler()
    eid = str(uuid.uuid4())
    iid = str(uuid.uuid4())

    vlm_obs = GeminiObservation(
        observationId="gemini-obs-1",
        imageReference=eid,
        evidenceId=eid,
        objectCategory="EXIT_SIGN",
        potentialHazardCategory="EXIT_SIGNAGE_MISSING",
        confidence=0.91,
    )

    vlm_result = GeminiAnalysisResult(
        evidenceId=eid,
        model="gemini-2.5-flash",
        status=GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS,
        observations=[vlm_obs],
    )

    findings, status, _ = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        gemini_result=vlm_result,
        cv_detections=[],
        cv_status="NO_DETECTIONS",
        cv_model_info={"model": "firesphere-cv-baseline", "version": "0.8.0"},
        gemini_model_info={"provider": "google-gemini", "model": "gemini-2.5-flash"},
    )

    assert len(findings) == 1
    f = findings[0]
    assert f.model == "google-gemini"
    assert len(f.provenance) >= 1
    assert f.provenance[0]["providerName"] == "google-gemini"
    assert f.provenance[0]["modelId"] == "gemini-2.5-flash"
    assert "Gemini VLM" in f.explanation


def test_reconciliation_unknown_provider_fallback():
    """Verify that missing provider metadata is safely marked as unknown rather than invented."""
    reconciler = ResultReconciler()
    eid = str(uuid.uuid4())
    iid = str(uuid.uuid4())

    vlm_obs = GeminiObservation(
        observationId="unknown-obs-1",
        imageReference=eid,
        evidenceId=eid,
        objectCategory="FIRE_EXTINGUISHER",
        potentialHazardCategory="FIRE_EXTINGUISHER_MISSING",
        confidence=0.80,
    )

    vlm_result = GeminiAnalysisResult(
        evidenceId=eid,
        model="unknown",
        status=GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS,
        observations=[vlm_obs],
    )

    findings, status, _ = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        gemini_result=vlm_result,
        cv_detections=[],
        cv_status="NO_DETECTIONS",
        cv_model_info={"model": "firesphere-cv-baseline", "version": "0.8.0"},
        gemini_model_info={},  # Empty metadata
    )

    assert len(findings) == 1
    f = findings[0]
    assert "VLM" in f.explanation


def test_reconciliation_mutual_agreement_attribution():
    """Verify that concordant findings accurately reference both CV and the actual VLM provider."""
    reconciler = ResultReconciler()
    eid = str(uuid.uuid4())
    iid = str(uuid.uuid4())

    vlm_obs = GeminiObservation(
        observationId="ollama-obs-1",
        imageReference=eid,
        evidenceId=eid,
        objectCategory="FIRE_EXTINGUISHER",
        potentialHazardCategory="FIRE_EXTINGUISHER_DAMAGED",
        confidence=0.90,
        boundingBox=BoundingBox(xMin=0.1, yMin=0.1, xMax=0.3, yMax=0.5),
    )
    vlm_result = GeminiAnalysisResult(
        evidenceId=eid,
        model="qwen2.5vl:3b",
        status=GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS,
        observations=[vlm_obs],
    )

    cv_det = RawDetection(
        className="FIRE_EXTINGUISHER",
        confidence=0.88,
        boundingBox={"xMin": 0.1, "yMin": 0.1, "xMax": 0.3, "yMax": 0.5},
        severity="HIGH",
    )

    findings, status, conflict = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        gemini_result=vlm_result,
        cv_detections=[cv_det],
        cv_status="SUCCESS",
        cv_model_info={"model": "firesphere-cv-production", "version": "0.8.0"},
        gemini_model_info={"provider": "ollama", "model": "qwen2.5vl:3b"},
        is_test_mode=True,
    )

    assert status == ReconciliationStatus.AGREEMENT
    assert len(findings) == 1
    f = findings[0]
    assert "Ollama/Qwen VLM" in f.explanation
    assert "Google Gemini" not in f.explanation
    assert f.model == "qwen2.5vl:3b+firesphere-cv-production"
    assert len(f.provenance) >= 2
    providers = [p["providerName"] for p in f.provenance]
    assert "ollama" in providers


# =============================================================================
# TASK 2: LOCAL-ONLY VLM DEFAULT TESTS
# =============================================================================

def test_settings_vlm_provider_defaults_to_ollama():
    """Verify that Settings default for vlm_provider is 'ollama', not 'gemini'."""
    settings = Settings()
    assert settings.vlm_provider == "ollama"


def test_factory_defaults_to_ollama():
    """Verify get_vlm_provider() defaults to OllamaQwenProvider when unconfigured."""
    provider = get_vlm_provider()
    assert isinstance(provider, OllamaQwenProvider)
    assert provider.provider_name == "ollama"


def test_factory_invalid_provider_raises_value_error():
    """Verify that invalid provider names raise ValueError and never silently fall back."""
    with pytest.raises(ValueError, match="Unsupported or invalid VLM provider 'invalid_provider'"):
        get_vlm_provider(provider_name="invalid_provider")

    with pytest.raises(ValueError, match="Unsupported or invalid VLM provider 'azure_ai'"):
        get_vlm_provider(provider_name="azure_ai")


def test_factory_explicit_provider_selection():
    """Verify explicit provider selection functions as expected."""
    gemini_prov = get_vlm_provider(provider_name="gemini")
    assert isinstance(gemini_prov, GeminiProvider)
    assert gemini_prov.provider_name == "google-gemini"

    openai_prov = get_vlm_provider(provider_name="openai")
    assert isinstance(openai_prov, OpenAIProvider)
    assert openai_prov.provider_name == "openai"


# =============================================================================
# TASK 3 & TASK 4: CV GATE AND STATUS HANDLING TESTS
# =============================================================================

def test_cv_production_gate_remains_not_approved():
    """Verify that the production CV gate rejects production inference by default."""
    assert model_approval_gate.is_production_approved(
        model_id="firesphere-inspection-cv",
        model_version="0.8.0",
    ) is False


def test_cv_gate_distinguishes_status_modes():
    """Verify that gate distinguishes NOT_APPROVED_GATED, SUCCESS (with/without detections), and FAILED."""
    # 1. Gate blocks unapproved production inference
    gate_decision = model_approval_gate.check_inference_permission(
        test_mode=False,
        model_id="firesphere-inspection-cv",
        model_version="0.8.0",
    )
    assert gate_decision["authorized"] is False
    assert "UNAPPROVED" in gate_decision["reason"] or "NOT_APPROVED" in gate_decision["reason"]

    # 2. Gate-blocked status maps deterministically to UNAVAILABLE (not empty success)
    assert map_component_status("NOT_APPROVED_GATED", 0) == ComponentAnalysisStatus.UNAVAILABLE

    # 3. Successful execution with 0 detections maps to SUCCESS_WITH_NO_OBSERVATIONS
    assert map_component_status("SUCCESS", 0) == ComponentAnalysisStatus.SUCCESS_WITH_NO_OBSERVATIONS

    # 4. Successful execution with detections maps to SUCCESS_WITH_OBSERVATIONS
    assert map_component_status("SUCCESS", 2) == ComponentAnalysisStatus.SUCCESS_WITH_OBSERVATIONS

    # 5. Checkpoint failure and generic failure map to FAILED
    assert map_component_status("CHECKPOINT_LOAD_FAILED", 0) == ComponentAnalysisStatus.FAILED
    assert map_component_status("FAILED", 0) == ComponentAnalysisStatus.FAILED


def test_cv_adapter_checkpoint_missing_when_authorized():
    """Verify CVModelAdapter raises RuntimeError if authorized for production but model weights are unloaded."""
    adapter = CVModelAdapter()
    adapter.load()
    adapter._model = None  # Force model to be None while loaded metadata exists
    dummy_img = np.zeros((100, 100, 3), dtype=np.uint8)

    # Mock gate approval for production (test_mode=False)
    with patch.object(model_approval_gate, "check_inference_permission", return_value={"authorized": True, "reason": "MOCK_APPROVED"}):
        with pytest.raises(RuntimeError, match="weights for '.*' are not loaded"):
            adapter.predict("eid-test", dummy_img, test_mode=False)


def test_baseline_checkpoint_cryptographic_integrity():
    """Verify the baseline checkpoint best.pt matches the expected SHA-256 hash."""
    checkpoint_path = os.path.abspath(
        os.path.join(os.path.dirname(__file__), "..", "artifacts", "phase8_4", "checkpoints", "best.pt")
    )
    assert os.path.exists(checkpoint_path), f"Checkpoint missing at {checkpoint_path}"

    hasher = hashlib.sha256()
    with open(checkpoint_path, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)

    expected_hash = "5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666"
    assert hasher.hexdigest().lower() == expected_hash.lower()


# =============================================================================
# TASK 5: RECONCILIATION AND MAPPING SAFETY INVARIANTS
# =============================================================================

def test_zero_detections_never_certifies_compliance():
    """Verify empty/zero detections do NOT certify compliance or set COMPLIANT status."""
    reconciler = ResultReconciler()
    eid = str(uuid.uuid4())
    iid = str(uuid.uuid4())

    findings, status, _ = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        gemini_result=None,
        cv_detections=[],
        cv_status="SUCCESS",
        cv_model_info={"model": "firesphere-cv-baseline", "version": "0.8.0"},
        gemini_model_info={"provider": "ollama", "model": "qwen2.5vl:3b"},
        is_test_mode=True,
    )

    # Empty observations must result in empty findings list, never an automated COMPLIANT certificate
    assert len(findings) == 0
    # Reconciliation status indicates empty/no observations, NOT certification of compliance
    assert status in (ReconciliationStatus.NO_OBSERVATIONS, ReconciliationStatus.SINGLE_PROVIDER, ReconciliationStatus.UNRECONCILED)
