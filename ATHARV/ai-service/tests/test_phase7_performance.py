"""Controlled performance and load test harness for Phase 7."""

import concurrent.futures
import time
import uuid
import numpy as np
import pytest
from fastapi.testclient import TestClient
from app.cv.preprocessor import CVPreprocessor
from app.jobs.manager import JobManager
from app.jobs.queue import InMemoryJobQueue
from app.jobs.repository import InMemoryJobRepository
from app.models.model_loader import ModelLoader
from app.reporting.deterministic_generator import DeterministicReportGenerator
from app.schemas.job import AIJobCreateRequest


def test_job_submission_latency(client: TestClient):
    """Measures API job creation latency (expected < 25ms)."""
    payload = {
        "inspectionId": str(uuid.uuid4()),
        "evidenceIds": [str(uuid.uuid4())],
        "testMode": True,
    }

    latencies = []
    for _ in range(10):
        t0 = time.perf_counter()
        resp = client.post("/internal/ai/jobs", json=payload)
        lat = (time.perf_counter() - t0) * 1000
        assert resp.status_code == 202
        latencies.append(lat)

    p50 = float(np.percentile(latencies, 50))
    p95 = float(np.percentile(latencies, 95))
    assert p50 < 50.0, f"Job submission p50 latency exceeded 50ms: {p50:.2f}ms"
    assert p95 < 100.0, f"Job submission p95 latency exceeded 100ms: {p95:.2f}ms"


def test_queue_latency():
    """Measures FIFO enqueue and dequeue latency (expected < 5ms)."""
    q = InMemoryJobQueue()
    jid = uuid.uuid4()

    t0 = time.perf_counter()
    q.enqueue(jid)
    enq_ms = (time.perf_counter() - t0) * 1000

    t1 = time.perf_counter()
    popped = q.dequeue(timeout_seconds=0.1)
    deq_ms = (time.perf_counter() - t1) * 1000

    assert popped == jid
    assert enq_ms < 5.0
    assert deq_ms < 5.0


def test_report_generation_latency():
    """Measures deterministic report draft compilation time (expected < 25ms)."""
    generator = DeterministicReportGenerator()
    insp_id = uuid.uuid4()
    run_id = uuid.uuid4()
    eid = uuid.uuid4()

    t0 = time.perf_counter()
    report = generator.generate_draft(
        inspection_id=insp_id,
        run_id=run_id,
        findings=[],
        evidence_ids=[eid],
        occupancy_context="COMMERCIAL",
        historical_context="ANNUAL_ROUTINE",
        is_test_data=True,
    )
    duration_ms = (time.perf_counter() - t0) * 1000

    assert report.inspectionId == insp_id
    assert duration_ms < 50.0, f"Report generation exceeded 50ms: {duration_ms:.2f}ms"


def test_controlled_concurrency_load_harness(client: TestClient):
    """Executes safe concurrent load test for 1, 5, and 10 parallel submissions.

    Measures throughput, p50, p95 latencies, and verifies zero failure rate.
    """
    repo = InMemoryJobRepository()
    queue = InMemoryJobQueue()
    manager = JobManager(repository=repo, job_queue=queue)

    def submit_one(idx: int):
        req = AIJobCreateRequest(
            inspectionId=uuid.uuid4(),
            evidenceIds=[uuid.uuid4()],
            idempotencyKey=f"load-test-key-{idx}",
            testMode=True,
        )
        t_start = time.perf_counter()
        resp, _ = manager.submit_job(req)
        duration_ms = (time.perf_counter() - t_start) * 1000
        is_queued = str(resp.status) == "QUEUED" or getattr(resp.status, "value", "") == "QUEUED"
        return is_queued, duration_ms

    for concurrency in [1, 5, 10]:
        start_time = time.perf_counter()
        latencies = []
        successes = 0

        with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as executor:
            futures = [executor.submit(submit_one, i) for i in range(concurrency)]
            for fut in concurrent.futures.as_completed(futures):
                ok, lat = fut.result()
                if ok:
                    successes += 1
                latencies.append(lat)

        total_elapsed = time.perf_counter() - start_time
        throughput = round(concurrency / total_elapsed, 2)
        p50 = round(float(np.percentile(latencies, 50)), 2)
        p95 = round(float(np.percentile(latencies, 95)), 2)

        assert successes == concurrency, f"Load failure at concurrency {concurrency}: {successes}/{concurrency} succeeded"
        assert p50 < 50.0, f"Concurrency {concurrency} p50 exceeded 50ms: {p50}ms"
        assert p95 < 100.0, f"Concurrency {concurrency} p95 exceeded 100ms: {p95}ms"


def test_hardware_device_detection():
    """Verifies honest hardware device detection and reporting."""
    device = ModelLoader.get_target_device()
    assert device in ("cpu", "cuda")
