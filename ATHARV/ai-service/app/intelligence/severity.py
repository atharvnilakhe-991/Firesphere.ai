"""Deterministic fire safety severity engine, operational risk classification, and reasoning."""

from typing import Dict, NamedTuple, Optional
from app.schemas.finding import FindingSeverity, FindingType


class SeverityProfile(NamedTuple):
    """Operational severity profile for a specific fire safety defect category."""

    baseSeverity: FindingSeverity
    reason: str
    assumptions: str
    verificationRequirement: str


# Operational Severity Registry for all cataloged FireSphere defect categories.
# NOTE: These are OPERATIONAL AI RISK CATEGORIES for workflow prioritization, NOT legal classifications.
SEVERITY_PROFILES: Dict[str, SeverityProfile] = {
    FindingType.EMERGENCY_EXIT_OBSTRUCTED: SeverityProfile(
        baseSeverity=FindingSeverity.CRITICAL,
        reason=(
            "Obstructed emergency egress directly impedes occupant evacuation during fire or smoke spread, "
            "representing an acute life-safety risk."
        ),
        assumptions="Assumes the flagged passage or doorway serves as a designated fire exit or egress corridor.",
        verificationRequirement=(
            "Requires physical officer verification of the actual obstruction, exit door functionality, "
            "occupant load, and remaining clear egress width."
        ),
    ),
    FindingType.ELECTRICAL_PANEL_HAZARD: SeverityProfile(
        baseSeverity=FindingSeverity.CRITICAL,
        reason=(
            "Open electrical distribution panels or combustible goods stored within electrical service perimeters "
            "constitute primary ignition and flashover hazards."
        ),
        assumptions="Assumes the electrical enclosure carries active service voltage and lacks required physical separation.",
        verificationRequirement=(
            "Requires officer inspection of panel latching, physical working clearance (minimum 1m), "
            "and absence of nearby flammable inventory."
        ),
    ),
    FindingType.FIRE_EXTINGUISHER_MISSING: SeverityProfile(
        baseSeverity=FindingSeverity.HIGH,
        reason=(
            "Absent portable fire extinguisher deprives occupants of first-response incipient fire control capability, "
            "increasing potential fire growth."
        ),
        assumptions="Assumes the vacant mounting bracket or marker designates an active first-aid firefighting post.",
        verificationRequirement=(
            "Requires officer verification of physical cylinder presence, correct mounting height, "
            "and travel distance from adjacent stations."
        ),
    ),
    FindingType.FIRE_EXTINGUISHER_BLOCKED: SeverityProfile(
        baseSeverity=FindingSeverity.HIGH,
        reason=(
            "Physical obstacles impeding access to a fire extinguisher cause critical delays during incipient fire response."
        ),
        assumptions="Assumes the obstruction restricts immediate physical retrieval within the 1-meter service envelope.",
        verificationRequirement=(
            "Requires officer verification to determine whether the obstruction is temporary or permanent, "
            "and confirm unimpeded physical clearance."
        ),
    ),
    FindingType.SPRINKLER_HEAD_DEFECT: SeverityProfile(
        baseSeverity=FindingSeverity.HIGH,
        reason=(
            "Corroded, mechanically damaged, painted, or obstructed sprinkler heads compromise automatic suppression spray patterns."
        ),
        assumptions="Assumes the automatic sprinkler system is active and the head provides primary overhead coverage.",
        verificationRequirement=(
            "Requires officer inspection of sprinkler deflector clearance (minimum 450mm) and physical condition of the thermal element."
        ),
    ),
    FindingType.MANUAL_CALL_POINT_BLOCKED: SeverityProfile(
        baseSeverity=FindingSeverity.HIGH,
        reason=(
            "Obstructed manual fire alarm call points delay immediate occupant evacuation notification and emergency dispatch."
        ),
        assumptions="Assumes the station is part of the operational building-wide fire alarm notification infrastructure.",
        verificationRequirement=(
            "Requires officer verification of direct line-of-sight visibility, clear approach, and operational pull mechanism."
        ),
    ),
    FindingType.HOSE_REEL_OBSTRUCTED: SeverityProfile(
        baseSeverity=FindingSeverity.HIGH,
        reason=(
            "Inaccessible fire hose reel cabinets prevent deployment of secondary water-based firefighting streams."
        ),
        assumptions="Assumes the cabinet connects to pressurized fire mains intended for first-response firefighting.",
        verificationRequirement=(
            "Requires officer verification that cabinet doors swing 180 degrees without blockage and nozzle/valve are operational."
        ),
    ),
    FindingType.FIRE_EXTINGUISHER_EXPIRED: SeverityProfile(
        baseSeverity=FindingSeverity.MEDIUM,
        reason=(
            "Expired inspection tag or depressurized gauge indicates the extinguisher may fail to discharge during emergency use."
        ),
        assumptions="Assumes visual gauge indicator is in the discharged or recharge zone or inspection tag is overdue.",
        verificationRequirement=(
            "Requires officer physical inspection of gauge pressure needle, inspection ring collar, and hydrostatic test records."
        ),
    ),
    FindingType.EXIT_SIGNAGE_MISSING: SeverityProfile(
        baseSeverity=FindingSeverity.MEDIUM,
        reason=(
            "Missing, unlit, or obscured emergency exit signage impairs path finding under reduced visibility or smoke conditions."
        ),
        assumptions="Assumes the location requires illuminated directional egress marking under National Building Code.",
        verificationRequirement=(
            "Requires officer verification of sign presence, illumination, emergency battery backup, and corridor line of sight."
        ),
    ),
    FindingType.COMBUSTIBLE_MATERIAL_HAZARD: SeverityProfile(
        baseSeverity=FindingSeverity.MEDIUM,
        reason=(
            "Excessive or unorganized accumulation of combustible materials elevates local fire load and flame spread velocity."
        ),
        assumptions="Assumes the materials are flammable and situated near potential heat or ignition sources.",
        verificationRequirement=(
            "Requires officer review of storage classification, segregation, quantity limits, and proximity to ignition points."
        ),
    ),
    FindingType.GENERIC_FIRE_SAFETY_DEFECT: SeverityProfile(
        baseSeverity=FindingSeverity.INFORMATIONAL,
        reason="General advisory visual observation flagged for officer awareness.",
        assumptions="Visual observation requires preliminary officer appraisal.",
        verificationRequirement="Requires manual officer assessment against applicable building safety guidelines.",
    ),
}


