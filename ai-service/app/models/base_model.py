"""Abstract base class and contracts for FireSphere Computer Vision models."""

from abc import ABC, abstractmethod
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field


class RawDetection(BaseModel):
    """Raw detection output directly emitted by an object detection or classification model."""

    className: str = Field(description="Detected object class label (e.g., 'fire_extinguisher')")
    confidence: float = Field(ge=0.0, le=1.0, description="Raw prediction probability [0.0 - 1.0]")
    boundingBox: Optional[Dict[str, float]] = Field(
        default=None,
        description="Normalized bounding box coordinates {'xMin': float, 'yMin': float, 'xMax': float, 'yMax': float}",
    )
    attributes: Dict[str, Any] = Field(default_factory=dict, description="Auxiliary detection attributes")


class BaseInspectionModel(ABC):
    """Abstract interface for all FireSphere inspection AI/CV models.

    Ensures unified lifecycle management: initialization, health check, inference, and versioning.
    Designed for seamless integration of YOLO, PyTorch, and fine-tuned CV models in Phase 2.
    """

    @abstractmethod
    def load(self) -> None:
        """Loads model weights, configuration, and dependencies into memory."""
        pass

    @abstractmethod
    def predict(self, evidence_id: str, preprocessed_image: Any) -> List[RawDetection]:
        """Runs model inference on preprocessed evidence data.

        Returns raw detections to be evaluated and transformed into formal findings.
        """
        pass

    @abstractmethod
    def get_metadata(self) -> Dict[str, str]:
        """Returns model name, version, architecture, and training checkpoint identifiers."""
        pass

    @abstractmethod
    def is_ready(self) -> bool:
        """Returns whether the model is loaded and ready for inference."""
        pass
