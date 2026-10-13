"""Tests for schema models, validation rules, and evaluation functions."""

import uuid
import pytest
from pydantic import ValidationError
from app.evaluation.metrics import calculate_severity, generate_explanation
from app.schemas.finding import BoundingBox, Finding, FindingSeverity, FindingType
from app.schemas.run import AIRun, RunStatus


def test_finding_valid_schema():
    """Verifies that a valid finding is successfully constructed."""
    f_id = uuid.uuid4()
    e_id = uuid.uuid4()
    finding = Finding(
        findingId=f_id,
        evidenceId=e_id,
        findingType=FindingType.FIRE_EXTINGUISHER_MISSING,
        confidence=0.88,
        severity=FindingSeverity.HIGH,
        explanation="Extinguisher absent at designated location.",
        model="firesphere-inspection-cv",
        modelVersion="0.1.0-phase1",
        boundingBox=BoundingBox(xMin=0.1, yMin=0.2, xMax=0.4, yMax=0.6),
        isTestData=True,
    )
    assert finding.findingId == f_id
    assert finding.evidenceId == e_id
    assert finding.confidence == 0.88
    assert finding.severity == FindingSeverity.HIGH
    assert finding.boundingBox.xMin == 0.1
    assert finding.isTestData is True


def test_finding_confidence_out_of_bounds():
    """Verifies that confidence values outside [0.0, 1.0] fail validation."""
    with pytest.raises(ValidationError):
        Finding(
            evidenceId=uuid.uuid4(),
            findingType="TEST",
            confidence=1.5,  # Invalid: > 1.0
            severity=FindingSeverity.HIGH,
            explanation="Invalid confidence test",
            model="test-model",
            modelVersion="0.1.0",
        )

    with pytest.raises(ValidationError):
        Finding(
            evidenceId=uuid.uuid4(),
            findingType="TEST",
            confidence=-0.1,  # Invalid: < 0.0
            severity=FindingSeverity.HIGH,
            explanation="Invalid confidence test",
            model="test-model",
            modelVersion="0.1.0",
        )


def test_bounding_box_out_of_bounds():
    """Verifies that bounding box coordinates outside [0.0, 1.0] fail validation."""
    with pytest.raises(ValidationError):
        BoundingBox(xMin=-0.1, yMin=0.0, xMax=1.0, yMax=1.0)

    with pytest.raises(ValidationError):
        BoundingBox(xMin=0.0, yMin=0.0, xMax=1.5, yMax=1.0)


def test_ai_run_valid_schema():
    """Verifies valid AIRun instance construction and default statuses."""
    r_id = uuid.uuid4()
    i_id = uuid.uuid4()
    e_id = uuid.uuid4()

    run = AIRun(
        runId=r_id,
        inspectionId=i_id,
        inputEvidence=[e_id],
        status=RunStatus.COMPLETED,
        model="firesphere-inspection-cv",
        modelVersion="0.1.0-phase1",
    )
    assert run.runId == r_id
    assert run.status == RunStatus.COMPLETED
    assert run.inputEvidence == [e_id]
    assert run.findings == []


def test_severity_calculation_and_explanation():
    """Verifies severity assignment and explainable report string generation."""
    # Critical defect with high confidence
    sev_critical = calculate_severity(FindingType.EMERGENCY_EXIT_OBSTRUCTED, 0.95)
    assert sev_critical == FindingSeverity.CRITICAL

    # Critical defect with marginal confidence drops to MEDIUM
    sev_marginal = calculate_severity(FindingType.EMERGENCY_EXIT_OBSTRUCTED, 0.55)
    assert sev_marginal == FindingSeverity.MEDIUM

    # Explanation text generation includes confidence and requirement for officer verification
    explanation = generate_explanation(FindingType.FIRE_EXTINGUISHER_MISSING, 0.90)
    assert "90.0%" in explanation
    assert "manual officer verification" in explanation.lower()
