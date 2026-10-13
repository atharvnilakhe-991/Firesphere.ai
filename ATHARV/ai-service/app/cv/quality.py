"""Image quality analysis and pre-inference validation."""

from typing import List, Tuple, Dict, Any, Optional
import cv2
import numpy as np
from pydantic import BaseModel, Field
from app.core.config import settings
from app.core.logging import logger


class ImageQualityReport(BaseModel):
    """Structured report documenting image dimensions, quality metrics, warnings, and errors."""

    isAcceptable: bool = Field(description="True if image satisfies minimum requirements for processing")
    dimensions: Tuple[int, int] = Field(description="(width, height) of decoded image")
    aspectRatio: float = Field(description="Width to height aspect ratio")
    blurScore: float = Field(description="Laplacian variance metric measuring focus/sharpness")
    brightnessScore: float = Field(description="Mean grayscale intensity [0.0 - 255.0]")
    warnings: List[str] = Field(default_factory=list, description="Quality advisory notices for reviewing officer")
    errors: List[str] = Field(default_factory=list, description="Fatal defects preventing CV processing")


class ImageQualityValidator:
    """Evaluates image dimensions, blur, illumination, and geometry."""

    @classmethod
    def evaluate(
        cls,
        image_bgr: np.ndarray,
        min_width: Optional[int] = None,
        min_height: Optional[int] = None,
        blur_threshold: Optional[float] = None,
    ) -> ImageQualityReport:
        """Runs quality checks on a decoded image matrix.

        Distinguishes fatal ERRORS (e.g. below minimum resolution) from
        advisory WARNINGS (e.g. high blur, low illumination).
        """
        min_w = min_width or settings.min_image_width
        min_h = min_height or settings.min_image_height
        blur_th = blur_threshold if blur_threshold is not None else settings.blur_threshold

        warnings: List[str] = []
        errors: List[str] = []

        if image_bgr is None or not isinstance(image_bgr, np.ndarray):
            return ImageQualityReport(
                isAcceptable=False,
                dimensions=(0, 0),
                aspectRatio=0.0,
                blurScore=0.0,
                brightnessScore=0.0,
                warnings=[],
                errors=["Image matrix is null or invalid."],
            )

        h, w = image_bgr.shape[:2]

        # 1. Critical Dimension Checks (FATAL ERROR)
        if w < min_w or h < min_h:
            errors.append(
                f"Image dimensions ({w}x{h}) are below minimum required resolution ({min_w}x{min_h})."
            )

        # 2. Aspect Ratio Check (WARNING)
        aspect_ratio = round(w / float(h), 3) if h > 0 else 0.0
        if aspect_ratio > 8.0:
            warnings.append(
                f"Extreme wide aspect ratio ({aspect_ratio:.2f}:1). May cause significant letterbox distortion."
            )
        elif aspect_ratio < 0.125:
            warnings.append(
                f"Extreme tall aspect ratio (1:{1.0 / aspect_ratio:.2f}). May cause significant letterbox distortion."
            )

        # 3. Grayscale conversion for blur and brightness analysis
        gray = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2GRAY) if len(image_bgr.shape) == 3 else image_bgr

        # 4. Blur / Sharpness Check via Laplacian Variance (WARNING)
        # Low variance indicates lack of edge sharpness / blurry capture
        laplacian_var = cv2.Laplacian(gray, cv2.CV_64F).var()
        blur_score = round(float(laplacian_var), 2)
        if blur_score < blur_th:
            warnings.append(
                f"Image focus is suboptimal (sharpness score: {blur_score} < {blur_th}). "
                "Advisory review recommended."
            )

        # 5. Illumination / Brightness Check (WARNING)
        mean_brightness = round(float(np.mean(gray)), 2)
        if mean_brightness < 30.0:
            warnings.append(
                f"Image is underexposed or in very low light (mean intensity: {mean_brightness}/255)."
            )
        elif mean_brightness > 235.0:
            warnings.append(
                f"Image is heavily overexposed or washed out (mean intensity: {mean_brightness}/255)."
            )

        is_acceptable = len(errors) == 0

        return ImageQualityReport(
            isAcceptable=is_acceptable,
            dimensions=(w, h),
            aspectRatio=aspect_ratio,
            blurScore=blur_score,
            brightnessScore=mean_brightness,
            warnings=warnings,
            errors=errors,
        )
