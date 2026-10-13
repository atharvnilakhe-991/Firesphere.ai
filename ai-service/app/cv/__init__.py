"""Computer Vision preprocessing package for FireSphere."""

from app.cv.file_validator import FileValidator
from app.cv.preprocessor import (
    BaseCVPreprocessor,
    CVPreprocessor,
    PreprocessingResult,
)
from app.cv.quality import ImageQualityReport, ImageQualityValidator
from app.cv.transforms import BoundingBoxTransformer, TransformationMetadata

__all__ = [
    "BaseCVPreprocessor",
    "CVPreprocessor",
    "PreprocessingResult",
    "FileValidator",
    "ImageQualityReport",
    "ImageQualityValidator",
    "BoundingBoxTransformer",
    "TransformationMetadata",
]
