"""S3 and MinIO-compatible object storage provider with integrity validation."""

from abc import ABC, abstractmethod
import hashlib
import hmac
import re
from typing import Any, Dict, Optional
from app.core.config import settings
from app.core.error_codes import AIErrorCode
from app.core.exceptions import (
    EvidenceNotFoundException,
    EvidenceOversizedException,
    FireSphereAIException,
    InvalidEvidenceException,
)
from app.core.logging import logger


class IntegrityCheckException(FireSphereAIException):
    """Raised when downloaded evidence fails cryptographic checksum verification."""

    def __init__(self, message: str = "Evidence integrity verification failed"):
        super().__init__(
            message=message,
            status_code=422,
            error_code=AIErrorCode.INTEGRITY_CHECK_FAILED.value,
        )


class StorageUnavailableException(FireSphereAIException):
    """Raised when object storage is unreachable or encounters timeout."""

    def __init__(self, message: str = "Object storage service is temporarily unavailable"):
        super().__init__(
            message=message,
            status_code=503,
            error_code=AIErrorCode.STORAGE_UNAVAILABLE.value,
        )


class BaseStorageProvider(ABC):
    """Abstract base class for evidence object storage providers."""

    @abstractmethod
    def fetch_object(self, bucket: str, key: str, expected_checksum: Optional[str] = None) -> bytes:
        """Retrieves raw binary object from storage and verifies integrity if checksum provided."""
        pass

    @abstractmethod
    def get_object_metadata(self, bucket: str, key: str) -> Dict[str, Any]:
        """Fetches object metadata without exposing internal URLs or credentials."""
        pass

    @staticmethod
    def compute_sha256(data: bytes) -> str:
        """Computes SHA-256 hex digest of raw binary payload."""
        return hashlib.sha256(data).hexdigest()

    @classmethod
    def verify_checksum(cls, data: bytes, expected_checksum: Optional[str]) -> bool:
        """Verifies binary payload against expected SHA-256 checksum."""
        if not expected_checksum:
            return True
        actual = cls.compute_sha256(data)
        return hmac.compare_digest(actual.lower(), expected_checksum.strip().lower())

    @staticmethod
    def sanitize_key(key: str) -> str:
        """Sanitizes object storage key, preventing path traversal and arbitrary URL injection."""
        if not key or not isinstance(key, str):
            raise InvalidEvidenceException("Invalid storage key provided.")

        clean_key = key.strip()
        # Disallow protocol prefixes (http://, https://, file://)
        if re.match(r"^[a-zA-Z0-9]+://", clean_key):
            raise InvalidEvidenceException("Arbitrary URL storage keys are strictly forbidden.")

        # Disallow path traversal attempts
        if ".." in clean_key or clean_key.startswith("/") or clean_key.startswith("\\"):
            raise InvalidEvidenceException("Path traversal patterns in storage keys are forbidden.")

        return clean_key


class MockStorageProvider(BaseStorageProvider):
    """In-memory storage provider for testing and offline local execution."""

    def __init__(self):
        self._store: Dict[str, bytes] = {}
        self._metadata: Dict[str, Dict[str, Any]] = {}
        self._simulate_failure: bool = False

    def set_simulate_failure(self, fail: bool) -> None:
        """Configures simulated transient storage failure for retry testing."""
        self._simulate_failure = fail

    def store_object(
        self,
        bucket: str,
        key: str,
        data: bytes,
        media_type: str = "image/jpeg",
    ) -> None:
        """Stores an object in the in-memory test store."""
        clean_key = self.sanitize_key(key)
        storage_path = f"{bucket}/{clean_key}"
        self._store[storage_path] = data
        self._metadata[storage_path] = {
            "bucket": bucket,
            "key": clean_key,
            "sizeBytes": len(data),
            "mediaType": media_type,
            "sha256": self.compute_sha256(data),
        }

    def fetch_object(self, bucket: str, key: str, expected_checksum: Optional[str] = None) -> bytes:
        if self._simulate_failure:
            raise StorageUnavailableException("Simulated transient storage failure.")

        clean_key = self.sanitize_key(key)
        storage_path = f"{bucket}/{clean_key}"
        if storage_path not in self._store:
            raise EvidenceNotFoundException(f"Object '{clean_key}' not found in bucket '{bucket}'.")

        data = self._store[storage_path]
        if expected_checksum and not self.verify_checksum(data, expected_checksum):
            raise IntegrityCheckException(
                f"Evidence '{clean_key}' failed integrity check. Expected {expected_checksum}."
            )
        return data

    def get_object_metadata(self, bucket: str, key: str) -> Dict[str, Any]:
        if self._simulate_failure:
            raise StorageUnavailableException("Simulated transient storage failure.")

        clean_key = self.sanitize_key(key)
        storage_path = f"{bucket}/{clean_key}"
        if storage_path not in self._metadata:
            raise EvidenceNotFoundException(f"Object '{clean_key}' not found in bucket '{bucket}'.")
        return dict(self._metadata[storage_path])

    def clear(self) -> None:
        """Clears all stored objects."""
        self._store.clear()
        self._metadata.clear()
        self._simulate_failure = False


