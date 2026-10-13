"""Tests for OpenCV image decoding, letterboxing, color conversion, and normalization."""

import cv2
import numpy as np
import pytest
from app.cv.preprocessor import CVPreprocessor


def create_solid_image_bytes(width: int, height: int, bgr_color: tuple = (255, 0, 0)) -> bytes:
    """Creates a test JPEG image with known BGR color (default pure blue in BGR)."""
    img = np.full((height, width, 3), bgr_color, dtype=np.uint8)
    # Add a cross pattern so it's not totally flat
    cv2.line(img, (0, 0), (width, height), (0, 255, 0), 2)
    success, buf = cv2.imencode(".jpg", img)
    assert success
    return buf.tobytes()


def test_cv_decode_dimensions():
    """Verifies that an image is decoded into correct original dimensions."""
    orig_w, orig_h = 400, 300
    raw_bytes = create_solid_image_bytes(width=orig_w, height=orig_h)

    preprocessor = CVPreprocessor()
    result = preprocessor.preprocess(raw_bytes, target_size=(640, 640), evidence_id="dim-test")

    assert result.metadata.originalWidth == orig_w
    assert result.metadata.originalHeight == orig_h
    assert result.metadata.targetWidth == 640
    assert result.metadata.targetHeight == 640


def test_letterbox_aspect_ratio_preservation():
    """Verifies that letterboxing preserves aspect ratio and calculates symmetric padding."""
    preprocessor = CVPreprocessor()

    # Wide image (800x400) -> aspect 2:1
    wide_bytes = create_solid_image_bytes(width=800, height=400)
    res_wide = preprocessor.preprocess(wide_bytes, target_size=(640, 640), evidence_id="wide-test")

    # In a 640x640 canvas, 800x400 scales by 640/800 = 0.8
    # Scaled dimensions: 640 x 320. Top/bottom padding = (640 - 320) / 2 = 160 each.
    assert res_wide.metadata.scaledWidth == 640
    assert res_wide.metadata.scaledHeight == 320
    assert res_wide.metadata.padLeft == 0
    assert res_wide.metadata.padRight == 0
    assert res_wide.metadata.padTop == 160
    assert res_wide.metadata.padBottom == 160
    assert res_wide.preprocessed_array.shape == (640, 640, 3)

    # Tall image (300x600) -> aspect 1:2
    tall_bytes = create_solid_image_bytes(width=300, height=600)
    res_tall = preprocessor.preprocess(tall_bytes, target_size=(640, 640), evidence_id="tall-test")

    # In a 640x640 canvas, 300x600 scales by 640/600 = 1.0667 -> 320 x 640.
    # Left/right padding = (640 - 320) / 2 = 160 each.
    assert res_tall.metadata.scaledHeight == 640
    assert res_tall.metadata.padTop == 0
    assert res_tall.metadata.padBottom == 0
    assert res_tall.metadata.padLeft == 160
    assert res_tall.metadata.padRight == 160
    assert res_tall.preprocessed_array.shape == (640, 640, 3)


def test_color_conversion_bgr_to_rgb():
    """Verifies that OpenCV BGR decoded images are correctly converted to RGB."""
    preprocessor = CVPreprocessor()
    # Create pure BGR Blue: (B=255, G=0, R=0)
    # When converted to RGB, it must be (R=0, G=0, B=255)
    bgr_blue_img = np.zeros((100, 100, 3), dtype=np.uint8)
    bgr_blue_img[:, :, 0] = 255  # Blue channel in BGR
    # Add small border marker for edge contrast without occluding center
    cv2.line(bgr_blue_img, (0, 0), (20, 0), (0, 255, 0), 2)
    _, buf = cv2.imencode(".png", bgr_blue_img)

    result = preprocessor.preprocess(
        raw_bytes=buf.tobytes(),
        target_size=(128, 128),
        normalize=False,  # Keep uint8 to verify raw channel values
    )

    # Check center pixel in the letterboxed canvas (avoid padding area and border marker)
    center_pixel = result.preprocessed_array[64, 64]
    # In RGB: channel 0 is R (0), channel 1 is G (0), channel 2 is B (255)
    assert center_pixel[0] == 0    # Red
    assert center_pixel[1] == 0    # Green
    assert center_pixel[2] == 255  # Blue
    assert result.metadata.colorFormat == "RGB"


def test_pixel_normalization():
    """Verifies that normalization scales pixel values to float32 range [0.0, 1.0]."""
    preprocessor = CVPreprocessor()
    raw_bytes = create_solid_image_bytes(width=128, height=128)

    result = preprocessor.preprocess(
        raw_bytes=raw_bytes,
        target_size=(640, 640),
        normalize=True,
    )

    arr = result.preprocessed_array
    assert arr.dtype == np.float32
    assert arr.min() >= 0.0
    assert arr.max() <= 1.0
    assert result.metadata.normalized is True


def test_configurable_target_dimensions():
    """Verifies that target letterbox dimensions can be custom specified."""
    preprocessor = CVPreprocessor()
    raw_bytes = create_solid_image_bytes(width=200, height=200)

    # Custom target size: 512x384
    result = preprocessor.preprocess(
        raw_bytes=raw_bytes,
        target_size=(512, 384),
        evidence_id="custom-dim",
    )

    assert result.metadata.targetWidth == 512
    assert result.metadata.targetHeight == 384
    assert result.preprocessed_array.shape == (384, 512, 3)
