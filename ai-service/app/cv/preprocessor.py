"""OpenCV Computer Vision preprocessing pipeline for FireSphere inspection evidence."""

from abc import ABC, abstractmethod
import time
from typing import Any, Dict, Optional, Tuple
import cv2
import numpy as np
from app.core.config import settings
from app.core.exceptions import (
    ImageDecodeException,
    ImageQualityException,
    UnsupportedInputException,
)
from app.core.logging import logger
from app.cv.file_validator import FileValidator
from app.cv.quality import ImageQualityReport, ImageQualityValidator
from app.cv.transforms import TransformationMetadata


class PreprocessingResult:
    """Encapsulates the output of the Computer Vision preprocessing pipeline."""

    def __init__(
        self,
        evidence_id: str,
        status: str,
        preprocessed_array: Optional[np.ndarray],
        metadata: TransformationMetadata,
        quality: ImageQualityReport,
        duration_ms: float,
        error_message: Optional[str] = None,
    ):
        self.evidence_id = evidence_id
        self.status = status
        self.preprocessed_array = preprocessed_array
        self.metadata = metadata
        self.quality = quality
        self.duration_ms = duration_ms
        self.error_message = error_message

    def to_dict(self) -> Dict[str, Any]:
        """Serializes result descriptors to a standard dictionary."""
        return {
            "evidenceId": self.evidence_id,
            "status": self.status,
            "durationMs": self.duration_ms,
            "metadata": self.metadata.model_dump(),
            "quality": self.quality.model_dump(),
            "tensorShape": list(self.preprocessed_array.shape) if self.preprocessed_array is not None else None,
            "colorFormat": self.metadata.colorFormat,
            "normalized": self.metadata.normalized,
            "errorMessage": self.error_message,
        }

    # Backward compatibility with dictionary access
    def __getitem__(self, item: str) -> Any:
        return self.to_dict().get(item)

    def get(self, item: str, default: Any = None) -> Any:
        return self.to_dict().get(item, default)


class BaseCVPreprocessor(ABC):
    """Abstract interface for image preprocessing prior to model inference."""

    @abstractmethod
    def validate_media(self, evidence_id: str, media_type: str, raw_bytes: Optional[bytes] = None) -> bool:
        """Validates that evidence media type and format are supported."""
        pass

    @abstractmethod
    def preprocess(self, raw_bytes: bytes, target_size: Optional[Tuple[int, int]] = None, **kwargs: Any) -> Any:
        """Applies normalization, resizing, and color space transformations for inference."""
        pass


