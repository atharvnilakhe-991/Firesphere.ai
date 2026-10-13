"""Evaluation, severity calculation, and explainability utilities for AI findings."""

from typing import Dict
from app.schemas.finding import FindingSeverity, FindingType


# Standardized default severity mapping for recognized fire safety non-compliances
SEVERITY_MAPPING: Dict[str, FindingSeverity] = {
    FindingType.EMERGENCY_EXIT_OBSTRUCTED: FindingSeverity.CRITICAL,
    FindingType.FIRE_EXTINGUISHER_MISSING: FindingSeverity.HIGH,
    FindingType.FIRE_EXTINGUISHER_BLOCKED: FindingSeverity.HIGH,
    FindingType.FIRE_EXTINGUISHER_EXPIRED: FindingSeverity.MEDIUM,
    FindingType.SPRINKLER_HEAD_DEFECT: FindingSeverity.HIGH,
    FindingType.ELECTRICAL_PANEL_HAZARD: FindingSeverity.CRITICAL,
    FindingType.EXIT_SIGNAGE_MISSING: FindingSeverity.MEDIUM,
    FindingType.MANUAL_CALL_POINT_BLOCKED: FindingSeverity.HIGH,
    FindingType.COMBUSTIBLE_MATERIAL_HAZARD: FindingSeverity.MEDIUM,
    FindingType.GENERIC_FIRE_SAFETY_DEFECT: FindingSeverity.INFORMATIONAL,
}

# Standard human-readable explanations to ensure AI output is reviewable and explainable
EXPLANATION_TEMPLATES: Dict[str, str] = {
    FindingType.EMERGENCY_EXIT_OBSTRUCTED: (
        "Emergency exit route or door is visibly obstructed by objects or equipment, "
        "violating primary egress safety codes."
    ),
    FindingType.FIRE_EXTINGUISHER_MISSING: (
        "Designated fire extinguisher station appears vacant or extinguisher is not detected "
        "at the expected location."
    ),
    FindingType.FIRE_EXTINGUISHER_BLOCKED: (
        "Clear access to the portable fire extinguisher is impeded by obstacles within 1 meter."
    ),
    FindingType.FIRE_EXTINGUISHER_EXPIRED: (
        "Visual inspection tag on extinguisher indicates past inspection or pressure gauge defect."
    ),
    FindingType.SPRINKLER_HEAD_DEFECT: (
        "Fire sprinkler head shows signs of corrosion, physical damage, or inadequate clearance."
    ),
    FindingType.EXIT_SIGNAGE_MISSING: (
        "Required illuminated emergency exit signage is missing, unlit, or obscured."
    ),
    FindingType.ELECTRICAL_PANEL_HAZARD: (
        "Electrical service panel has exposed wiring or combustible material stored within clearance perimeter."
    ),
    FindingType.COMBUSTIBLE_MATERIAL_HAZARD: (
        "Uncontrolled accumulation of combustible materials detected near potential ignition sources."
    ),
}


def calculate_severity(finding_type: str, confidence: float) -> FindingSeverity:
    """Computes finding severity based on safety defect classification and detection confidence."""
    base_severity = SEVERITY_MAPPING.get(finding_type, FindingSeverity.MEDIUM)

    # Adjust severity if confidence is marginal
    if confidence < 0.60 and base_severity in (FindingSeverity.CRITICAL, FindingSeverity.HIGH):
        return FindingSeverity.MEDIUM

    return base_severity


def generate_explanation(finding_type: str, confidence: float) -> str:
    """Generates an explainable description for the inspecting officer."""
    base_desc = EXPLANATION_TEMPLATES.get(
        finding_type,
        f"AI CV model detected potential {finding_type.replace('_', ' ').lower()} condition.",
    )
    return f"{base_desc} (Detection confidence: {confidence * 100:.1f}%. Requires manual officer verification.)"
