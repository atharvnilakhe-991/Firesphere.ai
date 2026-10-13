"""Model registry abstraction managing model versions, metadata, and lifecycle promotion states."""

from datetime import datetime, timezone
from enum import Enum
import threading
from typing import Any, Dict, List, Optional, Tuple
from pydantic import BaseModel, Field
from app.core.exceptions import FireSphereAIException
from app.core.logging import logger


class ModelLifecycleState(str, Enum):
    """Auditable deployment states for computer vision models."""

    EXPERIMENTAL = "EXPERIMENTAL"
    VALIDATED = "VALIDATED"
    STAGING = "STAGING"
    PRODUCTION = "PRODUCTION"
    RETIRED = "RETIRED"


class ModelPromotionException(FireSphereAIException):
    """Raised when an illegal lifecycle transition is attempted on a model artifact."""

    def __init__(self, message: str):
        super().__init__(
            message=message,
            status_code=400,
            error_code="INVALID_MODEL_PROMOTION",
        )


class ModelArtifactMetadata(BaseModel):
    """Cryptographically verifiable specification of a model artifact."""

    modelId: str
    modelVersion: str
    architecture: str
    datasetVersion: str
    trainingRunId: str
    createdAt: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    classList: List[str]
    inputSize: Tuple[int, int] = (640, 640)
    frameworkVersion: str = "PyTorch"
    torchVersion: str
    cudaAvailable: bool
    metrics: Dict[str, Any] = Field(default_factory=dict)
    calibrationStatus: str = "NOT_CALIBRATED"
    thresholdConfig: Dict[str, float] = Field(default_factory=dict)
    sha256Checksum: str
    status: ModelLifecycleState = ModelLifecycleState.EXPERIMENTAL
    auditTrail: List[Dict[str, Any]] = Field(default_factory=list)


class ModelRegistry:
    """Thread-safe registry for model discovery, provenance tracking, and promotion governance."""

    def __init__(self):
        self._lock = threading.Lock()
        self._registry: Dict[str, ModelArtifactMetadata] = {}

    @staticmethod
    def _make_key(model_id: str, version: str) -> str:
        return f"{model_id}:{version}"

    def register_model(self, metadata: ModelArtifactMetadata) -> ModelArtifactMetadata:
        """Registers a model artifact. Models are immutable once registered."""
        key = self._make_key(metadata.modelId, metadata.modelVersion)
        with self._lock:
            if key in self._registry:
                raise ValueError(
                    f"Model '{metadata.modelId}' version '{metadata.modelVersion}' is already registered and immutable."
                )

            entry = metadata.model_copy(deep=True)
            entry.auditTrail.append({
                "action": "REGISTERED",
                "status": entry.status.value,
                "timestamp": datetime.now(timezone.utc).isoformat(),
                "actor": "system",
                "reason": "Initial model artifact registration.",
            })
            self._registry[key] = entry
            logger.info(
                f"Model registered in registry | ID={metadata.modelId} | Version={metadata.modelVersion} | Status={entry.status.value}"
            )
            return entry.model_copy(deep=True)

    def get_model(self, model_id: str, version: str) -> Optional[ModelArtifactMetadata]:
        """Retrieves registered model artifact metadata."""
        key = self._make_key(model_id, version)
        with self._lock:
            entry = self._registry.get(key)
            return entry.model_copy(deep=True) if entry else None

    def list_models(self, state: Optional[ModelLifecycleState] = None) -> List[ModelArtifactMetadata]:
        """Lists registered models, optionally filtered by lifecycle status."""
        with self._lock:
            models = list(self._registry.values())
            if state is not None:
                models = [m for m in models if m.status == state]
            return [m.model_copy(deep=True) for m in models]

    def get_active_production_model(self) -> Optional[ModelArtifactMetadata]:
        """Returns the active model marked for PRODUCTION."""
        with self._lock:
            prod_models = [m for m in self._registry.values() if m.status == ModelLifecycleState.PRODUCTION]
            if not prod_models:
                return None
            # Return most recently created production model
            prod_models.sort(key=lambda m: m.createdAt, reverse=True)
            return prod_models[0].model_copy(deep=True)

    def promote_model(
        self,
        model_id: str,
        version: str,
        target_state: ModelLifecycleState,
        approved_by: str,
        audit_reason: str,
    ) -> ModelArtifactMetadata:
        """Promotes a model through strict governance gates.

        Allowed transitions:
        - EXPERIMENTAL -> VALIDATED
        - VALIDATED -> STAGING
        - STAGING -> PRODUCTION
        - Any -> RETIRED
        """
        key = self._make_key(model_id, version)
        with self._lock:
            if key not in self._registry:
                raise FileNotFoundError(f"Model '{model_id}' version '{version}' not found in registry.")

            current = self._registry[key]
            current_state = current.status

            if current_state == target_state:
                return current.model_copy(deep=True)

            # Retirement is always permitted
            if target_state == ModelLifecycleState.RETIRED:
                pass
            elif current_state == ModelLifecycleState.EXPERIMENTAL and target_state != ModelLifecycleState.VALIDATED:
                raise ModelPromotionException(
                    f"Illegal transition: EXPERIMENTAL models must first be VALIDATED before {target_state.value}."
                )
            elif current_state == ModelLifecycleState.VALIDATED and target_state != ModelLifecycleState.STAGING:
                raise ModelPromotionException(
                    f"Illegal transition: VALIDATED models must be deployed to STAGING before {target_state.value}."
                )
            elif current_state == ModelLifecycleState.STAGING and target_state != ModelLifecycleState.PRODUCTION:
                raise ModelPromotionException(
                    f"Illegal transition: STAGING models can only transition to PRODUCTION or RETIRED."
                )
            elif current_state == ModelLifecycleState.RETIRED:
                raise ModelPromotionException(
                    "Cannot promote a RETIRED model artifact. Register a new version instead."
                )

            # Record promotion
            current.status = target_state
            current.auditTrail.append({
                "action": "PROMOTED",
                "fromState": current_state.value,
                "toState": target_state.value,
                "approvedBy": approved_by,
                "reason": audit_reason,
                "timestamp": datetime.now(timezone.utc).isoformat(),
            })

            logger.info(
                f"Model lifecycle promoted | ID={model_id}:{version} | "
                f"{current_state.value} -> {target_state.value} | ApprovedBy={approved_by}"
            )
            return current.model_copy(deep=True)

    def clear_for_tests(self) -> None:
        """Clears in-memory registry for test isolation."""
        with self._lock:
            self._registry.clear()


# Global singleton model registry instance
model_registry = ModelRegistry()
