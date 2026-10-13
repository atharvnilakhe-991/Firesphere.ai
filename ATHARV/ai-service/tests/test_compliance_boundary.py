"""Tests for strict AI compliance safety boundaries and non-autonomous regulatory decisions."""

import uuid
import pytest
from pydantic import ValidationError
from app.schemas.finding import ComplianceStatus, Finding, FindingSeverity


def test_valid_compliance_statuses():
    """Verifies that allowed advisory compliance states are accepted."""
    finding = Finding(
        findingId=uuid.uuid4(),
        evidenceId=uuid.uuid4(),
        findingType="FIRE_EXTINGUISHER_MISSING",
        confidence=0.85,
        severity=FindingSeverity.HIGH,
        explanation="Test finding",
        model="firesphere-inspection-cv",
        modelVersion="0.3.0-phase3",
        complianceStatus=ComplianceStatus.REQUIRES_OFFICER_VERIFICATION,
    )
    assert finding.complianceStatus == ComplianceStatus.REQUIRES_OFFICER_VERIFICATION

    finding2 = Finding(
        findingId=uuid.uuid4(),
        evidenceId=uuid.uuid4(),
        findingType="FIRE_EXTINGUISHER_MISSING",
        confidence=0.85,
        severity=FindingSeverity.HIGH,
        explanation="Test finding",
        model="firesphere-inspection-cv",
        modelVersion="0.3.0-phase3",
        complianceStatus=ComplianceStatus.AI_ASSISTED_ONLY,
    )
    assert finding2.complianceStatus == ComplianceStatus.AI_ASSISTED_ONLY


def test_ai_cannot_return_ai_approved():
    """Verifies that the system strictly forbids setting complianceStatus to AI_APPROVED."""
    with pytest.raises(ValidationError, match="forbidden"):
        Finding(
            findingId=uuid.uuid4(),
            evidenceId=uuid.uuid4(),
            findingType="FIRE_EXTINGUISHER_MISSING",
            confidence=0.85,
            severity=FindingSeverity.HIGH,
            explanation="Test finding",
            model="firesphere-inspection-cv",
            modelVersion="0.3.0-phase3",
            complianceStatus="AI_APPROVED",  # type: ignore
        )


def test_ai_cannot_return_ai_rejected():
    """Verifies that the system strictly forbids setting complianceStatus to AI_REJECTED."""
    with pytest.raises(ValidationError, match="forbidden"):
        Finding(
            findingId=uuid.uuid4(),
            evidenceId=uuid.uuid4(),
            findingType="EMERGENCY_EXIT_OBSTRUCTED",
            confidence=0.90,
            severity=FindingSeverity.CRITICAL,
            explanation="Test finding",
            model="firesphere-inspection-cv",
            modelVersion="0.3.0-phase3",
            complianceStatus="AI_REJECTED",  # type: ignore
        )


def test_finding_lacks_autonomous_noc_fields():
    """Verifies that Finding schema contains no autonomous approval/rejection flags."""
    fields = Finding.model_fields.keys()
    assert "approved" not in fields
    assert "rejected" not in fields
    assert "nocApproved" not in fields
    assert "nocStatus" not in fields
    assert "autoApprove" not in fields
    assert "autoReject" not in fields
