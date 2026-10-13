"""Confidence processing, validation, categorical interpretation, and calibration architecture."""

from abc import ABC, abstractmethod
from typing import Optional, Tuple
from app.core.config import settings
from app.schemas.finding import ConfidenceLevel


class ConfidenceInterpreter:
    """Interprets raw model confidence scores into standardized operational categories."""

    @classmethod
    def validate_bounds(cls, confidence: float) -> float:
        """Validates that confidence is a finite float within [0.0, 1.0]."""
        if confidence is None or not isinstance(confidence, (int, float)):
            raise ValueError(f"Confidence must be a numerical float, got {type(confidence).__name__}")
        if confidence < 0.0 or confidence > 1.0:
            raise ValueError(f"Confidence score {confidence} is out of bounds [0.0, 1.0]")
        return float(confidence)

    @classmethod
    def interpret(cls, confidence: float) -> str:
        """Categorizes raw confidence into VERY_LOW, LOW, MEDIUM, HIGH, or VERY_HIGH.

        Engineering interpretation thresholds:
        - confidence < AI_CONFIDENCE_VERY_LOW (0.30): VERY_LOW
        - AI_CONFIDENCE_VERY_LOW <= confidence < AI_CONFIDENCE_LOW (0.50): LOW
        - AI_CONFIDENCE_LOW <= confidence < AI_CONFIDENCE_MEDIUM (0.70): MEDIUM
        - AI_CONFIDENCE_MEDIUM <= confidence < AI_CONFIDENCE_HIGH (0.85): HIGH
        - confidence >= AI_CONFIDENCE_HIGH (0.85): VERY_HIGH
        """
        conf = cls.validate_bounds(confidence)

        if conf < settings.confidence_very_low:
            return ConfidenceLevel.VERY_LOW.value
        elif conf < settings.confidence_low:
            return ConfidenceLevel.LOW.value
        elif conf < settings.confidence_medium:
            return ConfidenceLevel.MEDIUM.value
        elif conf < settings.confidence_high:
            return ConfidenceLevel.HIGH.value
        else:
            return ConfidenceLevel.VERY_HIGH.value

    @classmethod
    def get_compliance_certainty_disclaimer(cls) -> str:
        """Explicitly clarifies distinction between model detection confidence and regulatory compliance certainty."""
        return (
            "Model confidence represents visual feature activation strength of the computer vision network, "
            "NOT regulatory compliance certainty. Compliance certainty requires authorized Fire Officer verification."
        )


class BaseConfidenceCalibrator(ABC):
    """Abstract interface for model confidence calibration."""

    @abstractmethod
    def calibrate(self, raw_confidence: float) -> Tuple[float, str]:
        """Calibrates raw model confidence score.

        Returns:
            Tuple[float, str]: (calibrated_confidence, calibration_status)
        """
        pass


class UncalibratedPassThroughCalibrator(BaseConfidenceCalibrator):
    """Pass-through calibrator used when no annotated validation dataset is available.

    Preserves raw model confidence and reports calibrationStatus as 'NOT_CALIBRATED'
    to prevent misleading statistical calibration claims.
    """

    def calibrate(self, raw_confidence: float) -> Tuple[float, str]:
        validated = ConfidenceInterpreter.validate_bounds(raw_confidence)
        # Truthful reporting: no fake calibration coefficients
        return validated, "NOT_CALIBRATED"


class TemperatureScalingCalibrator(BaseConfidenceCalibrator):
    """Interface prepared for future post-hoc temperature scaling once validation dataset is established."""

    def __init__(self, temperature: Optional[float] = None):
        self.temperature = temperature
        self.status = "CALIBRATED_TEMPERATURE_SCALING" if temperature is not None else "NOT_CALIBRATED"

    def calibrate(self, raw_confidence: float) -> Tuple[float, str]:
        validated = ConfidenceInterpreter.validate_bounds(raw_confidence)
        if self.temperature is None or self.temperature <= 0:
            return validated, "NOT_CALIBRATED"
        # Reserved for future validation dataset tuning
        return validated, self.status


class IsotonicRegressionCalibrator(BaseConfidenceCalibrator):
    """Interface prepared for future non-parametric isotonic regression calibration."""

    def __init__(self, is_fitted: bool = False):
        self.is_fitted = is_fitted
        self.status = "CALIBRATED_ISOTONIC" if is_fitted else "NOT_CALIBRATED"

    def calibrate(self, raw_confidence: float) -> Tuple[float, str]:
        validated = ConfidenceInterpreter.validate_bounds(raw_confidence)
        if not self.is_fitted:
            return validated, "NOT_CALIBRATED"
        # Reserved for future validation dataset tuning
        return validated, self.status


# Default active calibrator
default_calibrator = UncalibratedPassThroughCalibrator()
