"""Automated tests for Phase 7 Asynchronous Queue Integration and Idempotency."""

import uuid
import pytest
from app.jobs.manager import JobManager
from app.jobs.queue import InMemoryJobQueue
from app.jobs.repository import InMemoryJobRepository
from app.jobs.worker import AIJobWorker
from app.schemas.job import AIJobCreateRequest, JobStatus


def test_queue_blocking_dequeue_timeout():
    """Verifies that dequeue blocks up to timeout_seconds and returns None on empty queue."""
    queue = InMemoryJobQueue()
    assert queue.get_queue_length() == 0

    popped = queue.dequeue(timeout_seconds=0.05)
    assert popped is None


def test_idempotent_duplicate_submission_does_not_requeue():
    """Verifies that submitting identical idempotencyKey twice returns original job without duplicate enqueues."""
    repo = InMemoryJobRepository()
    queue = InMemoryJobQueue()
    manager = JobManager(repository=repo, job_queue=queue)

    key = f"idemp-token-{uuid.uuid4()}"
    req = AIJobCreateRequest(
        inspectionId=uuid.uuid4(),
        evidenceIds=[uuid.uuid4()],
        idempotencyKey=key,
        tenantId="tenant-corp",
        testMode=True,
    )

    # First submission
    resp1, is_dup1 = manager.submit_job(req)
    assert is_dup1 is False
    assert queue.get_queue_length() == 1

    # Second submission with same key
    resp2, is_dup2 = manager.submit_job(req)
    assert is_dup2 is True
    assert resp2.jobId == resp1.jobId
    # Crucial check: Queue length remains 1 (no duplicate work enqueued)
    assert queue.get_queue_length() == 1


def test_worker_processes_queued_job_to_completion():
    """Verifies worker dequeues, processes through pipeline, and marks status COMPLETED."""
    repo = InMemoryJobRepository()
    queue = InMemoryJobQueue()
    worker = AIJobWorker(repository=repo, job_queue=queue)
    manager = JobManager(repository=repo, job_queue=queue)

    req = AIJobCreateRequest(
        inspectionId=uuid.uuid4(),
        evidenceIds=[uuid.uuid4()],
        testMode=True,
        officerRemarks="Boiler room inspection",
    )
    job_resp, _ = manager.submit_job(req)
    assert queue.get_queue_length() == 1

    # Process job via worker
    processed = worker.process_one_job(timeout_seconds=0.2)
    assert processed is True
    assert queue.get_queue_length() == 0

    # Verify completed state in repository
    completed = repo.get(job_resp.jobId)
    assert completed is not None
    assert completed.status == JobStatus.COMPLETED
    assert completed.analysisRunId is not None
    assert completed.reportId is not None
    assert completed.resultData is not None
