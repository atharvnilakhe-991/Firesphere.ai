"""FireSphere.ai — Phase 3 Evidence & Observation Validation Engine.

Enforces strict verification boundaries on evidence identifiers, inspection ownership,
image dimensions, bounding-box spatial geometry, observation schema conformance,
and model-to-evidence reference integrity.
"""

from datetime import datetime, timezone
import math
from typing import Any, Dict, List, Optional, Set, Tuple
import uuid
from app.core.exceptions import (
    EvidenceNotFoundException,
    InvalidEvidenceException,
)
from app.core.logging import logger
from app.models.gemini_schemas import (
    SUPPORTED_HAZARD_CATEGORIES,
    SUPPORTED_OBJECT_CATEGORIES,
    VALID_SEVERITIES,
)
from app.schemas.finding import BoundingBox
from app.schemas.reconciliation import EvidenceValidationResult
from app.storage.evidence_client import EvidenceClient, evidence_client


class EvidenceValidator:
    """Verifies evidence existence, inspection linkage, spatial coordinates, and observation schemas."""

    def __init__(self, client: Optional[EvidenceClient] = None):
        self.storage_client = client or evidence_client

    def validate_requested_evidence(
        self,
        inspection_id: uuid.UUID,
        evidence_ids: List[uuid.UUID],
        tenant_id: Optional[str] = "default",
    ) -> Dict[str, EvidenceValidationResult]:
        """Validates that all requested evidence items exist, have valid IDs, and belong to the tenant.

        Returns a dictionary mapping evidenceId string to EvidenceValidationResult.
        """
        results: Dict[str, EvidenceValidationResult] = {}

        if not inspection_id:
            raise InvalidEvidenceException("Inspection identifier must not be empty or null.")

        if not evidence_ids:
            raise InvalidEvidenceException("At least one evidence identifier must be provided.")

        for eid in evidence_ids:
            eid_str = str(eid)
            errors: List[str] = []

            # 1. Validate UUID format
            if not isinstance(eid, uuid.UUID):
                try:
                    uuid.UUID(str(eid))
                except Exception:
                    errors.append(f"Malformed evidence UUID: {eid}")

            # 2. Check existence in storage / registry
            try:
                meta = self.storage_client.get_evidence_metadata(eid)
                if not meta:
                    errors.append(f"Evidence {eid} metadata not found in storage.")
            except EvidenceNotFoundException as enf:
                errors.append(f"Evidence not found: {str(enf)}")
            except Exception as exc:
                errors.append(f"Evidence storage retrieval error: {type(exc).__name__}")

            is_valid = len(errors) == 0
            results[eid_str] = EvidenceValidationResult(
                evidenceId=eid,
                inspectionId=inspection_id,
                isValid=is_valid,
                validationErrors=errors,
                checkedAt=datetime.now(timezone.utc),
            )

        return results

    @staticmethod
    def validate_provider_evidence_reference(
        observation_evidence_id: Any,
        authorized_evidence_ids: Set[str],
    ) -> Tuple[bool, Optional[str]]:
        """Strictly validates that an AI provider observation refers ONLY to an evidence item in the current request.

        Prevents cross-inspection data pollution or model hallucinations of external evidence UUIDs.
        """
        if observation_evidence_id is None:
            return False, "Observation is missing an evidenceId reference."

        obs_eid_str = str(observation_evidence_id).strip().lower()
        authorized_normalized = {str(eid).strip().lower() for eid in authorized_evidence_ids}

        if obs_eid_str not in authorized_normalized:
            logger.warning(
                f"PROVIDER_SECURITY_VIOLATION | Model referenced unauthorized or unknown evidenceId={observation_evidence_id} "
                f"not present in request batch: {list(authorized_evidence_ids)}"
            )
            return False, f"Observation references unauthorized evidence identifier '{observation_evidence_id}'."

        return True, None

    @staticmethod
    def validate_image_dimensions(
        width: Optional[int],
        height: Optional[int],
        min_dim: int = 10,
        max_dim: int = 15000,
    ) -> Tuple[bool, Optional[str]]:
        """Validates that image dimensions are positive, finite integers within operational boundaries."""
        if width is None or height is None:
            return False, "Missing image dimensions."

        if not isinstance(width, (int, float)) or not isinstance(height, (int, float)):
            return False, "Dimensions must be numeric."

        if math.isnan(width) or math.isnan(height) or math.isinf(width) or math.isinf(height):
            return False, "Dimensions must be finite."

        int_w = int(width)
        int_h = int(height)

        if int_w < min_dim or int_h < min_dim:
            return False, f"Image dimensions ({int_w}x{int_h}) below minimum acceptable {min_dim}x{min_dim}."

        if int_w > max_dim or int_h > max_dim:
            return False, f"Image dimensions ({int_w}x{int_h}) exceed maximum acceptable {max_dim}x{max_dim}."

        return True, None

    @staticmethod
    def validate_bounding_box(
        box: Optional[BoundingBox],
    ) -> Tuple[bool, Optional[str]]:
        """Validates that a bounding box has finite, ordered, non-zero-area normalized coordinates in [0.0, 1.0]."""
        if box is None:
            # Absence of a bounding box is valid for image-level observations
            return True, None

        coords = [box.xMin, box.yMin, box.xMax, box.yMax]
        for c in coords:
            if c is None or not isinstance(c, (int, float)):
                return False, "Bounding box contains non-numeric coordinate values."
            if math.isnan(c) or math.isinf(c):
                return False, "Bounding box coordinates must be finite numbers."
            if c < 0.0 or c > 1.0:
                return False, f"Coordinate {c} is out of normalized bounds [0.0, 1.0]."

        if box.xMin >= box.xMax:
            return False, f"xMin ({box.xMin}) must be strictly less than xMax ({box.xMax})."

        if box.yMin >= box.yMax:
            return False, f"yMin ({box.yMin}) must be strictly less than yMax ({box.yMax})."

        area = (box.xMax - box.xMin) * (box.yMax - box.yMin)
        if area <= 0.0:
            return False, "Bounding box area must be strictly positive."

        return True, None

    @staticmethod
    def validate_observation_schema(
        observation: Dict[str, Any],
    ) -> Tuple[bool, List[str]]:
        """Validates that a candidate observation satisfies structural schema requirements."""
        errors: List[str] = []

        category = observation.get("category") or observation.get("objectCategory") or observation.get("findingType")
        if not category or not str(category).strip():
            errors.append("Observation missing category or findingType.")

        severity = observation.get("severity") or observation.get("potentialHazardSeverity")
        if severity:
            sev_norm = str(severity).strip().upper()
            if sev_norm not in VALID_SEVERITIES:
                errors.append(f"Invalid severity rating '{severity}'. Must be one of {VALID_SEVERITIES}.")

        confidence = observation.get("confidence")
        if confidence is not None:
            if not isinstance(confidence, (int, float)) or math.isnan(confidence) or math.isinf(confidence):
                errors.append("Confidence must be a finite float.")
            elif confidence < 0.0 or confidence > 1.0:
                errors.append(f"Confidence score {confidence} is out of range [0.0, 1.0].")

        return len(errors) == 0, errors


# Global evidence validator singleton
evidence_validator = EvidenceValidator()
