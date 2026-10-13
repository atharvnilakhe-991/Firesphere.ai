"""Model interfaces, adapters, and loaders for FireSphere."""

from app.models.base_model import BaseInspectionModel, RawDetection
from app.models.cv_model_adapter import CVModelAdapter
from app.models.model_loader import ModelLoader, TORCH_AVAILABLE
from app.models.model_metadata import (
    FIRE_INSPECTION_CLASSES,
    FireSafetyClass,
    ModelMetadata,
)
from app.models.gemini_provider import (
    GeminiAnalysisResult,
    GeminiObservation,
    GeminiProvider,
    gemini_provider,
)
from app.models.gemini_schemas import (
    GeminiAnalysisStatus,
    GeminiRawObservation,
    GeminiRawResponse,
    parse_and_validate_bounding_box,
)
from app.models.nms import apply_non_maximum_suppression, compute_iou
from app.models.placeholder_model import PlaceholderInspectionModel

__all__ = [
    "BaseInspectionModel",
    "RawDetection",
    "CVModelAdapter",
    "ModelLoader",
    "ModelMetadata",
    "FireSafetyClass",
    "FIRE_INSPECTION_CLASSES",
    "apply_non_maximum_suppression",
    "compute_iou",
    "PlaceholderInspectionModel",
    "TORCH_AVAILABLE",
    "GeminiProvider",
    "gemini_provider",
    "GeminiObservation",
    "GeminiAnalysisResult",
    "GeminiAnalysisStatus",
    "GeminiRawObservation",
    "GeminiRawResponse",
    "parse_and_validate_bounding_box",
]


