"""FireSphere.ai — Gemini VLM Schemas and Coordinate Contracts (Phase 2).

Defines structured response models, observation envelopes, explicit status enums,
and coordinate conversion/validation utilities for Gemini vision analysis.
"""

from enum import Enum
import math
from typing import Any, Dict, List, Optional, Tuple, Union
import uuid
from pydantic import BaseModel, Field

from app.schemas.finding import BoundingBox, FindingSeverity

# Official FireSphere Perception & Defect Taxonomy
SUPPORTED_OBJECT_CATEGORIES = {
    "FIRE_EXTINGUISHER",
    "EXIT_SIGN",
    "EXIT_DOOR",
    "OBSTACLE",
    "SPRINKLER_HEAD",
    "ELECTRICAL_PANEL",
    "MANUAL_CALL_POINT",
    "HOSE_REEL",
    "COMBUSTIBLE_MATERIAL",
    "EMERGENCY_LIGHTING",
    "FIRE_ALARM_PANEL",
}

SUPPORTED_HAZARD_CATEGORIES = {
    "FIRE_EXTINGUISHER_MISSING",
    "FIRE_EXTINGUISHER_BLOCKED",
    "FIRE_EXTINGUISHER_EXPIRED",
    "EMERGENCY_EXIT_OBSTRUCTED",
    "EXIT_SIGNAGE_MISSING",
    "SPRINKLER_HEAD_DEFECT",
    "ELECTRICAL_PANEL_HAZARD",
    "COMBUSTIBLE_MATERIAL_HAZARD",
    "MANUAL_CALL_POINT_BLOCKED",
    "HOSE_REEL_OBSTRUCTED",
    "GENERIC_FIRE_SAFETY_DEFECT",
    "NONE",
}

VALID_SEVERITIES = {
    "INFORMATIONAL",
    "LOW",
    "MEDIUM",
    "HIGH",
    "CRITICAL",
}


class GeminiAnalysisStatus(str, Enum):
    """Explicit lifecycle statuses for Gemini visual analysis execution.

    Distinguishes successful observation, successful empty, inconclusive evidence,
    invalid schema output, and technical provider failure.
    """

    GEMINI_DISABLED = "GEMINI_DISABLED"
    GEMINI_NOT_CONFIGURED = "GEMINI_NOT_CONFIGURED"
    ANALYSIS_COMPLETED_WITH_OBSERVATIONS = "ANALYSIS_COMPLETED_WITH_OBSERVATIONS"
    ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS = "ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS"
    ANALYSIS_INCONCLUSIVE = "ANALYSIS_INCONCLUSIVE"
    ANALYSIS_FAILED = "ANALYSIS_FAILED"
    ANALYSIS_INVALID_RESPONSE = "ANALYSIS_INVALID_RESPONSE"

    def __eq__(self, other: Any) -> bool:
        """Maintains backward compatibility with Phase 1 string status checks."""
        if self is GeminiAnalysisStatus.GEMINI_DISABLED and other in ("DISABLED", "GEMINI_DISABLED"):
            return True
        return super().__eq__(other)


class GeminiRawObservation(BaseModel):
    """Raw structured observation emitted by Gemini VLM during generation."""

    objectCategory: str = Field(description="Detected physical equipment or object class")
    potentialHazardCategory: Optional[str] = Field(
        default=None,
        description="Suggested fire safety hazard type or NONE",
    )
    observationDescription: str = Field(description="Objective natural-language reasoning of visible evidence")
    evidenceStatus: str = Field(
        default="DETECTED",
        description="Observation certainty: DETECTED, ABSENT, or UNCERTAIN",
    )
    box_2d: Optional[List[float]] = Field(
        default=None,
        description="2D bounding box [ymin, xmin, ymax, xmax] normalized on 0-1000 scale or null",
    )
    confidence: Optional[float] = Field(
        default=None,
        description="Model prediction confidence score [0.0 - 1.0] if available",
    )
    uncertaintyOrLimitations: List[str] = Field(
        default_factory=list,
        description="Documented optical limitations, occlusions, or image-quality caveats",
    )
    severityProposal: Optional[str] = Field(
        default=None,
        description="Suggested severity rating if a hazard is observed: INFORMATIONAL, LOW, MEDIUM, HIGH, CRITICAL",
    )


class GeminiRawResponse(BaseModel):
    """Envelope requested directly from Gemini VLM via structured output."""

    isInconclusive: bool = Field(
        default=False,
        description="True if the image does not provide sufficient visible evidence to evaluate",
    )
    inconclusiveReason: Optional[str] = Field(
        default=None,
        description="Reason why evidence is inconclusive (e.g., blurry, too dark, out of frame)",
    )
    observations: List[GeminiRawObservation] = Field(
        default_factory=list,
        description="List of structured visual observations extracted from the evidence",
    )