class CVPreprocessor(BaseCVPreprocessor):
    """Phase 2 Computer Vision preprocessor utilizing OpenCV.

    Pipeline:
    Raw bytes -> Validation -> Decode (cv2) -> Quality Checks -> BGR to RGB ->
    Aspect-preserving Letterbox Resize -> Normalization (float32 [0.0, 1.0]) -> Model-ready array.
    """

    SUPPORTED_MIME_TYPES = FileValidator.SUPPORTED_MIME_TYPES

    def validate_media(self, evidence_id: str, media_type: str, raw_bytes: Optional[bytes] = None) -> bool:
        """Validates that the evidence format and binary payload are valid."""
        if raw_bytes is not None and len(raw_bytes) > 0:
            FileValidator.validate_payload(
                evidence_id=evidence_id,
                raw_bytes=raw_bytes,
                declared_mime_type=media_type,
            )
            return True

        # Fallback MIME-only validation if raw_bytes are omitted
        if media_type.lower() not in self.SUPPORTED_MIME_TYPES and not media_type.startswith("image/"):
            logger.warning(f"Unsupported media type '{media_type}' for evidenceId={evidence_id}")
            raise UnsupportedInputException(
                message=f"Unsupported evidence media type: {media_type}. Supported: JPEG, PNG, WEBP."
            )
        return True

    def letterbox(
        self,
        image: np.ndarray,
        target_size: Tuple[int, int],
        pad_value: Tuple[int, int, int] = (114, 114, 114),
    ) -> Tuple[np.ndarray, TransformationMetadata]:
        """Resizes image to target_size (width, height) preserving aspect ratio with letterbox padding."""
        orig_h, orig_w = image.shape[:2]
        target_w, target_h = target_size

        # Compute scaling ratio
        scale = min(target_w / float(orig_w), target_h / float(orig_h))
        scaled_w = int(round(orig_w * scale))
        scaled_h = int(round(orig_h * scale))

        # Resize image using linear interpolation for downscaling / upscaling
        interpolation = cv2.INTER_AREA if scale < 1.0 else cv2.INTER_LINEAR
        resized_img = cv2.resize(image, (scaled_w, scaled_h), interpolation=interpolation)

        # Compute symmetric padding
        dw = (target_w - scaled_w) / 2.0
        dh = (target_h - scaled_h) / 2.0

        top = int(round(dh - 0.1))
        bottom = target_h - scaled_h - top
        left = int(round(dw - 0.1))
        right = target_w - scaled_w - left

        # Apply letterbox border padding
        letterboxed = cv2.copyMakeBorder(
            resized_img,
            top,
            bottom,
            left,
            right,
            cv2.BORDER_CONSTANT,
            value=pad_value,
        )

        metadata = TransformationMetadata(
            originalWidth=orig_w,
            originalHeight=orig_h,
            targetWidth=target_w,
            targetHeight=target_h,
            scaledWidth=scaled_w,
            scaledHeight=scaled_h,
            scaleFactor=scale,
            padLeft=left,
            padRight=right,
            padTop=top,
            padBottom=bottom,
            colorFormat=settings.image_color_format,
            normalized=settings.enable_normalization,
            durationMs=0.0,
        )

        return letterboxed, metadata

    def preprocess(
        self,
        raw_bytes: bytes,
        target_size: Optional[Tuple[int, int]] = None,
        evidence_id: str = "unknown",
        normalize: Optional[bool] = None,
    ) -> PreprocessingResult:
        """Executes end-to-end OpenCV decoding, quality evaluation, letterboxing, and normalization."""
        start_time = time.perf_counter()
        target_dimensions = target_size or (settings.target_image_width, settings.target_image_height)
        should_normalize = normalize if normalize is not None else settings.enable_normalization

        # 1. Validate payload binary signatures and size
        detected_mime, _ = FileValidator.validate_payload(
            evidence_id=evidence_id,
            raw_bytes=raw_bytes,
        )

        # 2. Decode raw bytes into OpenCV image matrix (BGR)
        np_arr = np.frombuffer(raw_bytes, dtype=np.uint8)
        image_bgr = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)

        if image_bgr is None or image_bgr.size == 0:
            logger.error(f"cv2.imdecode failed to decode image bytes for evidenceId={evidence_id}")
            raise ImageDecodeException(
                message=f"Failed to decode evidence {evidence_id} into an image matrix. Corrupted or invalid image data.",
                details={"evidenceId": evidence_id, "detectedFormat": detected_mime},
            )

        # 3. Quality Validation
        quality_report = ImageQualityValidator.evaluate(image_bgr)
        if not quality_report.isAcceptable:
            logger.warning(
                f"Image failed critical quality checks | evidenceId={evidence_id} | "
                f"errors={quality_report.errors}"
            )
            raise ImageQualityException(
                message=f"Evidence {evidence_id} failed quality check: {'; '.join(quality_report.errors)}",
                details={"evidenceId": evidence_id, "errors": quality_report.errors},
            )

        # 4. Color Conversion (BGR -> RGB standard for model inference)
        if settings.image_color_format.upper() == "RGB":
            image_processed = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2RGB)
        else:
            image_processed = image_bgr

        # 5. Aspect-ratio-preserving Letterbox Resizing
        letterboxed, transform_meta = self.letterbox(
            image=image_processed,
            target_size=target_dimensions,
        )

        # 6. Normalization: scale from uint8 [0, 255] to float32 [0.0, 1.0]
        if should_normalize:
            model_input = letterboxed.astype(np.float32) / 255.0
        else:
            model_input = letterboxed

        duration_ms = round((time.perf_counter() - start_time) * 1000, 2)
        transform_meta.durationMs = duration_ms

        status = "WARNING" if quality_report.warnings else "SUCCESS"

        logger.debug(
            f"Preprocessed evidenceId={evidence_id} in {duration_ms}ms | "
            f"Orig: {transform_meta.originalWidth}x{transform_meta.originalHeight} -> "
            f"Target: {transform_meta.targetWidth}x{transform_meta.targetHeight} | "
            f"Status: {status} | Warnings: {len(quality_report.warnings)}"
        )

        return PreprocessingResult(
            evidence_id=evidence_id,
            status=status,
            preprocessed_array=model_input,
            metadata=transform_meta,
            quality=quality_report,
            duration_ms=duration_ms,
        )
