"""Tests for deterministic SeverityEngine, fire-safety defect profiles, and operational reasoning."""

import pytest
from app.intelligence.severity import SEVERITY_PROFILES, SeverityEngine
from app.schemas.finding import FindingSeverity, FindingType


def test_severity_mapping_all_cataloged_classes():
    """Verifies that every fire safety class in the catalog has a deterministic operational severity profile."""
    cataloged_classes = [
        FindingType.EMERGENCY_EXIT_OBSTRUCTED,
        FindingType.ELECTRICAL_PANEL_HAZARD,
        FindingType.FIRE_EXTINGUISHER_MISSING,
        FindingType.FIRE_EXTINGUISHER_BLOCKED,
        FindingType.SPRINKLER_HEAD_DEFECT,
        FindingType.MANUAL_CALL_POINT_BLOCKED,
        FindingType.HOSE_REEL_OBSTRUCTED,
        FindingType.FIRE_EXTINGUISHER_EXPIRED,
        FindingType.EXIT_SIGNAGE_MISSING,
        FindingType.COMBUSTIBLE_MATERIAL_HAZARD,
        FindingType.GENERIC_FIRE_SAFETY_DEFECT,
    ]

    for cls_name in cataloged_classes:
        profile = SeverityEngine.get_profile(cls_name)
        assert profile is not None
        assert isinstance(profile.baseSeverity, FindingSeverity)
        assert len(profile.reason) > 10
        assert len(profile.assumptions) > 5
        assert "officer" in profile.verificationRequirement.lower() or "manual" in profile.verificationRequirement.lower()


def test_critical_and_high_severity_class_assignments():
    """Verifies high-risk life safety defects receive appropriate operational severity."""
    assert SeverityEngine.evaluate_severity(FindingType.EMERGENCY_EXIT_OBSTRUCTED, 0.85) == FindingSeverity.CRITICAL
    assert SeverityEngine.evaluate_severity(FindingType.ELECTRICAL_PANEL_HAZARD, 0.90) == FindingSeverity.CRITICAL
    assert SeverityEngine.evaluate_severity(FindingType.FIRE_EXTINGUISHER_MISSING, 0.80) == FindingSeverity.HIGH
    assert SeverityEngine.evaluate_severity(FindingType.FIRE_EXTINGUISHER_BLOCKED, 0.75) == FindingSeverity.HIGH
    assert SeverityEngine.evaluate_severity(FindingType.SPRINKLER_HEAD_DEFECT, 0.88) == FindingSeverity.HIGH
    assert SeverityEngine.evaluate_severity(FindingType.MANUAL_CALL_POINT_BLOCKED, 0.78) == FindingSeverity.HIGH
    assert SeverityEngine.evaluate_severity(FindingType.HOSE_REEL_OBSTRUCTED, 0.82) == FindingSeverity.HIGH


def test_marginal_confidence_severity_adjustment():
    """Verifies that if detection confidence is marginal (<0.60), high/critical base severity adjusts to MEDIUM."""
    # Under high confidence: CRITICAL
    assert SeverityEngine.evaluate_severity(FindingType.EMERGENCY_EXIT_OBSTRUCTED, 0.85) == FindingSeverity.CRITICAL
    # Under marginal confidence (0.52): adjusted to MEDIUM
    assert SeverityEngine.evaluate_severity(FindingType.EMERGENCY_EXIT_OBSTRUCTED, 0.52) == FindingSeverity.MEDIUM

    # Under high confidence: HIGH
    assert SeverityEngine.evaluate_severity(FindingType.FIRE_EXTINGUISHER_MISSING, 0.75) == FindingSeverity.HIGH
    # Under marginal confidence (0.55): adjusted to MEDIUM
    assert SeverityEngine.evaluate_severity(FindingType.FIRE_EXTINGUISHER_MISSING, 0.55) == FindingSeverity.MEDIUM


def test_severity_reasoning_generation():
    """Verifies that generated severity reasoning exposes the operational rule, assumptions, and verification requirement."""
    sev = FindingSeverity.HIGH
    reason = SeverityEngine.generate_severity_reason(FindingType.FIRE_EXTINGUISHER_MISSING, sev)

    assert "Assigned operational severity: HIGH" in reason
    assert "Assumptions:" in reason
    assert "Verification:" in reason
    assert "extinguisher" in reason.lower()
