"""FireSphere.ai — Repository Contract Test Suite for Finding and Job Persistence.

Defines reusable, specification-level contract tests for any BaseFindingRepository
and BaseJobRepository implementation (In-Memory, Redis, or future PostgreSQL / Flyway).

Verifies:
1. CRUD operations (Save, Get, Update, Filter).
2. Inspection session scoping and foreign ID isolation.
3. Audit history preservation across finding modifications.
4. Thread-safety under concurrent saves and updates.
5. Idempotency key indexing and deep-copy state isolation.
6. Explicit durability indicator (is_durable property).
"""

from datetime import datetime, timezone
import threading
import uuid
import pytest
from app.jobs.repository import BaseJobRepository, InMemoryJobRepository
from app.schemas.finding import BoundingBox, Finding, FindingSeverity
from app.schemas.job import AIJobRecord, JobStatus
from app.services.finding_repository import BaseFindingRepository, InMemoryFindingRepository


# ==============================================================================
# 1. FINDING REPOSITORY CONTRACT TESTS
# ==============================================================================


def test_finding_repository_contract_crud():
    """Verify repository accurately saves, retrieves, and updates finding records."""
    repo: BaseFindingRepository = InMemoryFindingRepository()

    fid = uuid.uuid4()
    iid = uuid.uuid4()
    finding = Finding(
        findingId=fid,
        inspectionId=iid,
        evidenceId=uuid.uuid4(),
        findingType="FIRE_EXTINGUISHER_MISSING",
        confidence=0.88,
        confidenceLevel="HIGH",
        calibrationStatus="NOT_CALIBRATED",
        severity=FindingSeverity.HIGH,
        severityReason="Safety perimeter requirement.",
        operationalRiskScore=75,
        operationalRiskLevel="HIGH",
        explanation="Fire extinguisher missing from designated bracket.",
        verificationGuidance=["Inspect physical bracket.", "Check log sheet."],
        evidenceQuality="GOOD",
        complianceStatus="REQUIRES_OFFICER_VERIFICATION",
        boundingBox=BoundingBox(xMin=0.1, yMin=0.2, xMax=0.4, yMax=0.6),
        model="yolo-v8-safety-mock",
        modelVersion="v1.0.0",
        version=1,
    )

    # 1. Save
    saved = repo.save_finding(finding)
    assert saved.findingId == fid

    # 2. Get by ID
    retrieved = repo.get_finding(fid)
    assert retrieved is not None
    assert retrieved.findingId == fid
    assert retrieved.confidence == 0.88

    # 3. Nonexistent ID returns None
    assert repo.get_finding(uuid.uuid4()) is None

    # 4. Update
    finding.confidence = 0.95
    finding.version = 2
    updated = repo.update_finding(finding)
    assert updated.confidence == 0.95

    retrieved_updated = repo.get_finding(fid)
    assert retrieved_updated is not None
    assert retrieved_updated.version == 2
    assert retrieved_updated.confidence == 0.95


def test_finding_repository_contract_inspection_filtering():
    """Verify findings are strictly partitioned by inspectionId."""
    repo: BaseFindingRepository = InMemoryFindingRepository()

    insp_a = uuid.uuid4()
    insp_b = uuid.uuid4()

    def make_finding(iid: uuid.UUID) -> Finding:
        return Finding(
            findingId=uuid.uuid4(),
            inspectionId=iid,
            evidenceId=uuid.uuid4(),
            findingType="EXIT_SIGNAGE_MISSING",
            confidence=0.80,
            confidenceLevel="HIGH",
            calibrationStatus="NOT_CALIBRATED",
            severity=FindingSeverity.MEDIUM,
            severityReason="Reason",
            explanation="Explanation",
            model="mock",
            modelVersion="v1.0",
        )

    # 3 findings for insp_a, 2 findings for insp_b
    f_a1 = repo.save_finding(make_finding(insp_a))
    f_a2 = repo.save_finding(make_finding(insp_a))
    f_a3 = repo.save_finding(make_finding(insp_a))
    f_b1 = repo.save_finding(make_finding(insp_b))
    f_b2 = repo.save_finding(make_finding(insp_b))

    list_a = repo.get_findings_by_inspection(insp_a)
    list_b = repo.get_findings_by_inspection(insp_b)
    list_c = repo.get_findings_by_inspection(uuid.uuid4())  # Empty

    assert len(list_a) == 3
    assert len(list_b) == 2
    assert len(list_c) == 0

    assert {f.findingId for f in list_a} == {f_a1.findingId, f_a2.findingId, f_a3.findingId}
    assert {f.findingId for f in list_b} == {f_b1.findingId, f_b2.findingId}


