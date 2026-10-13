"""Security utilities for internal service-to-service communication."""

import uuid
from typing import List
from app.core.exceptions import InvalidEvidenceException, InvalidInspectionException


def validate_uuid_format(value: str, field_name: str = "id") -> uuid.UUID:
    """Strictly validates that an input string is a valid RFC 4122 UUID."""
    try:
        return uuid.UUID(str(value))
    except (ValueError, AttributeError, TypeError):
        if field_name == "inspectionId":
            raise InvalidInspectionException(f"Invalid UUID format for {field_name}")
        elif field_name == "evidenceId":
            raise InvalidEvidenceException(f"Invalid UUID format for {field_name}")
        raise ValueError(f"Field {field_name} must be a valid UUID")


def validate_evidence_ids(evidence_ids: List[str]) -> List[uuid.UUID]:
    """Validates that evidence list is non-empty and contains valid UUIDs."""
    if not evidence_ids:
        raise InvalidEvidenceException("Evidence list cannot be empty")

    validated_ids = []
    for eid in evidence_ids:
        validated_ids.append(validate_uuid_format(eid, field_name="evidenceId"))
    return validated_ids
