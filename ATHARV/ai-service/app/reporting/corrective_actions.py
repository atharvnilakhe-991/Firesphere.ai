"""Deterministic corrective action recommendation engine for fire safety inspection findings."""

from typing import Dict, NamedTuple
import uuid
from app.schemas.finding import FindingType
from app.schemas.report import ReportCorrectiveAction


class CorrectiveActionTemplate(NamedTuple):
    action: str
    scope: str


# Deterministic, safe operational templates without fabricated regulations, penalties, or deadlines
CORRECTIVE_TEMPLATES: Dict[str, CorrectiveActionTemplate] = {
    FindingType.FIRE_EXTINGUISHER_MISSING: CorrectiveActionTemplate(
        action=(
            "Verify the designated extinguisher station and provide/mount the required certified portable fire extinguisher "
            "in accordance with the authorized inspection checklist."
        ),
        scope="Extinguisher mounting bracket and immediate designated perimeter",
    ),
    FindingType.FIRE_EXTINGUISHER_BLOCKED: CorrectiveActionTemplate(
        action=(
            "Clear all temporary or permanent obstructions within a 1-meter radius of the fire extinguisher station to restore "
            "unimpeded emergency approach and retrieval."
        ),
        scope="1-meter clear access perimeter around fire extinguisher",
    ),
    FindingType.FIRE_EXTINGUISHER_EXPIRED: CorrectiveActionTemplate(
        action=(
            "Arrange immediate maintenance inspection by an authorized service agency, recharge cylinder if gauge indicator "
            "is depressed, and affix a current certified inspection tag."
        ),
        scope="Portable fire extinguisher cylinder and valve assembly",
    ),
    FindingType.EMERGENCY_EXIT_OBSTRUCTED: CorrectiveActionTemplate(
        action=(
            "Immediately remove all stored goods, equipment, and barriers from the emergency exit doorway and corridor to restore "
            "full, unobstructed egress width for building occupants."
        ),
        scope="Primary emergency exit doorway, corridor, and stairwell route",
    ),
    FindingType.EXIT_SIGNAGE_MISSING: CorrectiveActionTemplate(
        action=(
            "Install or restore required illuminated directional emergency exit signage with secondary battery backup "
            "at the designated egress portal in accordance with the applicable safety checklist."
        ),
        scope="Overhead lintel and directional approach sightlines to exit door",
    ),
    FindingType.SPRINKLER_HEAD_DEFECT: CorrectiveActionTemplate(
        action=(
            "Have a qualified fire protection technician inspect and replace damaged, painted, or corroded sprinkler heads, "
            "and enforce minimum 450mm vertical clearance below all deflectors."
        ),
        scope="Overhead fire sprinkler distribution head and surrounding clearance zone",
    ),
    FindingType.ELECTRICAL_PANEL_HAZARD: CorrectiveActionTemplate(
        action=(
            "Close and securely latch electrical service panel doors, seal any missing knockout openings, and remove all "
            "combustible storage within the minimum 1-meter working clearance envelope."
        ),
        scope="Electrical distribution board enclosure and dedicated floor perimeter",
    ),
    FindingType.COMBUSTIBLE_MATERIAL_HAZARD: CorrectiveActionTemplate(
        action=(
            "Organize, reduce, or relocate excessive combustible materials to approved storage arrangements and maintain "
            "safe separation distance from potential heat and ignition sources."
        ),
        scope="Combustible inventory storage area and adjacent ignition hazards",
    ),
    FindingType.MANUAL_CALL_POINT_BLOCKED: CorrectiveActionTemplate(
        action=(
            "Clear obstacles in front of the manual fire alarm call point to ensure direct visibility, unrestricted physical approach, "
            "and standard mounting accessibility."
        ),
        scope="Fire alarm pull station station and approach corridor",
    ),
    FindingType.HOSE_REEL_OBSTRUCTED: CorrectiveActionTemplate(
        action=(
            "Remove all obstructions around the fire hose reel cabinet, verify that cabinet doors swing open 180 degrees without "
            "interference, and confirm water supply valve operability."
        ),
        scope="Fire hose reel cabinet enclosure and valve access perimeter",
    ),
    FindingType.GENERIC_FIRE_SAFETY_DEFECT: CorrectiveActionTemplate(
        action=(
            "Review the flagged condition with the premises safety coordinator and address observed safety discrepancies "
            "in alignment with authorized inspection procedures."
        ),
        scope="Identified inspection zone",
    ),
}


class CorrectiveActionEngine:
    """Generates generic, non-statutory operational corrective suggestions."""

    @classmethod
    def generate_suggestion(
        cls,
        index: int,
        finding_id: uuid.UUID,
        finding_type: str,
    ) -> ReportCorrectiveAction:
        """Constructs a deterministic corrective action suggestion for a finding."""
        template = CORRECTIVE_TEMPLATES.get(
            finding_type,
            CorrectiveActionTemplate(
                action=f"Physically examine and rectify apparent {finding_type.replace('_', ' ').lower()} condition.",
                scope="Flagged evidence area",
            ),
        )

        return ReportCorrectiveAction(
            suggestionId=f"CA-{index:03d}",
            findingId=finding_id,
            findingType=finding_type,
            suggestedAction=template.action,
            actionType="AI_SUGGESTED_CORRECTIVE_ACTION",
            scope=template.scope,
            disclaimer="Advisory recommendation only. Official statutory orders must be determined by authorized Fire Officers.",
        )
