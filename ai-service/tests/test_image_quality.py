"""Tests for image quality validation, resolution thresholds, blur, and lighting."""

import cv2
import numpy as np
import pytest
from app.core.exceptions import ImageQualityException
from app.cv.preprocessor import CVPreprocessor
from app.cv.quality import ImageQualityValidator


def test_acceptable_high_quality_image():
    """Verifies that a sharp, well-lit image of adequate resolution passes quality evaluation without warnings."""
    # Create 640x480 sharp image with high contrast edges
    img = np.full((480, 640, 3), (128, 128, 128), dtype=np.uint8)
    for i in range(0, 640, 40):
        cv2.line(img, (i, 0), (i, 480), (255, 255, 255), 3)
        cv2.line(img, (0, i % 480), (640, i % 480), (0, 0, 0), 3)

    report = ImageQualityValidator.evaluate(img)
    assert report.isAcceptable is True
    assert len(report.errors) == 0
    assert report.blurScore > 30.0  # Sharp edges yield high Laplacian variance


def test_low_resolution_image_fatal_error():
    """Verifies that an image below minimum resolution thresholds triggers a fatal error."""
    # Create 32x32 image (below 64x64 minimum)
    tiny_img = np.full((32, 32, 3), (100, 100, 100), dtype=np.uint8)

    report = ImageQualityValidator.evaluate(tiny_img, min_width=64, min_height=64)
    assert report.isAcceptable is False
    assert len(report.errors) > 0
    assert "below minimum required resolution" in report.errors[0]

    # Preprocessor should raise ImageQualityException
    preprocessor = CVPreprocessor()
    _, buf = cv2.imencode(".jpg", tiny_img)
    with pytest.raises(ImageQualityException):
        preprocessor.preprocess(buf.tobytes(), evidence_id="tiny-image")


def test_blurry_image_generates_warning():
    """Verifies that an extremely blurry image produces an advisory WARNING rather than crashing."""
    # Create flat/smooth image with gaussian blur
    smooth_img = np.full((200, 200, 3), (150, 150, 150), dtype=np.uint8)
    # A flat color image has zero Laplacian variance (maximum blur)
    report = ImageQualityValidator.evaluate(smooth_img)

    assert report.isAcceptable is True  # Processable, but advisory
    assert len(report.warnings) > 0
    assert any("sharpness" in w.lower() for w in report.warnings)

    preprocessor = CVPreprocessor()
    _, buf = cv2.imencode(".jpg", smooth_img)
    result = preprocessor.preprocess(buf.tobytes(), evidence_id="blurry-test")
    # Status should reflect advisory warning
    assert result.status == "WARNING"
    assert len(result.quality.warnings) > 0


def test_extreme_aspect_ratio_generates_warning():
    """Verifies that an image with an extreme aspect ratio (e.g. 12:1 panorama) flags a warning."""
    # Create 1200 x 100 image (aspect ratio 12.0)
    panoramic = np.full((100, 1200, 3), (100, 120, 140), dtype=np.uint8)
    # Add features for sharpness
    cv2.line(panoramic, (0, 50), (1200, 50), (255, 255, 255), 2)

    report = ImageQualityValidator.evaluate(panoramic)
    assert report.isAcceptable is True
    assert any("aspect ratio" in w.lower() for w in report.warnings)


def test_underexposed_image_generates_warning():
    """Verifies that a very dark/underexposed image flags a lighting warning."""
    # Create near-black image (mean intensity ~10)
    dark_img = np.full((200, 200, 3), (10, 10, 10), dtype=np.uint8)
    report = ImageQualityValidator.evaluate(dark_img)

    assert report.isAcceptable is True
    assert any("underexposed" in w.lower() for w in report.warnings)
