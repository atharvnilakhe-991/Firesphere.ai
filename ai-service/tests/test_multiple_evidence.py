"""Tests for multiple evidence processing, fault isolation, and per-evidence traceability."""

import uuid
import cv2
import numpy as np
from fastapi.testclient import TestClient
from app.storage.evidence_client import evidence_client


def make_valid_test_jpeg(width: int = 300, height: int = 200) -> bytes:
    """Creates a valid JPEG byte payload."""
    img = np.full((height, width, 3), (120, 140, 160), dtype=np.uint8)
    cv2.rectangle(img, (20, 20), (100, 100), (0, 200, 0), -1)
    _, buf = cv2.imencode(".jpg", img)
    return buf.tobytes()


def test_multiple_valid_evidence(client: TestClient):
    """Verifies that an inspection with multiple valid evidence items succeeds completely."""
    eid1 = uuid.uuid4()
    eid2 = uuid.uuid4()
    insp_id = uuid.uuid4()

    evidence_client.register_test_evidence(eid1, make_valid_test_jpeg(), "image/jpeg")
    evidence_client.register_test_evidence(eid2, make_valid_test_jpeg(), "image/jpeg")

    payload = {
        "inspectionId": str(insp_id),
        "evidenceIds": [str(eid1), str(eid2)],
        "testMode": True,
    }

    response = client.post("/internal/ai/inspection-analysis", json=payload)
    assert response.status_code == 200
    data = response.json()

    assert data["status"] == "COMPLETED"
    # When testMode is True, each valid evidence item yields 1 synthetic finding
    assert len(data["findings"]) == 2
    finding_eids = {f["evidenceId"] for f in data["findings"]}
    assert str(eid1) in finding_eids
    assert str(eid2) in finding_eids


def test_mixed_valid_and_invalid_evidence(client: TestClient):
    """Verifies fault isolation: one corrupted/unsupported evidence does not crash the valid evidence."""
    valid_eid = uuid.uuid4()
    corrupt_eid = uuid.uuid4()
    insp_id = uuid.uuid4()

    # Register one valid image and one corrupted image
    evidence_client.register_test_evidence(valid_eid, make_valid_test_jpeg(), "image/jpeg")
    evidence_client.register_test_evidence(corrupt_eid, b"NOT_A_VALID_IMAGE_BYTES_AT_ALL", "image/jpeg")

    payload = {
        "inspectionId": str(insp_id),
        "evidenceIds": [str(valid_eid), str(corrupt_eid)],
        "testMode": True,
    }

    response = client.post("/internal/ai/inspection-analysis", json=payload)
    assert response.status_code == 200
    data = response.json()

    # Overall run completes because valid evidence was processed
    assert data["status"] == "COMPLETED"
    # Findings should be generated ONLY for the valid evidence
    assert len(data["findings"]) == 1
    assert data["findings"][0]["evidenceId"] == str(valid_eid)


def test_all_invalid_evidence_controlled_failure(client: TestClient):
    """Verifies that when all evidence items fail, a controlled error is returned without crashing the service."""
    corrupt_eid1 = uuid.uuid4()
    corrupt_eid2 = uuid.uuid4()
    insp_id = uuid.uuid4()

    evidence_client.register_test_evidence(corrupt_eid1, b"GARBAGE_1", "text/plain")
    evidence_client.register_test_evidence(corrupt_eid2, b"GARBAGE_2", "text/plain")

    payload = {
        "inspectionId": str(insp_id),
        "evidenceIds": [str(corrupt_eid1), str(corrupt_eid2)],
        "testMode": False,
    }

    response = client.post("/internal/ai/inspection-analysis", json=payload)
    # When all evidence items fail, pipeline raises PipelineProcessingException -> HTTP 500
    assert response.status_code == 500
    data = response.json()

    assert data["status"] == "FAILED"
    assert data["errorCode"] in ["AI_PROCESSING_FAILURE", "AI_INTERNAL_ERROR"]
    assert "all evidence items failed" in data["message"].lower()
    # Confirm stack trace is NOT exposed
    assert "Traceback" not in response.text
