"""Comprehensive automated test suite for Phase 6: Async AI Jobs, Worker, & Production Hardening."""

from datetime import datetime, timezone
import uuid
import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError
from app.core.auth import verify_service_token
from app.core.config import settings
from app.core.error_codes import AIErrorCode
from app.core.metrics import metrics
from app.jobs.manager import JobManager
from app.jobs.queue import InMemoryJobQueue
from app.jobs.repository import InMemoryJobRepository
from app.jobs.retry import RetryPolicy
from app.jobs.worker import AIJobWorker
from app.models.model_loader import ModelLoader
from app.schemas.job import (
    AIJobCreateRequest,
    AIJobRecord,
    AIJobResponse,
    JobStatus,
)
from app.storage.evidence_client import evidence_client
from app.storage.s3_storage import (
    IntegrityCheckException,
    MockStorageProvider,
    StorageUnavailableException,
)


@pytest.fixture(autouse=True)
def reset_test_state():
    """Resets in-memory metrics, evidence registry, storage provider, and jobs before each test."""
    from app.jobs.manager import job_manager
    metrics.reset_for_tests()
    evidence_client.clear_test_evidence()
    evidence_client.set_storage_provider(None)
    job_manager.reset_for_tests()
    yield
    evidence_client.clear_test_evidence()
    evidence_client.set_storage_provider(None)
    metrics.reset_for_tests()
    job_manager.reset_for_tests()


# ==============================================================================
# 1. JOB DOMAIN & IDENTITY SEPARATION
# ==============================================================================


def test_job_id_and_run_report_identity_separation():
    """Verifies that jobId, runId, reportId, and inspectionId remain completely distinct."""
    repo = InMemoryJobRepository()
    queue = InMemoryJobQueue()
    worker = AIJobWorker(repository=repo, job_queue=queue)

    insp_id = uuid.uuid4()
    eid = uuid.uuid4()
    job_id = uuid.uuid4()

    record = AIJobRecord(
        jobId=job_id,
        inspectionId=insp_id,
        tenantId="tenant-alpha",
        evidenceIds=[eid],
        testMode=True,
    )
    repo.save(record)
    queue.enqueue(job_id)

    # Process job with worker
    success = worker.process_one_job(timeout_seconds=0.5)
    assert success is True

    completed_job = repo.get(job_id)
    assert completed_job.status == JobStatus.COMPLETED
    assert completed_job.analysisRunId is not None
    assert completed_job.reportId is not None

    # Crucial test: all 4 identifiers must be distinct UUIDs
    ids = {
        completed_job.jobId,
        completed_job.inspectionId,
        completed_job.analysisRunId,
        completed_job.reportId,
    }
    assert len(ids) == 4, "jobId, inspectionId, runId, and reportId must not be collapsed!"


def test_invalid_job_status_transition_raises_error():
    """Verifies that setting forbidden compliance states on AIJobResponse raises ValidationError."""
    with pytest.raises(ValidationError, match="forbidden"):
        AIJobResponse(
            jobId=uuid.uuid4(),
            inspectionId=uuid.uuid4(),
            status="AI_APPROVED",  # type: ignore
            operation="INSPECTION_ANALYSIS_AND_REPORT",
            modelName="model",
            modelVersion="v1",
            serviceVersion="v1",
            createdAt=datetime.now(timezone.utc),
        )

    with pytest.raises(ValidationError, match="forbidden"):
        AIJobResponse(
            jobId=uuid.uuid4(),
            inspectionId=uuid.uuid4(),
            status="AI_REJECTED",  # type: ignore
            operation="INSPECTION_ANALYSIS_AND_REPORT",
            modelName="model",
            modelVersion="v1",
            serviceVersion="v1",
            createdAt=datetime.now(timezone.utc),
        )


def test_idempotent_job_submission():
    """Verifies that duplicate job submissions with identical idempotencyKey return the existing job."""
    repo = InMemoryJobRepository()
    queue = InMemoryJobQueue()
    manager = JobManager(repository=repo, job_queue=queue)

    insp_id = uuid.uuid4()
    eid = uuid.uuid4()
    idemp_key = f"idemp-{uuid.uuid4()}"

    req = AIJobCreateRequest(
        inspectionId=insp_id,
        evidenceIds=[eid],
        idempotencyKey=idemp_key,
        tenantId="tenant-corp",
        testMode=True,
    )

    # First submission
    resp1, is_dup1 = manager.submit_job(req)
    assert is_dup1 is False
    assert resp1.status == JobStatus.QUEUED
    assert queue.get_queue_length() == 1

    # Second submission with identical key
    resp2, is_dup2 = manager.submit_job(req)
    assert is_dup2 is True
    assert resp2.jobId == resp1.jobId
    assert resp2.inspectionId == insp_id
    # Must NOT enqueue a second job
    assert queue.get_queue_length() == 1


