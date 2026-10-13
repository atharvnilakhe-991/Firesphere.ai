"""Tests for evidence validation, magic byte signatures, and payload limits."""

import uuid
import cv2
import numpy as np
import pytest
from app.core.config import settings
from app.core.exceptions import (
    EvidenceNotFoundException,
    EvidenceOversizedException,
    ImageDecodeException,
    InvalidEvidenceException,
    UnsupportedInputException,
)
from app.cv.file_validator import FileValidator
from app.cv.preprocessor import CVPreprocessor
from app.storage.evidence_client import EvidenceClient


def make_test_image_bytes(fmt: str = ".jpg", width: int = 200, height: int = 150) -> bytes:
    """Helper to synthesize valid image bytes."""
    img = np.full((height, width, 3), (120, 150, 180), dtype=np.uint8)
    cv2.circle(img, (width // 2, height // 2), 30, (0, 255, 0), -1)
    success, buf = cv2.imencode(fmt, img)
    assert success
    return buf.tobytes()


def test_magic_byte_format_detection():
    """Verifies that JPEG, PNG, and WebP are accurately detected from binary headers."""
    jpeg_bytes = make_test_image_bytes(".jpg")
    png_bytes = make_test_image_bytes(".png")
    webp_bytes = make_test_image_bytes(".webp")

    assert FileValidator.detect_format_from_bytes(jpeg_bytes) == "image/jpeg"
    assert FileValidator.detect_format_from_bytes(png_bytes) == "image/png"
    assert FileValidator.detect_format_from_bytes(webp_bytes) == "image/webp"


def test_unsupported_file_types_rejected():
    """Verifies that non-image payloads (PDF, SVG, HTML, plain text) are rejected."""
    unsupported_payloads = [
        (b"%PDF-1.4 sample pdf content", "application/pdf"),
        (b"<svg xmlns='http://www.w3.org/2000/svg'></svg>", "image/svg+xml"),
        (b"<html><body>Not an image</body></html>", "text/html"),
        (b"Plain text report instead of photo evidence", "text/plain"),
        (b"MZ\x90\x00\x03\x00\x00\x00Binary payload", "application/octet-stream"),
    ]

    for payload, mime in unsupported_payloads:
        with pytest.raises(UnsupportedInputException):
            FileValidator.validate_payload(
                evidence_id=str(uuid.uuid4()),
                raw_bytes=payload,
                declared_mime_type=mime,
            )


def test_empty_file_rejected():
    """Verifies that an empty payload (0 bytes) raises UnsupportedInputException."""
    with pytest.raises(UnsupportedInputException):
        FileValidator.validate_payload(
            evidence_id=str(uuid.uuid4()),
            raw_bytes=b"",
        )


def test_oversized_file_rejected():
    """Verifies that a payload exceeding max_image_size_bytes raises EvidenceOversizedException."""
    test_id = uuid.uuid4()
    small_limit = 1024  # 1 KB limit for testing
    oversized_payload = b"\xff\xd8\xff" + b"\x00" * 2048

    with pytest.raises(EvidenceOversizedException):
        FileValidator.validate_payload(
            evidence_id=str(test_id),
            raw_bytes=oversized_payload,
            max_size_bytes=small_limit,
        )


def test_corrupted_image_decode_failure():
    """Verifies that a corrupted or truncated image matrix raises ImageDecodeException."""
    preprocessor = CVPreprocessor()
    corrupted_bytes = b"\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x00\x00\x01" + b"\x00" * 20

    with pytest.raises(ImageDecodeException):
        preprocessor.preprocess(
            raw_bytes=corrupted_bytes,
            evidence_id=str(uuid.uuid4()),
        )


def test_missing_evidence_raises_not_found():
    """Verifies that querying an unregistered evidence item raises EvidenceNotFoundException when mock loader is disabled."""
    storage = EvidenceClient()
    missing_id = uuid.uuid4()

    # Temporarily disable mock fallback to test missing evidence behavior
    original_setting = settings.enable_mock_evidence_loader
    try:
        settings.enable_mock_evidence_loader = False
        with pytest.raises(EvidenceNotFoundException):
            storage.fetch_evidence_bytes(missing_id)
    finally:
        settings.enable_mock_evidence_loader = original_setting


def test_invalid_evidence_id_null():
    """Verifies that passing null or invalid evidence IDs raises InvalidEvidenceException."""
    storage = EvidenceClient()
    with pytest.raises(InvalidEvidenceException):
        storage.fetch_evidence_bytes(None)  # type: ignore

    with pytest.raises(InvalidEvidenceException):
        storage.get_evidence_metadata(None)  # type: ignore
