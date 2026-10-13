"""Placeholder model implementation for Phase 1 AI Service foundation."""

from typing import Any, Dict, List
from app.core.logging import logger
from app.models.base_model import BaseInspectionModel, RawDetection


class PlaceholderInspectionModel(BaseInspectionModel):
    """Phase 1 baseline model implementation.

    Serves as an architectural placeholder prior to Phase 2 YOLO/PyTorch integration.
    Never fabricates detections for real inspection evidence without a trained model.
    """

    def __init__(self, model_name: str = "firesphere-inspection-cv", model_version: str = "0.1.0-phase1"):
        self.model_name = model_name
        self.model_version = model_version
        self._loaded = False

    def load(self) -> None:
        """Initializes model readiness."""
        logger.info(f"Initialized {self.model_name} (v{self.model_version}) in Phase 1 baseline mode.")
        self._loaded = True

    def is_ready(self) -> bool:
        return self._loaded

    def predict(self, evidence_id: str, preprocessed_image: Any, test_mode: bool = False) -> List[RawDetection]:
        """In Phase 1: returns empty list for standard requests (no trained model yet).

        When test_mode is explicitly enabled during automated testing, returns synthetic detections.
        """
        if test_mode:
            logger.debug(f"Generating synthetic test detection for evidence {evidence_id} in testMode")
            return [
                RawDetection(
                    className="FIRE_EXTINGUISHER_MISSING",
                    confidence=0.92,
                    boundingBox={"xMin": 0.15, "yMin": 0.20, "xMax": 0.45, "yMax": 0.70},
                    attributes={"testFixture": True, "evidenceId": evidence_id},
                )
            ]
        # Real production calls return empty detections in Phase 1
        return []

    def get_metadata(self) -> Dict[str, str]:
        return {
            "modelName": self.model_name,
            "modelVersion": self.model_version,
            "architecture": "Phase1-Placeholder-Baseline",
            "framework": "Python-FastAPI-Pydantic",
            "phase": "Phase 1 - Foundation & Service Setup",
        }