class SeverityEngine:
    """Deterministic severity engine for operational fire safety risk categorization."""

    @classmethod
    def get_profile(cls, finding_type: str) -> SeverityProfile:
        """Retrieves operational severity profile for finding type, defaulting to INFORMATIONAL."""
        return SEVERITY_PROFILES.get(
            finding_type,
            SeverityProfile(
                baseSeverity=FindingSeverity.MEDIUM,
                reason=f"Operational observation of potential {finding_type.replace('_', ' ').lower()}.",
                assumptions="Assumes visual condition may influence fire safety.",
                verificationRequirement="Requires manual officer assessment on site.",
            ),
        )

    @classmethod
    def evaluate_severity(
        cls,
        finding_type: str,
        confidence: float,
    ) -> FindingSeverity:
        """Computes deterministic operational severity rating.

        Adjusts severity when detection confidence is marginal to avoid premature escalation:
        - If confidence < 0.60 and base severity is CRITICAL or HIGH, adjusts to MEDIUM.
        """
        profile = cls.get_profile(finding_type)
        base = profile.baseSeverity

        if confidence < 0.60 and base in (FindingSeverity.CRITICAL, FindingSeverity.HIGH):
            return FindingSeverity.MEDIUM

        return base

    @classmethod
    def generate_severity_reason(
        cls,
        finding_type: str,
        assigned_severity: FindingSeverity,
    ) -> str:
        """Constructs human-readable reasoning explaining the operational severity assignment."""
        profile = cls.get_profile(finding_type)
        return (
            f"Assigned operational severity: {assigned_severity.value}. Reason: {profile.reason} "
            f"Assumptions: {profile.assumptions} Verification: {profile.verificationRequirement}"
        )
