"""Retry policy, backoff calculation, and failure classification for AI jobs."""

import random
from typing import Set, Type
from app.core.exceptions import (
    EvidenceNotFoundException,
    EvidenceOversizedException,
    ImageDecodeException,
    ImageQualityException,
    InvalidEvidenceException,
    InvalidInspectionException,
    UnsupportedInputException,
)
from app.storage.s3_storage import IntegrityCheckException, StorageUnavailableException


class RetryPolicy:
    """Bounded exponential backoff and error classification policy for AI jobs."""

    NON_RETRYABLE_EXCEPTIONS: Set[Type[Exception]] = {
        InvalidInspectionException,
        InvalidEvidenceException,
        EvidenceNotFoundException,
        EvidenceOversizedException,
        ImageDecodeException,
        ImageQualityException,
        UnsupportedInputException,
        IntegrityCheckException,
        ValueError,
        TypeError,
    }

    RETRYABLE_EXCEPTIONS: Set[Type[Exception]] = {
        StorageUnavailableException,
        TimeoutError,
        ConnectionError,
        OSError,
    }

    @classmethod
    def is_retryable(cls, exc: Exception) -> bool:
        """Determines if an exception is considered transient and safe for retry.

        Deterministic input errors, corrupted files, and schema validation failures
        must never be retried.
        """
        # Explicit non-retryable takes precedence
        if any(isinstance(exc, non_retry) for non_retry in cls.NON_RETRYABLE_EXCEPTIONS):
            return False

        # Explicit retryable matches
        if any(isinstance(exc, retryable) for retryable in cls.RETRYABLE_EXCEPTIONS):
            return True

        # Check for timeout-like names
        exc_name = type(exc).__name__.lower()
        if "timeout" in exc_name or "connection" in exc_name:
            return True

        return False

    @staticmethod
    def compute_backoff_seconds(
        attempt: int,
        base_seconds: float = 1.0,
        max_seconds: float = 30.0,
        jitter: bool = True,
    ) -> float:
        """Calculates exponential backoff delay with randomized jitter."""
        if attempt <= 1:
            delay = base_seconds
        else:
            delay = min(max_seconds, base_seconds * (2 ** (attempt - 1)))

        if jitter:
            # Full jitter: random uniform between 0 and computed delay
            delay = random.uniform(base_seconds, delay + 1.0)

        return round(delay, 2)