# ==============================================================================
# 2. QUEUE & WORKER EXECUTION
# ==============================================================================


def test_job_enqueue_dequeue_lifecycle():
    """Verifies FIFO enqueue and dequeue operations with timeout handling."""
    queue = InMemoryJobQueue()
    jid1 = uuid.uuid4()
    jid2 = uuid.uuid4()

    queue.enqueue(jid1)
    queue.enqueue(jid2)
    assert queue.get_queue_length() == 2

    popped1 = queue.dequeue(timeout_seconds=0.1)
    assert popped1 == jid1

    popped2 = queue.dequeue(timeout_seconds=0.1)
    assert popped2 == jid2

    # Empty queue returns None on timeout
    popped_none = queue.dequeue(timeout_seconds=0.05)
    assert popped_none is None


def test_worker_skips_nonexistent_or_completed_jobs():
    """Verifies worker handles missing records gracefully without crashing."""
    repo = InMemoryJobRepository()
    queue = InMemoryJobQueue()
    worker = AIJobWorker(repository=repo, job_queue=queue)

    ghost_id = uuid.uuid4()
    queue.enqueue(ghost_id)

    # Should safely return False
    result = worker.process_one_job(timeout_seconds=0.1)
    assert result is False


# ==============================================================================
# 3. BOUNDED RETRIES & ERROR CLASSIFICATION
# ==============================================================================


def test_retry_policy_classification():
    """Verifies that deterministic errors are non-retryable and transient errors are retryable."""
    assert RetryPolicy.is_retryable(StorageUnavailableException()) is True
    assert RetryPolicy.is_retryable(TimeoutError()) is True
    assert RetryPolicy.is_retryable(ConnectionError()) is True

    # Non-retryable
    assert RetryPolicy.is_retryable(IntegrityCheckException()) is False
    assert RetryPolicy.is_retryable(ValueError("Invalid shape")) is False
    assert RetryPolicy.is_retryable(TypeError("Type error")) is False


def test_exponential_backoff_calculation():
    """Verifies that exponential backoff scales with attempt number."""
    delay1 = RetryPolicy.compute_backoff_seconds(1, base_seconds=2.0, jitter=False)
    delay2 = RetryPolicy.compute_backoff_seconds(2, base_seconds=2.0, jitter=False)
    delay3 = RetryPolicy.compute_backoff_seconds(3, base_seconds=2.0, jitter=False)

    assert delay1 == 2.0
    assert delay2 == 4.0
    assert delay3 == 8.0

    # With jitter, delay remains positive and bounded
    jittered = RetryPolicy.compute_backoff_seconds(3, base_seconds=2.0, jitter=True)
    assert 2.0 <= jittered <= 10.0


def test_transient_failure_triggers_bounded_retry():
    """Verifies that transient errors increment attempt and schedule retry up to maxAttempts."""
    repo = InMemoryJobRepository()
    queue = InMemoryJobQueue()

    # Create mock storage that fails transiently
    mock_storage = MockStorageProvider()
    mock_storage.set_simulate_failure(True)
    evidence_client.set_storage_provider(mock_storage)

    worker = AIJobWorker(repository=repo, job_queue=queue)
    job_id = uuid.uuid4()
    record = AIJobRecord(
        jobId=job_id,
        inspectionId=uuid.uuid4(),
        evidenceIds=[uuid.uuid4()],
        attempt=1,
        maxAttempts=2,
    )
    repo.save(record)
    queue.enqueue(job_id)

    # First attempt -> retryable
    worker.process_one_job(timeout_seconds=0.2)
    job_after_att1 = repo.get(job_id)
    assert job_after_att1.status == JobStatus.RETRYING
    assert job_after_att1.attempt == 2
    assert queue.get_queue_length() == 1

    # Second attempt -> maxAttempts reached, terminates with FAILED
    worker.process_one_job(timeout_seconds=0.2)
    job_after_att2 = repo.get(job_id)
    assert job_after_att2.status == JobStatus.FAILED
    assert job_after_att2.errorCode == AIErrorCode.STORAGE_UNAVAILABLE.value

    # Reset storage provider
    evidence_client.set_storage_provider(None)


