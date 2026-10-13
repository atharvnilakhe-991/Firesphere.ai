"""Evidence-grounded explainability engine and future tabular/risk SHAP/LIME architecture."""

from abc import ABC, abstractmethod
from typing import Any, Dict, Optional
from app.schemas.finding import BoundingBox, FindingType


class BaseExplainer(ABC):
    """Abstract base class for explainability providers."""

    @abstractmethod
    def explain(self, **kwargs: Any) -> Dict[str, Any]:
        """Generates an explanation payload for the prediction or score."""
        pass


class VisionExplanationProvider(BaseExplainer):
    """Generates evidence-grounded, human-readable explanations for computer vision detections."""

    WHAT_DESCRIPTIONS: Dict[str, str] = {
        FindingType.EMERGENCY_EXIT_OBSTRUCTED: "apparent obstruction in emergency exit passage or door",
        FindingType.ELECTRICAL_PANEL_HAZARD: "open electrical distribution panel or combustible storage near switchgear",
        FindingType.FIRE_EXTINGUISHER_MISSING: "vacant fire extinguisher mounting bracket or absent unit",
        FindingType.FIRE_EXTINGUISHER_BLOCKED: "impeded physical approach to a portable fire extinguisher",
        FindingType.FIRE_EXTINGUISHER_EXPIRED: "discharge indicator or outdated maintenance collar on fire extinguisher",
        FindingType.SPRINKLER_HEAD_DEFECT: "damaged, obstructed, or corroded fire sprinkler head",
        FindingType.EXIT_SIGNAGE_MISSING: "absent or dark emergency illuminated exit signage",
        FindingType.COMBUSTIBLE_MATERIAL_HAZARD: "uncontrolled accumulation of combustible materials near heat sources",
        FindingType.MANUAL_CALL_POINT_BLOCKED: "blocked manual fire alarm call point station",
        FindingType.HOSE_REEL_OBSTRUCTED: "inaccessible or obstructed fire hose reel cabinet",
        FindingType.GENERIC_FIRE_SAFETY_DEFECT: "potential fire safety defect or advisory visual condition",
    }

    @classmethod
    def _describe_spatial_region(cls, box: Optional[BoundingBox]) -> str:
        """Determines image quadrant/region based on bounding box center."""
        if box is None:
            return "location unspecified in visual frame"

        center_x = (box.xMin + box.xMax) / 2.0
        center_y = (box.yMin + box.yMax) / 2.0

        h_pos = "left" if center_x < 0.35 else ("right" if center_x > 0.65 else "center")
        v_pos = "upper" if center_y < 0.35 else ("lower" if center_y > 0.65 else "mid")

        region = f"{v_pos}-{h_pos}" if h_pos != "center" or v_pos != "mid" else "central area"
        coords = f"[{box.xMin:.2f}, {box.yMin:.2f}, {box.xMax:.2f}, {box.yMax:.2f}]"
        return f"{region} of the image (coordinates {coords})"

    def explain(
        self,
        finding_type: str,
        confidence: float,
        confidence_level: str,
        bounding_box: Optional[BoundingBox] = None,
        evidence_quality: str = "ACCEPTABLE",
        **kwargs: Any,
    ) -> Dict[str, Any]:
        """Constructs an evidence-grounded explanation answering WHAT, WHERE, CONFIDENCE, WHY, and VERIFY."""
        what = self.WHAT_DESCRIPTIONS.get(
            finding_type,
            f"potential {finding_type.replace('_', ' ').lower()} condition",
        )
        where = self._describe_spatial_region(bounding_box)

        why = (
            f"Visual features extracted from the photograph exhibit patterns characteristic of a {what}."
        )

        verify = (
            "The inspecting Fire Safety Officer should physically examine the premises to verify physical conditions, "
            "confirm whether the condition is permanent or transient, and evaluate applicable regulatory standards."
        )

        narrative = (
            f"WHAT: The computer-vision inspection model identified {what}. "
            f"WHERE: Detected in the {where}. "
            f"CONFIDENCE: Detection score is {confidence:.2f} ({confidence_level}). "
            "Note: Model confidence reflects visual pattern matching, not legal compliance certainty. "
            f"WHY: {why} "
            f"VERIFY: {verify}"
        )

        return {
            "narrative": narrative,
            "what": what,
            "where": where,
            "confidenceScore": confidence,
            "confidenceLevel": confidence_level,
            "why": why,
            "verify": verify,
        }


class RiskModelExplainer(BaseExplainer):
    """Prepared architecture for future SHAP / LIME feature attribution on tabular risk models."""

    def __init__(self, model: Optional[Any] = None):
        self.model = model
        self.status = "NOT_AVAILABLE"

    def explain(self, **kwargs: Any) -> Dict[str, Any]:
        """Returns structured explanation or transparently indicates feature attribution is not yet trained."""
        return {
            "explainerStatus": self.status,
            "method": "SHAP_LIME_FUTURE_INTERFACE",
            "message": (
                "SHAP/LIME feature attribution is not available: no trained tabular or risk prediction "
                "model is currently deployed. Visual explainability is provided via VisionExplanationProvider."
            ),
            "attributions": {},
        }


class ExplainabilityEngine:
    """Consolidated explainability engine coordinating visual and risk explainers."""

    def __init__(self):
        self.vision_explainer = VisionExplanationProvider()
        self.risk_explainer = RiskModelExplainer()

    def generate_explanation(
        self,
        finding_type: str,
        confidence: float,
        confidence_level: str,
        bounding_box: Optional[BoundingBox] = None,
        evidence_quality: str = "ACCEPTABLE",
    ) -> str:
        """Returns evidence-grounded narrative explanation string."""
        payload = self.vision_explainer.explain(
            finding_type=finding_type,
            confidence=confidence,
            confidence_level=confidence_level,
            bounding_box=bounding_box,
            evidence_quality=evidence_quality,
        )
        return payload["narrative"]

    def get_risk_explainer_status(self) -> Dict[str, Any]:
        """Returns status of tabular/risk explainers."""
        return self.risk_explainer.explain()


# Global explainability engine instance
explainability_engine = ExplainabilityEngine()
