"""Automated tests for Phase 7 Object Storage Integration (MinIO/S3 abstraction)."""

import uuid
import pytest
from app.core.exceptions import EvidenceNotFoundException
from app.storage.evidence_client import EvidenceClient
from app.storage.s3_storage import (
    IntegrityCheckException,
    MockStorageProvider,
    S3CompatibleStorageProvider,
    StorageUnavailableException,
)


def test_storage_provider_upload_retrieve_lifecycle():
    """Verifies end-to-end evidence storage and retrieval with SHA-256 metadata."""
    storage = MockStorageProvider()
    bucket = "firesphere-inspection-evidence"
    key = "evidence/extinguisher_001.jpg"
    payload = b"\xff\xd8\xff\xe0" + b"\x00" * 1024  # Minimal valid JPEG header signature

    storage.store_object(bucket, key, payload)

    # 1. Fetch object
    retrieved = storage.fetch_object(bucket, key)
    assert retrieved == payload

    # 2. Get object metadata
    meta = storage.get_object_metadata(bucket, key)
    assert meta["bucket"] == bucket
    assert meta["key"] == key
    assert meta["sizeBytes"] == len(payload)
    assert meta["sha256"] == storage.compute_sha256(payload)


def test_storage_provider_nonexistent_key_raises_404():
    """Verifies that querying a missing key raises EvidenceNotFoundException."""
    storage = MockStorageProvider()
    with pytest.raises(EvidenceNotFoundException, match="not found"):
        storage.fetch_object("bucket", "nonexistent/item.jpg")


def test_storage_provider_simulated_outage_raises_service_unavailable():
    """Verifies that network outage or MinIO downtime triggers StorageUnavailableException."""
    storage = MockStorageProvider()
    storage.store_object("bucket", "test.jpg", b"DATA")
    storage.set_simulate_failure(True)

    with pytest.raises(StorageUnavailableException, match="Simulated transient storage failure"):
        storage.fetch_object("bucket", "test.jpg")


def test_evidence_client_with_storage_provider_integration():
    """Verifies EvidenceClient delegates to storage provider with tenant isolation."""
    storage = MockStorageProvider()
    client = EvidenceClient(bucket="firesphere-evidence", storage_provider=storage)

    eid = uuid.uuid4()
    tenant_id = "tenant-industrial-zone"
    expected_key = f"tenants/{tenant_id}/evidence/{eid}"
    payload = b"\xff\xd8\xff\xe0" + b"\x12\x34" * 50

    # Pre-populate storage at tenant path
    storage.store_object("firesphere-evidence", expected_key, payload)

    # Fetch through evidence client
    fetched = client.fetch_evidence_bytes(evidence_id=eid, tenant_id=tenant_id)
    assert fetched == payload
