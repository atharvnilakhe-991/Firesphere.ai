"""On-site officer verification guidance and ambiguity resolution checklists."""

from typing import Dict, List
from app.schemas.finding import FindingType


VERIFICATION_GUIDANCE: Dict[str, List[str]] = {
    FindingType.FIRE_EXTINGUISHER_MISSING: [
        "Verify physical presence of fire extinguisher at the designated bracket location.",
        "Confirm whether the wall bracket or sign is an active post or decommissioned station.",
        "Check whether camera viewpoint or angle obscures an extinguisher located nearby.",
        "Confirm that an alternate certified extinguisher is available within mandated travel distance.",
        "Review on-site maintenance and inspection log for the sector.",
    ],
    FindingType.FIRE_EXTINGUISHER_BLOCKED: [
        "Inspect the 1-meter clear working perimeter around the extinguisher mounting location.",
        "Determine if the detected obstacle is transient/temporary (e.g. movable cart/pallet) or permanent.",
        "Verify that occupants and emergency responders have immediate, unimpeded physical access.",
        "Ensure signage above the extinguisher remains visible from all primary approach routes.",
    ],
    FindingType.FIRE_EXTINGUISHER_EXPIRED: [
        "Physically inspect the pressure gauge indicator to ensure the needle rests in the operable green zone.",
        "Examine the physical maintenance tag / annual service collar for inspection validity date.",
        "Check cylinder body stamp for required 5-year hydrostatic pressure testing certification.",
        "Inspect safety pin, tamper seal, and discharge nozzle for physical integrity.",
    ],
    FindingType.EMERGENCY_EXIT_OBSTRUCTED: [
        "Inspect the full width of the exit corridor, doorway, and stairwell for physical blockage.",
        "Determine whether the apparent blockage consists of temporary stored goods or fixed construction.",
        "Verify that exit door hardware (panic bar, lever, push plate) opens freely from inside without key or special knowledge.",
        "Measure remaining unobstructed egress width against minimum occupant capacity requirements.",
        "Verify that the exterior egress discharge path is completely unobstructed to the public way.",
    ],
    FindingType.EXIT_SIGNAGE_MISSING: [
        "Verify physical mounting of illuminated emergency exit sign directly above the egress door.",
        "Test primary electrical illumination and verify functionality of battery backup supply during power loss.",
        "Check directional exit sign line-of-sight from all corridor intersection points.",
        "Confirm contrast, lettering height, and compliance with National Building Code specifications.",
    ],
    FindingType.SPRINKLER_HEAD_DEFECT: [
        "Inspect sprinkler deflector and frame for physical corrosion, paint overspray, or mechanical distortion.",
        "Verify minimum 450mm (18 inches) vertical clearance between storage top and sprinkler deflector.",
        "Check that the thermal glass bulb element is intact, unpainted, and contains proper liquid volume.",
        "Confirm that escutcheon plate is properly seated and ceiling penetration is sealed.",
    ],
    FindingType.ELECTRICAL_PANEL_HAZARD: [
        "Verify that electrical distribution panel doors are closed, secured, and properly latched.",
        "Enforce minimum 1-meter dedicated working clearance in front of all switchgear and panels.",
        "Confirm that no combustible materials, packaging, or flammable liquids are stored within clearance zone.",
        "Check panel interior for missing knockout plugs, exposed busbars, or visible arcing damage.",
    ],
    FindingType.COMBUSTIBLE_MATERIAL_HAZARD: [
        "Assess storage classification, volume, and stacking arrangement of combustible goods.",
        "Verify adequate physical separation from space heaters, boilers, electrical panels, and hot works.",
        "Check for appropriate flammable liquid storage cabinets and spill containment provisions.",
        "Review housekeeping protocols and waste accumulation controls.",
    ],
    FindingType.MANUAL_CALL_POINT_BLOCKED: [
        "Verify clear, unobstructed approach to the manual fire alarm call point / pull station.",
        "Check that mounting height is between 1.2m and 1.4m above finished floor level.",
        "Inspect protective glass or hinged cover to ensure it is clean, undamaged, and readily operable.",
        "Confirm visibility of operating instructions on station faceplate.",
    ],
    FindingType.HOSE_REEL_OBSTRUCTED: [
        "Verify that fire hose reel cabinet door swings freely through a full 180-degree arc.",
        "Confirm clear physical approach without storage, shelving, or parked vehicles blocking access.",
        "Test manual isolation valve to verify live water supply pressure to the hose reel.",
        "Inspect rubber hose for cracking, dry rot, and nozzle shut-off operation.",
    ],
    FindingType.GENERIC_FIRE_SAFETY_DEFECT: [
        "Conduct on-site visual walkthrough of the flagged zone.",
        "Cross-reference visual observations against applicable local Fire Prevention Regulations.",
        "Consult site building manager or safety officer regarding observed condition.",
    ],
}


class OfficerVerificationGuidance:
    """Provides actionable on-site verification checklists tailored to each finding category."""

    @classmethod
    def get_guidance(cls, finding_type: str) -> List[str]:
        """Returns standard verification checklist for the specified defect type."""
        return VERIFICATION_GUIDANCE.get(
            finding_type,
            [
                f"Physically inspect the location of the flagged {finding_type.replace('_', ' ').lower()}.",
                "Verify whether observed visual features reflect permanent site non-compliance.",
                "Review maintenance logs and consult premise safety coordinator.",
            ],
        )