class S3CompatibleStorageProvider(BaseStorageProvider):
    """Production S3 / MinIO-compatible storage provider.

    Adheres strictly to secure configuration via environment variables.
    Never exposes credentials or internal endpoints to callers or logs.
    """

    def __init__(
        self,
        endpoint_url: Optional[str] = None,
        access_key: Optional[str] = None,
        secret_key: Optional[str] = None,
        region: Optional[str] = None,
        secure: bool = False,
    ):
        self.endpoint_url = endpoint_url or settings.s3_endpoint
        self.access_key = access_key or settings.s3_access_key
        self.secret_key = secret_key or settings.s3_secret_key
        self.region = region or settings.s3_region
        self.secure = secure if secure is not None else settings.s3_secure
        self._boto_client = None

    def _get_client(self):
        """Initializes or returns cached boto3 S3 client if boto3 is installed."""
        if self._boto_client is not None:
            return self._boto_client

        try:
            import boto3
            from botocore.config import Config

            cfg = Config(
                connect_timeout=settings.evidence_timeout_seconds,
                read_timeout=settings.evidence_timeout_seconds,
                retries={"max_attempts": 2},
            )
            self._boto_client = boto3.client(
                "s3",
                endpoint_url=self.endpoint_url,
                aws_access_key_id=self.access_key,
                aws_secret_access_key=self.secret_key,
                region_name=self.region,
                use_ssl=self.secure,
                config=cfg,
            )
            return self._boto_client
        except ImportError:
            return None

    def fetch_object(self, bucket: str, key: str, expected_checksum: Optional[str] = None) -> bytes:
        clean_key = self.sanitize_key(key)
        client = self._get_client()

        if client is not None:
            try:
                response = client.get_object(Bucket=bucket, Key=clean_key)
                data = response["Body"].read()
                if len(data) > settings.max_image_size_bytes:
                    raise EvidenceOversizedException(
                        f"Evidence '{clean_key}' exceeds max size of {settings.max_image_size_bytes} bytes."
                    )
                if expected_checksum and not self.verify_checksum(data, expected_checksum):
                    raise IntegrityCheckException(f"Evidence '{clean_key}' failed SHA-256 verification.")
                return data
            except Exception as e:
                if isinstance(e, (EvidenceOversizedException, IntegrityCheckException)):
                    raise
                err_str = str(e).lower()
                if "nosuchkey" in err_str or "404" in err_str:
                    raise EvidenceNotFoundException(f"Evidence '{clean_key}' not found in storage.")
                raise StorageUnavailableException(f"Failed to retrieve evidence from storage: {type(e).__name__}")

        # Fallback if boto3 is not installed: simulated storage retrieval or controlled error
        raise StorageUnavailableException("S3 client is not installed or configured.")

    def get_object_metadata(self, bucket: str, key: str) -> Dict[str, Any]:
        clean_key = self.sanitize_key(key)
        client = self._get_client()
        if client is not None:
            try:
                resp = client.head_object(Bucket=bucket, Key=clean_key)
                return {
                    "bucket": bucket,
                    "key": clean_key,
                    "sizeBytes": resp.get("ContentLength", 0),
                    "mediaType": resp.get("ContentType", "image/jpeg"),
                    "etag": resp.get("ETag", "").strip('"'),
                }
            except Exception as e:
                err_str = str(e).lower()
                if "nosuchkey" in err_str or "404" in err_str:
                    raise EvidenceNotFoundException(f"Evidence '{clean_key}' not found in storage.")
                raise StorageUnavailableException(f"Failed to query metadata: {type(e).__name__}")

        raise StorageUnavailableException("S3 client is not installed or configured.")