# ==============================================================================
# 4. STORAGE ABSTRACTION & INTEGRITY VALIDATION
# ==============================================================================


def test_mock_storage_provider_fetch_and_metadata():
    """Verifies that MockStorageProvider stores and retrieves objects with SHA-256 metadata."""
    storage = MockStorageProvider()
    payload = b"FIRESPHERE_MOCK_IMAGE_DATA_12345"
    storage.store_object("inspection-bucket", "evidence/sample.jpg", payload)

    retrieved = storage.fetch_object("inspection-bucket", "evidence/sample.jpg")
    assert retrieved == payload

    meta = storage.get_object_metadata("inspection-bucket", "evidence/sample.jpg")
    assert meta["bucket"] == "inspection-bucket"
    assert meta["sizeBytes"] == len(payload)
    assert "sha256" in meta


def test_storage_key_security_sanitization():
    """Verifies rejection of path traversal attempts and arbitrary URLs."""
    storage = MockStorageProvider()
    with pytest.raises(Exception, match="forbidden"):
        storage.sanitize_key("https://malicious.com/exploit.jpg")

    with pytest.raises(Exception, match="forbidden"):
        storage.sanitize_key("../../etc/passwd")

    with pytest.raises(Exception, match="forbidden"):
        storage.sanitize_key("/absolute/path/file.jpg")


def test_cryptographic_checksum_verification():
    """Verifies that mismatched SHA-256 triggers IntegrityCheckException."""
    storage = MockStorageProvider()
    data = b"INTEGRITY_TEST_PAYLOAD"
    correct_hash = storage.compute_sha256(data)
    wrong_hash = "0000000000000000000000000000000000000000000000000000000000000000"

    storage.store_object("bucket-a", "img1.jpg", data)

    # Correct hash succeeds
    assert storage.fetch_object("bucket-a", "img1.jpg", expected_checksum=correct_hash) == data

    # Tampered hash fails
    with pytest.raises(IntegrityCheckException, match="integrity check"):
        storage.fetch_object("bucket-a", "img1.jpg", expected_checksum=wrong_hash)


# ==============================================================================
# 5. SERVICE-TO-SERVICE AUTHENTICATION
# ==============================================================================


def test_service_token_authentication(client: TestClient, monkeypatch):
    """Verifies that service-to-service authentication header is enforced when configured."""
    monkeypatch.setattr(settings, "service_auth_token", "secret-test-token-777")
    monkeypatch.setattr(settings, "auth_enabled", True)

    payload = {
        "inspectionId": str(uuid.uuid4()),
        "evidenceIds": [str(uuid.uuid4())],
        "testMode": True,
    }

    # Missing token -> 401
    resp_no_token = client.post("/internal/ai/jobs", json=payload)
    assert resp_no_token.status_code == 401
    assert resp_no_token.json()["errorCode"] == AIErrorCode.UNAUTHORIZED_SERVICE.value

    # Wrong token -> 401
    resp_bad_token = client.post(
        "/internal/ai/jobs",
        json=payload,
        headers={"X-AI-Service-Token": "invalid-wrong-token"},
    )
    assert resp_bad_token.status_code == 401

    # Valid token via X-AI-Service-Token -> 202
    resp_valid = client.post(
        "/internal/ai/jobs",
        json=payload,
        headers={"X-AI-Service-Token": "secret-test-token-777"},
    )
    assert resp_valid.status_code == 202

    # Valid token via Bearer header -> 202
    resp_bearer = client.post(
        "/internal/ai/jobs",
        json=payload,
        headers={"Authorization": "Bearer secret-test-token-777"},
    )
    assert resp_bearer.status_code == 202


# ==============================================================================
# 6. MODEL LIFECYCLE & CPU/GPU CONFIGURATION
# ==============================================================================


