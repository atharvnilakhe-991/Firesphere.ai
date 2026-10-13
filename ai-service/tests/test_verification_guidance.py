"""Tests for OfficerVerificationGuidance checklists across all cataloged classes."""

import pytest
from app.intelligence.verification import (
    VERIFICATION_GUIDANCE,
    OfficerVerificationGuidance,
)
from app.schemas.finding import FindingType


def test_guidance_exists_for_all_fire_safety_classes():
    """Verifies that every fire safety class in the catalog has a non-empty actionable checklist."""
    cataloged = [
        FindingType.FIRE_EXTINGUISHER_MISSING,
        FindingType.FIRE_EXTINGUISHER_BLOCKED,
        FindingType.FIRE_EXTINGUISHER_EXPIRED,
        FindingType.EMERGENCY_EXIT_OBSTRUCTED,
        FindingType.EXIT_SIGNAGE_MISSING,
        FindingType.SPRINKLER_HEAD_DEFECT,
        FindingType.ELECTRICAL_PANEL_HAZARD,
        FindingType.COMBUSTIBLE_MATERIAL_HAZARD,
        FindingType.MANUAL_CALL_POINT_BLOCKED,
        FindingType.HOSE_REEL_OBSTRUCTED,
        FindingType.GENERIC_FIRE_SAFETY_DEFECT,
    ]

    for cls_name in cataloged:
        guidance = OfficerVerificationGuidance.get_guidance(cls_name)
        assert isinstance(guidance, list)
        assert len(guidance) >= 3
        # Each item should be substantive
        for item in guidance:
            assert len(item) > 15


def test_fire_extinguisher_missing_specific_checks():
    """Verifies specific on-site verification items for missing fire extinguishers."""
    guidance = OfficerVerificationGuidance.get_guidance(FindingType.FIRE_EXTINGUISHER_MISSING)
    text = " ".join(guidance).lower()

    assert "physical presence" in text
    assert "bracket" in text
    assert "angle" in text
    assert "travel distance" in text


def test_emergency_exit_obstructed_specific_checks():
    """Verifies specific on-site verification items for obstructed emergency exits."""
    guidance = OfficerVerificationGuidance.get_guidance(FindingType.EMERGENCY_EXIT_OBSTRUCTED)
    text = " ".join(guidance).lower()

    assert "door" in text or "corridor" in text
    assert "temporary" in text or "stored" in text
    assert "hardware" in text or "panic bar" in text
    assert "width" in text


def test_unknown_class_fallback_guidance():
    """Verifies that an uncataloged defect type returns a safe fallback inspection checklist."""
    guidance = OfficerVerificationGuidance.get_guidance("CUSTOM_UNKNOWN_DEFECT")
    assert isinstance(guidance, list)
    assert len(guidance) >= 2
    assert "custom unknown defect" in guidance[0].lower()