def test_finding_repository_contract_audit_trail_preservation():
    """Verify finding auditTrail is preserved and retrievable after updates."""
    repo: BaseFindingRepository = InMemoryFindingRepository()

    fid = uuid.uuid4()
    finding = Finding(
        findingId=fid,
        inspectionId=uuid.uuid4(),
        evidenceId=uuid.uuid4(),
        findingType="FIRE_EXTINGUISHER_MISSING",
        confidence=0.85,
        confidenceLevel="HIGH",
        calibrationStatus="NOT_CALIBRATED",
        severity=FindingSeverity.HIGH,
        severityReason="Reason",
        explanation="Explanation",
        model="mock",
        modelVersion="v1.0",
        auditTrail=[],
    )
    repo.save_finding(finding)

    # Simulate audit trail record in auditHistory
    audit_entry = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "officerId": "OFFICER-007",
        "officerRole": "INSPECTOR",
        "previousAssessment": "UNREVIEWED",
        "newAssessment": "OFFICER_CONFIRMED",
    }
    finding.auditHistory.append(audit_entry)
    repo.update_finding(finding)

    retrieved = repo.get_finding(fid)
    assert retrieved is not None
    assert len(retrieved.auditHistory) == 1
    assert retrieved.auditHistory[0]["officerId"] == "OFFICER-007"


def test_finding_repository_contract_durability_indicator():
    """Verify repository explicitly declares its durability status."""
    repo = InMemoryFindingRepository()
    # In-memory must be declared non-durable
    assert repo.is_durable is False
    assert hasattr(BaseFindingRepository, "is_durable")


def test_finding_repository_contract_thread_safety():
    """Verify repository handles concurrent writes without data loss or exceptions."""
    repo = InMemoryFindingRepository()
    insp_id = uuid.uuid4()
    count_per_thread = 20
    threads = []
    created_ids = []
    lock = threading.Lock()

    def worker():
        for _ in range(count_per_thread):
            f = Finding(
                findingId=uuid.uuid4(),
                inspectionId=insp_id,
                evidenceId=uuid.uuid4(),
                findingType="FIRE_EXTINGUISHER_MISSING",
                confidence=0.75,
                confidenceLevel="MEDIUM",
                calibrationStatus="NOT_CALIBRATED",
                severity=FindingSeverity.MEDIUM,
                severityReason="Reason",
                explanation="Explanation",
                model="mock",
                modelVersion="v1.0",
            )
            repo.save_finding(f)
            with lock:
                created_ids.append(f.findingId)

    for _ in range(5):
        t = threading.Thread(target=worker)
        threads.append(t)
        t.start()

    for t in threads:
        t.join()

    assert len(created_ids) == 100
    findings_for_insp = repo.get_findings_by_inspection(insp_id)
    assert len(findings_for_insp) == 100


# ==============================================================================
# 2. JOB REPOSITORY CONTRACT TESTS
# ==============================================================================


def test_job_repository_contract_crud_and_idempotency():
    """Verify job repository saves, updates, and indexes by idempotency key."""
    repo: BaseJobRepository = InMemoryJobRepository()

    jid = uuid.uuid4()
    idemp_key = f"idemp-test-{uuid.uuid4()}"
    record = AIJobRecord(
        jobId=jid,
        inspectionId=uuid.uuid4(),
        tenantId="tenant-alpha",
        status=JobStatus.QUEUED,
        operation="INSPECTION_ANALYSIS",
        evidenceIds=[uuid.uuid4()],
        idempotencyKey=idemp_key,
    )

    # 1. Save and Get
    repo.save(record)
    retrieved = repo.get(jid)
    assert retrieved is not None
    assert retrieved.jobId == jid
    assert retrieved.status == JobStatus.QUEUED

    # 2. Idempotency Key Lookup
    found_by_key = repo.find_by_idempotency_key(idemp_key)
    assert found_by_key is not None
    assert found_by_key.jobId == jid

    # 3. Nonexistent Key returns None
    assert repo.find_by_idempotency_key("nonexistent-key-123") is None

    # 4. Update
    record.status = JobStatus.COMPLETED
    record.completedAt = datetime.now(timezone.utc)
    repo.update(record)

    retrieved_after_update = repo.get(jid)
    assert retrieved_after_update is not None
    assert retrieved_after_update.status == JobStatus.COMPLETED


def test_job_repository_contract_deep_copy_isolation():
    """Verify repository returns deep copies so in-memory mutations do not leak."""
    repo = InMemoryJobRepository()
    jid = uuid.uuid4()
    record = AIJobRecord(
        jobId=jid,
        inspectionId=uuid.uuid4(),
        tenantId="tenant-alpha",
        status=JobStatus.QUEUED,
        operation="INSPECTION_ANALYSIS",
        evidenceIds=[uuid.uuid4()],
    )
    repo.save(record)

    # Retrieve and mutate local copy without calling update()
    retrieved = repo.get(jid)
    assert retrieved is not None
    retrieved.status = JobStatus.RUNNING

    # Re-retrieve: repository state must NOT have changed
    fresh = repo.get(jid)
    assert fresh is not None
    assert fresh.status == JobStatus.QUEUED
