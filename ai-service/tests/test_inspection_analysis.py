"""Tests for AI inspection analysis endpoint and AI run creation."""

import uuid
from fastapi.testclient import TestClient
from app.pipelines.inspection_pipeline import InspectionPipeline
from app.schemas.run import RunStatus


def test_valid_inspection_analysis_request(client: TestClient, valid_inspection_payload: dict):
    """Verifies that POST /internal/ai/inspection-analysis accepts valid payload and returns 200."""
    response = client.post("/internal/ai/inspection-analysis", json=valid_inspection_payload)
    assert response.status_code == 200
    data = response.json()

    # Verify response schema contract
    assert "runId" in data
    assert uuid.UUID(data["runId"])  # Must be valid UUID
    assert data["status"] == "COMPLETED"
    assert isinstance(data["findings"], list)
    assert len(data["findings"]) == 0  # Phase 1 baseline returns empty findings for real data
    assert "model" in data
    assert "modelVersion" in data
    assert "executionDurationMs" in data
    assert data["executionDurationMs"] >= 0


def test_inspection_analysis_with_test_mode(client: TestClient, sample_inspection_id: str, sample_evidence_ids: list):
    """Verifies that testMode generates synthetic test findings with isTestData=True."""
    payload = {
        "inspectionId": sample_inspection_id,
        "evidenceIds": sample_evidence_ids,
        "testMode": True,
    }
    response = client.post("/internal/ai/inspection-analysis", json=payload)
    assert response.status_code == 200
    data = response.json()

    assert data["status"] == "COMPLETED"
    assert len(data["findings"]) > 0

    finding = data["findings"][0]
    assert "findingId" in finding
    assert "evidenceId" in finding
    assert "findingType" in finding
    assert "confidence" in finding
    assert 0.0 <= finding["confidence"] <= 1.0
    assert "severity" in finding
    assert finding["severity"] in ["INFORMATIONAL", "LOW", "MEDIUM", "HIGH", "CRITICAL"]
    assert "explanation" in finding
    assert finding["isTestData"] is True


def test_ai_run_creation_and_traceability(sample_inspection_id: str, sample_evidence_ids: list):
    """Verifies internal AIRun model creation, fields, and traceability."""
    pipeline = InspectionPipeline()
    inspection_uuid = uuid.UUID(sample_inspection_id)
    evidence_uuids = [uuid.UUID(eid) for eid in sample_evidence_ids]

    ai_run = pipeline.execute(
        inspection_id=inspection_uuid,
        evidence_ids=evidence_uuids,
        test_mode=False,
    )

    assert ai_run.runId is not None
    assert ai_run.inspectionId == inspection_uuid
    assert ai_run.inputEvidence == evidence_uuids
    assert ai_run.status == RunStatus.COMPLETED
    assert ai_run.model is not None
    assert ai_run.modelVersion is not None
    assert ai_run.startedAt is not None
    assert ai_run.completedAt is not None
    assert ai_run.executionDurationMs is not None
    assert ai_run.errorMessage is None
