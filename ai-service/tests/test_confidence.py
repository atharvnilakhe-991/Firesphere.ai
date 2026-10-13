"""Tests for ConfidenceInterpreter, bounds validation, and calibration architecture."""

import pytest
from app.intelligence.confidence import (
    ConfidenceInterpreter,
    IsotonicRegressionCalibrator,
    TemperatureScalingCalibrator,
    UncalibratedPassThroughCalibrator,
    default_calibrator,
)


def test_confidence_bounds_validation():
    """Verifies that confidence scores within [0.0, 1.0] are accepted, and out of bounds raise ValueError."""
    assert ConfidenceInterpreter.validate_bounds(0.0) == 0.0
    assert ConfidenceInterpreter.validate_bounds(0.55) == 0.55
    assert ConfidenceInterpreter.validate_bounds(1.0) == 1.0

    with pytest.raises(ValueError, match="out of bounds"):
        ConfidenceInterpreter.validate_bounds(-0.01)

    with pytest.raises(ValueError, match="out of bounds"):
        ConfidenceInterpreter.validate_bounds(1.01)

    with pytest.raises(ValueError, match="must be a numerical float"):
        ConfidenceInterpreter.validate_bounds("invalid")  # type: ignore


def test_confidence_categorical_interpretation():
    """Verifies that confidence is categorized into VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH based on thresholds."""
    assert ConfidenceInterpreter.interpret(0.15) == "VERY_LOW"
    assert ConfidenceInterpreter.interpret(0.35) == "LOW"
    assert ConfidenceInterpreter.interpret(0.60) == "MEDIUM"
    assert ConfidenceInterpreter.interpret(0.75) == "HIGH"
    assert ConfidenceInterpreter.interpret(0.92) == "VERY_HIGH"


def test_uncalibrated_confidence_passthrough():
    """Verifies that uncalibrated calibrator returns identical score and explicitly reports NOT_CALIBRATED."""
    calibrated_val, status = default_calibrator.calibrate(0.82)
    assert calibrated_val == 0.82
    assert status == "NOT_CALIBRATED"

    calibrator = UncalibratedPassThroughCalibrator()
    val, stat = calibrator.calibrate(0.45)
    assert val == 0.45
    assert stat == "NOT_CALIBRATED"


def test_compliance_certainty_disclaimer():
    """Verifies that compliance certainty disclaimer explicitly states confidence does not equal legal certainty."""
    disclaimer = ConfidenceInterpreter.get_compliance_certainty_disclaimer()
    assert "NOT regulatory compliance certainty" in disclaimer
    assert "Fire Officer verification" in disclaimer


def test_future_calibrator_interfaces():
    """Verifies prepared interfaces for future temperature scaling and isotonic regression."""
    temp_cal = TemperatureScalingCalibrator(temperature=None)
    val, stat = temp_cal.calibrate(0.75)
    assert val == 0.75
    assert stat == "NOT_CALIBRATED"

    iso_cal = IsotonicRegressionCalibrator(is_fitted=False)
    val2, stat2 = iso_cal.calibrate(0.65)
    assert val2 == 0.65
    assert stat2 == "NOT_CALIBRATED"
