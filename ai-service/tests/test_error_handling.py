"""Tests for input validation, error handling, and safe failure responses."""

from unittest.mock import patch
from fastapi.testclient import TestClient


def test_invalid_inspection_id_returns_422(client: TestClient, sample_evidence_ids: list):
    """Verifies that malformed inspectionId triggers controlled validation error."""
    payload = {
        "inspectionId": "invalid-non-uuid-string",
        "evidenceIds": sample_evidence_ids,
    }
    response = client.post("/internal/ai/inspection-analysis", json=payload)
    assert response.status_code == 422
    data = response.json()
    assert data["status"] == "FAILED"
    assert data["errorCode"] == "VALIDATION_ERROR"
    assert "validationErrors" in data
    # Confirm stack trace / internal filesystem path is NOT leaked
    assert "Traceback" not in response.text
    assert "c:\\" not in response.text.lower()


def test_invalid_evidence_id_returns_422(client: TestClient, sample_inspection_id: str):
    """Verifies that malformed evidence ID triggers controlled validation error."""
    payload = {
        "inspectionId": sample_inspection_id,
        "evidenceIds": ["not-a-valid-uuid"],
    }
    response = client.post("/internal/ai/inspection-analysis", json=payload)
    assert response.status_code == 422
    data = response.json()
    assert data["status"] == "FAILED"
    assert data["errorCode"] == "VALIDATION_ERROR"


def test_empty_evidence_ids_returns_422(client: TestClient, sample_inspection_id: str):
    """Verifies that an empty evidence list is rejected."""
    payload = {
        "inspectionId": sample_inspection_id,
        "evidenceIds": [],
    }
    response = client.post("/internal/ai/inspection-analysis", json=payload)
    assert response.status_code == 422
    data = response.json()
    assert data["status"] == "FAILED"
    assert data["errorCode"] == "VALIDATION_ERROR"


def test_missing_required_fields_returns_422(client: TestClient):
    """Verifies that missing payload fields return 422."""
    response = client.post("/internal/ai/inspection-analysis", json={})
    assert response.status_code == 422
    data = response.json()
    assert data["status"] == "FAILED"
    assert data["errorCode"] == "VALIDATION_ERROR"


def test_controlled_pipeline_failure_response(valid_inspection_payload: dict):
    """Verifies that an internal pipeline failure returns controlled response without leaking internals."""
    from app.main import app

    test_client = TestClient(app, raise_server_exceptions=False)
    with patch(
        "app.pipelines.inspection_pipeline.InspectionPipeline.execute",
        side_effect=RuntimeError("Simulated internal pipeline hardware fault"),
    ):
        response = test_client.post("/internal/ai/inspection-analysis", json=valid_inspection_payload)
        assert response.status_code == 500
        data = response.json()

        assert data["status"] == "FAILED"
        assert "errorCode" in data
        assert "message" in data
        # Ensure stack trace and paths are never exposed to client
        assert "Traceback" not in response.text
        assert "OneDrive" not in response.text
        assert "ai-service" not in response.text
