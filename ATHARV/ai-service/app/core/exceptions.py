"""Centralized exceptions and error handlers for FireSphere AI Service."""

from typing import Any, Dict, Optional
from fastapi import Request, status
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from app.core.logging import logger


class FireSphereAIException(Exception):
    """Base exception for all domain-specific AI service errors."""

    def __init__(
        self,
        message: str,
        status_code: int = status.HTTP_500_INTERNAL_SERVER_ERROR,
        error_code: str = "AI_INTERNAL_ERROR",
        details: Optional[Dict[str, Any]] = None,
    ):
        super().__init__(message)
        self.message = message
        self.status_code = status_code
        self.error_code = error_code
        self.details = details or {}


class InvalidInspectionException(FireSphereAIException):
    """Raised when inspection ID is invalid or cannot be verified."""

    def __init__(self, message: str = "Invalid inspection identifier provided", details: Optional[Dict[str, Any]] = None):
        super().__init__(
            message=message,
            status_code=status.HTTP_400_BAD_REQUEST,
            error_code="INVALID_INSPECTION_ID",
            details=details,
        )


class InvalidEvidenceException(FireSphereAIException):
    """Raised when evidence IDs are invalid, missing, or inaccessible."""

    def __init__(self, message: str = "Invalid or inaccessible evidence identifiers provided", details: Optional[Dict[str, Any]] = None):
        super().__init__(
            message=message,
            status_code=status.HTTP_400_BAD_REQUEST,
            error_code="INVALID_EVIDENCE_ID",
            details=details,
        )


class EvidenceNotFoundException(FireSphereAIException):
    """Raised when an evidence ID cannot be found in the evidence repository."""

    def __init__(self, message: str = "Inspection evidence item not found in storage", details: Optional[Dict[str, Any]] = None):
        super().__init__(
            message=message,
            status_code=status.HTTP_404_NOT_FOUND,
            error_code="EVIDENCE_NOT_FOUND",
            details=details,
        )


class EvidenceOversizedException(FireSphereAIException):
    """Raised when an evidence file exceeds maximum size limits."""

    def __init__(self, message: str = "Evidence file exceeds maximum allowed size", details: Optional[Dict[str, Any]] = None):
        super().__init__(
            message=message,
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            error_code="EVIDENCE_OVERSIZED",
            details=details,
        )


class ImageDecodeException(FireSphereAIException):
    """Raised when image bytes cannot be decoded into a valid image matrix."""

    def __init__(self, message: str = "Failed to decode evidence into valid image matrix", details: Optional[Dict[str, Any]] = None):
        super().__init__(
            message=message,
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            error_code="IMAGE_DECODE_FAILED",
            details=details,
        )


class ImageQualityException(FireSphereAIException):
    """Raised when image fails critical quality requirements (e.g. unreadable, 0x0)."""

    def __init__(self, message: str = "Evidence image does not meet minimum quality requirements", details: Optional[Dict[str, Any]] = None):
        super().__init__(
            message=message,
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            error_code="IMAGE_QUALITY_ERROR",
            details=details,
        )


class UnsupportedInputException(FireSphereAIException):
    """Raised when evidence format or media type is unsupported."""

    def __init__(self, message: str = "Unsupported evidence media format or empty payload", details: Optional[Dict[str, Any]] = None):
        super().__init__(
            message=message,
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            error_code="UNSUPPORTED_INPUT",
            details=details,
        )


class PipelineProcessingException(FireSphereAIException):
    """Raised when the AI CV/ML pipeline experiences an internal processing error."""

    def __init__(self, message: str = "AI inspection analysis pipeline processing failed", details: Optional[Dict[str, Any]] = None):
        super().__init__(
            message=message,
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            error_code="AI_PROCESSING_FAILURE",
            details=details,
        )


class GeminiConfigurationException(FireSphereAIException):
    """Raised when Gemini VLM configuration is invalid or credentials are missing when enabled."""

    def __init__(self, message: str = "Invalid Gemini provider configuration", details: Optional[Dict[str, Any]] = None):
        super().__init__(
            message=message,
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            error_code="GEMINI_CONFIGURATION_ERROR",
            details=details,
        )


class GeminiProviderException(FireSphereAIException):
    """Raised when Gemini VLM provider fails during client initialization or inference."""

    def __init__(self, message: str = "Gemini provider operation failed", details: Optional[Dict[str, Any]] = None):
        super().__init__(
            message=message,
            status_code=status.HTTP_502_BAD_GATEWAY,
            error_code="GEMINI_PROVIDER_ERROR",
            details=details,
        )



async def firesphere_exception_handler(request: Request, exc: FireSphereAIException) -> JSONResponse:
    """Handles domain-specific FireSphereAI exceptions."""
    logger.warning(
        f"Domain exception on {request.method} {request.url.path} | "
        f"Code: {exc.error_code} | Msg: {exc.message}"
    )
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "status": "FAILED",
            "errorCode": exc.error_code,
            "message": exc.message,
            "details": exc.details,
        },
    )


async def validation_exception_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
    """Handles Pydantic request validation errors without leaking sensitive internal details."""
    errors = []
    for err in exc.errors():
        field = ".".join(str(loc) for loc in err.get("loc", []) if loc != "body")
        msg = err.get("msg", "Invalid value")
        errors.append({"field": field or "payload", "issue": msg})

    logger.warning(
        f"Request validation failed on {request.method} {request.url.path} | "
        f"Errors: {len(errors)}"
    )

    return JSONResponse(
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
        content={
            "status": "FAILED",
            "errorCode": "VALIDATION_ERROR",
            "message": "Request payload failed validation schema.",
            "validationErrors": errors,
        },
    )


async def unhandled_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    """Fallback handler for unhandled exceptions.

    Never exposes stack traces, paths, or secrets to client.
    """
    logger.error(
        f"Unhandled server error on {request.method} {request.url.path}: {type(exc).__name__}",
        exc_info=True,
    )
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={
            "status": "FAILED",
            "errorCode": "INTERNAL_SERVER_ERROR",
            "message": "An unexpected error occurred during AI processing. Manual inspection workflow remains unaffected.",
        },
    )