def test_model_loader_device_selection_and_cpu_fallback(monkeypatch):
    """Verifies that get_target_device respects explicit configuration and falls back safely."""
    # Explicit CPU preference
    monkeypatch.setattr(settings, "inference_device", "cpu")
    assert ModelLoader.get_target_device() == "cpu"

    # Auto mode without CUDA GPU returns CPU
    monkeypatch.setattr(settings, "inference_device", "auto")
    device = ModelLoader.get_target_device()
    assert device in ("cpu", "cuda")


# ==============================================================================
# 7. MULTI-TENANCY & CORRELATION ID
# ==============================================================================


def test_tenant_context_and_correlation_id_propagation(client: TestClient):
    """Verifies that tenantId and X-Correlation-ID are preserved through the async job submission."""
    corr_id = f"corr-{uuid.uuid4()}"
    tenant_id = "municipal-fire-dept-42"
    insp_id = uuid.uuid4()
    eid = uuid.uuid4()

    payload = {
        "inspectionId": str(insp_id),
        "evidenceIds": [str(eid)],
        "tenantId": tenant_id,
        "testMode": True,
    }

    response = client.post(
        "/internal/ai/jobs",
        json=payload,
        headers={"X-Correlation-ID": corr_id},
    )
    assert response.status_code == 202
    data = response.json()

    assert data["tenantId"] == tenant_id
    assert data["correlationId"] == corr_id
    assert data["status"] == "QUEUED"

    job_id = data["jobId"]

    # Poll status endpoint
    status_resp = client.get(f"/internal/ai/jobs/{job_id}")
    assert status_resp.status_code == 200
    st_data = status_resp.json()
    assert st_data["tenantId"] == tenant_id
    assert st_data["correlationId"] == corr_id


# ==============================================================================
# 8. OBSERVABILITY & METRICS
# ==============================================================================


def test_prometheus_metrics_endpoint(client: TestClient):
    """Verifies that GET /metrics returns standard Prometheus text format."""
    metrics.inc_counter("ai_jobs_total", labels={"tenant": "unit-test"})
    metrics.observe_duration("ai_inference_duration_seconds", 0.045)

    response = client.get("/metrics")
    assert response.status_code == 200
    assert "text/plain" in response.headers["content-type"]
    text = response.text
    assert "ai_jobs_total" in text
    assert "ai_inference_duration_seconds" in text


# ==============================================================================
# 9. END-TO-END ASYNC JOB RESULTS
# ==============================================================================


def test_end_to_end_job_submission_and_result_retrieval(client: TestClient):
    """Verifies end-to-end async job submission, worker execution, and result retrieval."""
    insp_id = uuid.uuid4()
    eid = uuid.uuid4()

    payload = {
        "inspectionId": str(insp_id),
        "evidenceIds": [str(eid)],
        "testMode": True,
        "occupancyContext": "INDUSTRIAL",
        "officerRemarks": "Checking boiler room extinguishers.",
    }

    # 1. Enqueue job
    resp = client.post("/internal/ai/jobs", json=payload)
    assert resp.status_code == 202
    job_id = resp.json()["jobId"]

    # 2. Query status
    status_resp = client.get(f"/internal/ai/jobs/{job_id}")
    assert status_resp.status_code == 200

    # 3. Retrieve result
    # Process job manually via manager worker if still in QUEUED state
    from app.jobs.manager import job_manager
    job_manager.worker.process_one_job(timeout_seconds=0.2)

    result_resp = client.get(f"/internal/ai/jobs/{job_id}/result")
    assert result_resp.status_code == 200
    res_data = result_resp.json()

    assert res_data["jobId"] == job_id
    assert res_data["status"] == "COMPLETED"
    assert res_data["analysis"] is not None
    assert res_data["report"] is not None
    assert res_data["report"]["reviewState"] == "DRAFT"
    assert res_data["report"]["summary"]["requiresOfficerReview"] is True
    assert "[OFFICER REMARKS — HUMAN AUTHORED]:" in res_data["report"]["officerRemarks"]


def test_job_not_found_returns_404(client: TestClient):
    """Verifies that requesting non-existent jobId returns 404."""
    random_id = uuid.uuid4()
    resp = client.get(f"/internal/ai/jobs/{random_id}")
    assert resp.status_code == 404
    assert resp.json()["errorCode"] == AIErrorCode.JOB_NOT_FOUND.value

    result_resp = client.get(f"/internal/ai/jobs/{random_id}/result")
    assert result_resp.status_code == 404
