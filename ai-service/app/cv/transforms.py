"""Bounding box and spatial coordinate transformation utilities."""

from typing import Tuple, Dict, Any, Optional
from pydantic import BaseModel, Field
from app.schemas.finding import BoundingBox


class TransformationMetadata(BaseModel):
    """Encapsulates spatial scaling and padding parameters applied during letterbox resizing."""

    originalWidth: int = Field(ge=1, description="Original image width in pixels")
    originalHeight: int = Field(ge=1, description="Original image height in pixels")
    targetWidth: int = Field(ge=1, description="Target model canvas width in pixels")
    targetHeight: int = Field(ge=1, description="Target model canvas height in pixels")
    scaledWidth: int = Field(ge=1, description="Width after aspect-preserving scaling")
    scaledHeight: int = Field(ge=1, description="Height after aspect-preserving scaling")
    scaleFactor: float = Field(gt=0.0, description="Scaling factor applied to original image")
    padLeft: int = Field(ge=0, description="Padding added to left border")
    padRight: int = Field(ge=0, description="Padding added to right border")
    padTop: int = Field(ge=0, description="Padding added to top border")
    padBottom: int = Field(ge=0, description="Padding added to bottom border")
    colorFormat: str = Field(default="RGB", description="Channel order of preprocessed matrix")
    normalized: bool = Field(default=True, description="True if pixel values are scaled to [0.0, 1.0]")
    durationMs: float = Field(default=0.0, description="Preprocessing duration in milliseconds")


class BoundingBoxTransformer:
    """Transforms bounding box coordinates between letterboxed model space and original image space."""

    @staticmethod
    def letterbox_to_original(
        box: BoundingBox,
        meta: TransformationMetadata,
    ) -> BoundingBox:
        """Maps normalized bounding box from letterbox model space [0.0, 1.0] to original image space [0.0, 1.0].

        Un-pads and un-scales coordinates so future Phase 3 findings accurately reference the original photograph.
        """
        # 1. Convert normalized letterbox coords to absolute letterbox pixels
        x_min_px = box.xMin * meta.targetWidth
        y_min_px = box.yMin * meta.targetHeight
        x_max_px = box.xMax * meta.targetWidth
        y_max_px = box.yMax * meta.targetHeight

        # 2. Subtract padding and divide by scale to recover original pixel coordinates
        orig_x_min = (x_min_px - meta.padLeft) / meta.scaleFactor
        orig_y_min = (y_min_px - meta.padTop) / meta.scaleFactor
        orig_x_max = (x_max_px - meta.padLeft) / meta.scaleFactor
        orig_y_max = (y_max_px - meta.padTop) / meta.scaleFactor

        # 3. Normalize back to original image dimensions [0.0, 1.0] and clamp
        norm_x_min = max(0.0, min(1.0, orig_x_min / meta.originalWidth))
        norm_y_min = max(0.0, min(1.0, orig_y_min / meta.originalHeight))
        norm_x_max = max(0.0, min(1.0, orig_x_max / meta.originalWidth))
        norm_y_max = max(0.0, min(1.0, orig_y_max / meta.originalHeight))

        # Ensure min <= max
        norm_x_min, norm_x_max = min(norm_x_min, norm_x_max), max(norm_x_min, norm_x_max)
        norm_y_min, norm_y_max = min(norm_y_min, norm_y_max), max(norm_y_min, norm_y_max)

        return BoundingBox(
            xMin=round(norm_x_min, 4),
            yMin=round(norm_y_min, 4),
            xMax=round(norm_x_max, 4),
            yMax=round(norm_y_max, 4),
        )

    @staticmethod
    def original_to_letterbox(
        box: BoundingBox,
        meta: TransformationMetadata,
    ) -> BoundingBox:
        """Maps normalized bounding box from original image space [0.0, 1.0] to letterbox model space [0.0, 1.0]."""
        # 1. Convert normalized original coords to original pixel values
        orig_x_min = box.xMin * meta.originalWidth
        orig_y_min = box.yMin * meta.originalHeight
        orig_x_max = box.xMax * meta.originalWidth
        orig_y_max = box.yMax * meta.originalHeight

        # 2. Scale and add padding in letterbox space
        lb_x_min = (orig_x_min * meta.scaleFactor) + meta.padLeft
        lb_y_min = (orig_y_min * meta.scaleFactor) + meta.padTop
        lb_x_max = (orig_x_max * meta.scaleFactor) + meta.padLeft
        lb_y_max = (orig_y_max * meta.scaleFactor) + meta.padTop

        # 3. Normalize to target dimensions [0.0, 1.0] and clamp
        norm_x_min = max(0.0, min(1.0, lb_x_min / meta.targetWidth))
        norm_y_min = max(0.0, min(1.0, lb_y_min / meta.targetHeight))
        norm_x_max = max(0.0, min(1.0, lb_x_max / meta.targetWidth))
        norm_y_max = max(0.0, min(1.0, lb_y_max / meta.targetHeight))

        return BoundingBox(
            xMin=round(norm_x_min, 4),
            yMin=round(norm_y_min, 4),
            xMax=round(norm_x_max, 4),
            yMax=round(norm_y_max, 4),
        )