def parse_and_validate_bounding_box(
    box_raw: Any,
    image_dims: Optional[Tuple[int, int]] = None,
    coordinate_convention: Optional[str] = "gemini_0_1000",
) -> Optional[BoundingBox]:
    """Parses, normalizes, and strictly validates bounding box coordinates from Gemini VLM.

    Explicitly establishes the source coordinate convention rather than inferring
    arbitrarily from coordinate magnitudes alone.

    Supported Conventions:
    - 'gemini_0_1000': Official Gemini API standard [ymin, xmin, ymax, xmax] scaled to [0, 1000].
    - 'normalized': Already normalized float coordinates [0.0 - 1.0].
    - 'absolute_pixels': Absolute pixel values requiring verified (image_w, image_h).

    Rejection Rules:
    - Ambiguous coordinate scales (e.g. coordinates between 1.05 and 10.0 without established scale)
      are strictly REJECTED (returns None) rather than guessed.
    - Non-finite numbers (NaN, Inf) are rejected.
    - Zero or negative box area is rejected.
    - Inverted ordering (xmin >= xmax or ymin >= ymax) is rejected.
    - Missing boxes return None (NEVER fabricates synthetic boxes).
    """
    if box_raw is None:
        return None

    xmin: Optional[float] = None
    ymin: Optional[float] = None
    xmax: Optional[float] = None
    ymax: Optional[float] = None

    # Handle 4-element sequence [ymin, xmin, ymax, xmax] (standard Gemini 2D box format)
    if isinstance(box_raw, (list, tuple)) and len(box_raw) == 4:
        try:
            y0, x0, y1, x1 = float(box_raw[0]), float(box_raw[1]), float(box_raw[2]), float(box_raw[3])
            ymin, xmin, ymax, xmax = y0, x0, y1, x1
        except (ValueError, TypeError):
            return None

    # Handle dictionary representation
    elif isinstance(box_raw, dict):
        try:
            # Check for ymin, xmin, ymax, xmax
            if "ymin" in box_raw and "xmin" in box_raw and "ymax" in box_raw and "xmax" in box_raw:
                ymin = float(box_raw["ymin"])
                xmin = float(box_raw["xmin"])
                ymax = float(box_raw["ymax"])
                xmax = float(box_raw["xmax"])
            # Check for yMin, xMin, yMax, xMax
            elif "yMin" in box_raw and "xMin" in box_raw and "yMax" in box_raw and "xMax" in box_raw:
                ymin = float(box_raw["yMin"])
                xmin = float(box_raw["xMin"])
                ymax = float(box_raw["yMax"])
                xmax = float(box_raw["xMax"])
            # Check for xMin, yMin, xMax, yMax (internal FireSphere convention)
            elif "xMin" in box_raw and "yMin" in box_raw and "xMax" in box_raw and "yMax" in box_raw:
                xmin = float(box_raw["xMin"])
                ymin = float(box_raw["yMin"])
                xmax = float(box_raw["xMax"])
                ymax = float(box_raw["yMax"])
            else:
                return None
        except (ValueError, TypeError):
            return None
    else:
        return None

    # Ensure all coordinates are finite numbers
    if not all(math.isfinite(c) for c in (xmin, ymin, xmax, ymax)):
        return None

    coords = [xmin, ymin, xmax, ymax]
    max_c = max(coords)
    min_c = min(coords)

    # Establish coordinate scaling according to verified convention
    convention = (coordinate_convention or "gemini_0_1000").lower().strip()

    if convention == "normalized":
        # Must be strictly normalized [0.0 - 1.0] with small boundary rounding tolerance
        if max_c > 1.05 or min_c < -0.05:
            return None
    elif convention == "absolute_pixels":
        if not image_dims or image_dims[0] <= 0 or image_dims[1] <= 0:
            return None
        img_w, img_h = image_dims
        if max_c > max(img_w, img_h) * 1.05 or min_c < -50.0:
            return None
        xmin /= float(img_w)
        ymin /= float(img_h)
        xmax /= float(img_w)
        ymax /= float(img_h)
    else:
        # Default 'gemini_0_1000' convention:
        # If coordinates are already normalized floats (<= 1.05 and >= -0.05)
        if max_c <= 1.05 and min_c >= -0.05:
            pass  # Already normalized float representation
        # If coordinates are in verified 0-1000 integer range (max >= 10.0 and <= 1050.0, min >= -50.0)
        elif max_c <= 1050.0 and min_c >= -50.0 and max_c >= 10.0:
            xmin /= 1000.0
            ymin /= 1000.0
            xmax /= 1000.0
            ymax /= 1000.0
        # If image dimensions provided and values align with pixel coordinates
        elif image_dims and image_dims[0] > 0 and image_dims[1] > 0 and max_c <= max(image_dims) * 1.05:
            img_w, img_h = image_dims
            xmin /= float(img_w)
            ymin /= float(img_h)
            xmax /= float(img_w)
            ymax /= float(img_h)
        else:
            # Ambiguous coordinate system: reject rather than placing finding at arbitrary location
            return None

    # Check for severely invalid coordinate values after normalization
    if any(c < -0.05 or c > 1.05 for c in (xmin, ymin, xmax, ymax)):
        return None



    # Clamp minor boundary rounding deviations into valid [0.0, 1.0] interval
    xmin = max(0.0, min(1.0, xmin))
    ymin = max(0.0, min(1.0, ymin))
    xmax = max(0.0, min(1.0, xmax))
    ymax = max(0.0, min(1.0, ymax))

    # Validate coordinate ordering: xmin < xmax and ymin < ymax
    if xmin >= xmax or ymin >= ymax:
        return None

    # Enforce positive area
    area = (xmax - xmin) * (ymax - ymin)
    if area <= 0.0:
        return None

    return BoundingBox(
        xMin=round(xmin, 4),
        yMin=round(ymin, 4),
        xMax=round(xmax, 4),
        yMax=round(ymax, 4),
    )


