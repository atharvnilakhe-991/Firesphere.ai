"""End-to-End Automated Integration Test Suite for Phase 7."""

import uuid
import pytest
from fastapi.testclient import TestClient
from app.jobs.manager import job_manager
from app.schemas.job import AIJobResponse, JobStatus
from app.storage.evidence_client import evidence_client
from app.storage.s3_storage import MockStorageProvider


@pytest.fixture(autouse=True)
def reset_e2e_state():
    """Resets storage provider, queue, and evidence registry before each test."""
    evidence_client.clear_test_evidence()
    evidence_client.set_storage_provider(None)
    job_manager.reset_for_tests()
    yield
    evidence_client.clear_test_evidence()
    evidence_client.set_storage_provider(None)
    job_manager.reset_for_tests()


def test_complete_end_to_end_fire_safety_ai_workflow(client: TestClient):
    """Executes the complete end-to-end fire safety AI lifecycle:

    1. Tenant & Inspection Session creation
    2. Evidence upload to S3/MinIO storage abstraction with SHA-256
    3. Asynchronous AI job submission to FastAPI (HTTP 202 Accepted)
    4. FIFO queue dispatch and worker execution
    5. Computer Vision inference + Phase 4 Intelligence + Phase 5 Report Draft
    6. Structured result retrieval
    7. Validation of human-in-the-loop review boundaries (reviewState: DRAFT, requiresOfficerReview: True)
    """
    tenant_id = "municipal-fire-district-09"
    inspection_id = uuid.uuid4()
    evidence_id = uuid.uuid4()

    # 1. Setup mock storage and store valid JPEG evidence payload
    storage = MockStorageProvider()
    evidence_client.set_storage_provider(storage)

    img_data = evidence_client._generate_synthetic_evidence_image(640, 640)
    evidence_client.register_test_evidence(evidence_id, img_data)
    storage.store_object("firesphere-evidence", f"evidence/{evidence_id}", img_data)
    storage.store_object("firesphere-evidence", f"tenants/{tenant_id}/evidence/{evidence_id}", img_data)

    # 2. Submit AI job via FastAPI API
    submission_payload = {
        "inspectionId": str(inspection_id),
        "evidenceIds": [str(evidence_id)],
        "tenantId": tenant_id,
        "operation": "INSPECTION_ANALYSIS_AND_REPORT",
        "occupancyContext": "HOSPITALITY",
        "historicalContext": "ANNUAL_ROUTINE",
        "officerRemarks": "Inspecting hotel kitchen fire suppression and emergency exits.",
        "checklistData": {"panel_clear": True},
        "idempotencyKey": f"e2e-run-{inspection_id}",
        "testMode": True,  # Clearly marked TEST_DATA
    }

    create_resp = client.post("/internal/ai/jobs", json=submission_payload)
    assert create_resp.status_code == 202
    job_data = create_resp.json()

    job_id = job_data["jobId"]
    assert job_data["status"] == "QUEUED"
    assert job_data["tenantId"] == tenant_id

    # 3. Query initial job status
    poll_resp1 = client.get(f"/internal/ai/jobs/{job_id}")
    assert poll_resp1.status_code == 200
    assert poll_resp1.json()["status"] == "QUEUED"

    # 4. Trigger Worker processing
    success = job_manager.worker.process_one_job(timeout_seconds=0.5)
    assert success is True

    # 5. Query status after worker completion
    poll_resp2 = client.get(f"/internal/ai/jobs/{job_id}")
    assert poll_resp2.status_code == 200
    st_data = poll_resp2.json()
    assert st_data["status"] == "COMPLETED"
    assert st_data["analysisRunId"] is not None
    assert st_data["reportId"] is not None

    # Crucial identity separation check:
    ids = {
        st_data["jobId"],
        st_data["inspectionId"],
        st_data["analysisRunId"],
        st_data["reportId"],
    }
    assert len(ids) == 4, "jobId, inspectionId, runId, and reportId must not be collapsed!"

    # 6. Retrieve complete structured results
    result_resp = client.get(f"/internal/ai/jobs/{job_id}/result")
    assert result_resp.status_code == 200
    result_data = result_resp.json()

    assert result_data["status"] == "COMPLETED"
    assert result_data["analysis"] is not None
    assert result_data["report"] is not None

    # 7. Human review safety verification:
    report = result_data["report"]
    assert report["reviewState"] == "DRAFT"
    assert report["status"] == "DRAFT"
    assert report["summary"]["requiresOfficerReview"] is True

    # Verify officer remarks are preserved
    assert "Inspecting hotel kitchen" in report["officerRemarks"]

    # Verify test mode flag and warning
    assert report["isTestData"] is True
    assert "TEST DATA — NOT FOR OPERATIONAL OR COMPLIANCE USE" in report["testDataWarning"]

    # Verify explainability (WHAT, WHERE, WHY, VERIFY) exists on findings
    findings = result_data["analysis"]["findings"]
    for finding in findings:
        assert "explanation" in finding
        exp = finding["explanation"]
        assert isinstance(exp, str)
        exp_lower = exp.lower()
        assert "what:" in exp_lower and "where:" in exp_lower and "why:" in exp_lower and "verify:" in exp_lower


def test_empty_findings_does_not_certify_building_safe(client: TestClient):
    """Verifies that 0 findings explicitly requires officer verification and does NOT certify compliance."""
    insp_id = uuid.uuid4()
    eid = uuid.uuid4()

    # Submit with testMode=False (production baseline mode generates 0 fake findings)
    payload = {
        "inspectionId": str(insp_id),
        "evidenceIds": [str(eid)],
        "testMode": False,
        "officerRemarks": "Warehouse inspection without visual defects.",
    }

    resp = client.post("/internal/ai/jobs", json=payload)
    job_id = resp.json()["jobId"]

    # Process via worker
    job_manager.worker.process_one_job(timeout_seconds=0.5)

    res_resp = client.get(f"/internal/ai/jobs/{job_id}/result")
    res_data = res_resp.json()
    report = res_data["report"]

    assert len(res_data["analysis"]["findings"]) == 0
    # Must NOT certify compliance
    assert report["summary"]["requiresOfficerReview"] is True
    assert report["reviewState"] == "DRAFT"
    assert "does NOT certify that the building is compliant" in report["summary"]["narrative"]
    assert "human Fire Officer physical inspection is required" in report["summary"]["narrative"]
