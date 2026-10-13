"""File and media content validator for inspection evidence."""

from typing import Optional, Tuple
from app.core.config import settings
from app.core.exceptions import (
    EvidenceOversizedException,
    UnsupportedInputException,
)
from app.core.logging import logger


class FileValidator:
    """Validates raw evidence payloads against supported image signatures, sizes, and integrity."""

    # Approved MIME types for FireSphere inspection photography
    SUPPORTED_MIME_TYPES = {
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp",
    }

    # Binary magic number signatures
    SIGNATURE_JPEG = b"\xff\xd8\xff"
    SIGNATURE_PNG = b"\x89PNG\r\n\x1a\n"
    SIGNATURE_RIFF = b"RIFF"
    SIGNATURE_WEBP = b"WEBP"

    @classmethod
    def detect_format_from_bytes(cls, raw_bytes: bytes) -> Optional[str]:
        """Detects image format by inspecting binary file magic signatures.

        Never trusts file extensions blindly.
        """
        if not raw_bytes or len(raw_bytes) < 12:
            return None

        # JPEG signature check
        if raw_bytes.startswith(cls.SIGNATURE_JPEG):
            return "image/jpeg"

        # PNG signature check
        if raw_bytes.startswith(cls.SIGNATURE_PNG):
            return "image/png"

        # WebP signature check: "RIFF....WEBP"
        if raw_bytes.startswith(cls.SIGNATURE_RIFF) and raw_bytes[8:12] == cls.SIGNATURE_WEBP:
            return "image/webp"

        return None

    @classmethod
    def validate_payload(
        cls,
        evidence_id: str,
        raw_bytes: bytes,
        declared_mime_type: Optional[str] = None,
        max_size_bytes: Optional[int] = None,
    ) -> Tuple[str, int]:
        """Performs rigorous validation of the evidence payload.

        Returns (detected_format, size_bytes).
        Raises EvidenceOversizedException or UnsupportedInputException on failure.
        """
        max_allowed = max_size_bytes or settings.max_image_size_bytes

        # 1. Check for empty payload
        if not raw_bytes or len(raw_bytes) == 0:
            logger.warning(f"Rejected empty evidence payload | evidenceId={evidence_id}")
            raise UnsupportedInputException(
                message=f"Evidence payload for {evidence_id} is empty (0 bytes).",
                details={"evidenceId": evidence_id, "sizeBytes": 0},
            )

        # 2. Check file size limits
        size_bytes = len(raw_bytes)
        if size_bytes > max_allowed:
            logger.warning(
                f"Rejected oversized evidence | evidenceId={evidence_id} | "
                f"size={size_bytes}B > max={max_allowed}B"
            )
            raise EvidenceOversizedException(
                message=f"Evidence payload ({size_bytes} bytes) exceeds limit of {max_allowed} bytes.",
                details={"evidenceId": evidence_id, "sizeBytes": size_bytes, "maxAllowed": max_allowed},
            )

        # 3. Detect binary content format
        detected_mime = cls.detect_format_from_bytes(raw_bytes)
        if not detected_mime or detected_mime not in cls.SUPPORTED_MIME_TYPES:
            logger.warning(
                f"Unsupported file format rejected | evidenceId={evidence_id} | "
                f"declared={declared_mime_type} | detected={detected_mime}"
            )
            raise UnsupportedInputException(
                message=(
                    f"Unsupported or unrecognized file format for evidence {evidence_id}. "
                    "Only JPEG, PNG, and WebP inspection photographs are supported."
                ),
                details={
                    "evidenceId": evidence_id,
                    "declaredMimeType": declared_mime_type,
                    "detectedMimeType": detected_mime,
                },
            )

        # 4. Cross-check with declared MIME if provided and incompatible
        if declared_mime_type:
            norm_declared = declared_mime_type.lower().strip()
            # If declared format is completely different (e.g. application/pdf, text/html)
            if not norm_declared.startswith("image/") and norm_declared not in cls.SUPPORTED_MIME_TYPES:
                logger.warning(
                    f"MIME type mismatch | evidenceId={evidence_id} | "
                    f"declared={norm_declared} != detected={detected_mime}"
                )
                raise UnsupportedInputException(
                    message=f"Declared media type '{declared_mime_type}' is unsupported.",
                    details={"evidenceId": evidence_id, "declared": declared_mime_type},
                )

        return detected_mime, size_bytes