class GeminiObservation(BaseModel):
    """Validated visual observation candidate produced by Gemini VLM.

    Advisory only. Designed for Phase 2 visual reasoning output and Phase 3
    evidence validation, conflict resolution, and mandatory human officer verification.
    """

    observationId: str = Field(
        default_factory=lambda: str(uuid.uuid4()),
        description="Unique identifier for this visual observation",
    )
    imageReference: str = Field(description="Evidence identifier or image reference")
    objectCategory: str = Field(description="Visual object category (e.g., FIRE_EXTINGUISHER, EXIT_SIGN)")
    potentialHazardCategory: Optional[str] = Field(
        default=None,
        description="Suggested fire safety hazard classification if an anomaly or defect is detected",
    )
    observationDescription: str = Field(default="", description="Descriptive visual reasoning and factual evidence summary")
    description: Optional[str] = Field(default=None, description="Observation description alias")
    evidenceStatus: str = Field(
        default="DETECTED",
        description="Observation status: DETECTED, ABSENT, UNCERTAIN, or NOT_EVALUATED",
    )
    boundingBox: Optional[BoundingBox] = Field(
        default=None,
        description="Normalized spatial bounding coordinates when visual localization is supported",
    )
    confidence: Optional[float] = Field(
        default=None,
        ge=0.0,
        le=1.0,
        description="Visual reasoning confidence score [0.0 - 1.0]",
    )
    severityProposal: Optional[str] = Field(
        default=None,
        description="Advisory severity proposal (subject to authorized officer confirmation)",
    )
    potentialHazardSeverity: Optional[str] = Field(
        default=None,
        description="Suggested hazard severity alias",
    )
    modelName: str = Field(default="gemini-2.5-flash", description="Configured Gemini model ID used for inference")
    modelVersion: str = Field(default="gemini-genai-v2", description="Provider adapter version")
    analysisStatus: str = Field(default="COMPLETED", description="Status of the VLM analysis")
    uncertaintyOrLimitations: List[str] = Field(
        default_factory=list,
        description="Documented visual limitations, occlusions, or image-quality caveats",
    )
    officerReviewRequirement: str = Field(
        default="MANDATORY_OFFICER_REVIEW",
        description="Safety boundary: All AI visual observations require human officer verification",
    )


class GeminiAnalysisResult(BaseModel):
    """Result envelope for Gemini VLM inspection analysis."""

    evidenceId: Union[str, uuid.UUID] = Field(description="Inspection evidence item identifier")
    model: str = Field(description="Configured Gemini model identifier")
    status: GeminiAnalysisStatus = Field(
        description="Analysis lifecycle status: GEMINI_DISABLED, GEMINI_NOT_CONFIGURED, "
                    "ANALYSIS_COMPLETED_WITH_OBSERVATIONS, ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS, "
                    "ANALYSIS_INCONCLUSIVE, ANALYSIS_FAILED, or ANALYSIS_INVALID_RESPONSE"
    )
    observations: List[GeminiObservation] = Field(
        default_factory=list,
        description="Structured visual observations extracted by the model",
    )
    rawResponse: Optional[Dict[str, Any]] = Field(
        default=None,
        description="Sanitized structured model output if available",
    )
    inconclusiveReason: Optional[str] = Field(
        default=None,
        description="Documented visual reason when analysis is inconclusive",
    )
    error: Optional[str] = Field(
        default=None,
        description="Sanitized error message if analysis failed or was skipped",
    )
    executionDurationMs: float = Field(
        default=0.0,
        description="Execution latency in milliseconds",
    )
    requiresOfficerVerification: bool = Field(
        default=True,
        description="Safety invariant: AI outputs must be verified by an authorized human officer",
    )
    isSafeInference: bool = Field(
        default=False,
        description="Safety invariant: Absence of observations does not certify that the inspected area is safe",
    )
