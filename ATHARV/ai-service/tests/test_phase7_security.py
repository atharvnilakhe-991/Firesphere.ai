"""Automated tests for Phase 7 Security, Tenant Isolation, and Input Hardening."""

from pathlib import Path
import re
import uuid
import pytest
from fastapi.testclient import TestClient
from app.core.config import settings
from app.core.error_codes import AIErrorCode
from app.storage.s3_storage import (
    IntegrityCheckException,
    MockStorageProvider,
)


def test_service_authentication_enforcement(client: TestClient, monkeypatch):
    """Verifies that unauthorized requests are rejected with 401 and structured error."""
    monkeypatch.setattr(settings, "service_auth_token", "production-secret-token-xyz")
    monkeypatch.setattr(settings, "auth_enabled", True)

    payload = {
        "inspectionId": str(uuid.uuid4()),
        "evidenceIds": [str(uuid.uuid4())],
        "testMode": True,
    }

    # 1. Missing header
    r1 = client.post("/internal/ai/jobs", json=payload)
    assert r1.status_code == 401
    assert r1.json()["errorCode"] == AIErrorCode.UNAUTHORIZED_SERVICE.value

    # 2. Tampered header
    r2 = client.post("/internal/ai/jobs", json=payload, headers={"X-AI-Service-Token": "bad-token"})
    assert r2.status_code == 401
    assert r2.json()["errorCode"] == AIErrorCode.UNAUTHORIZED_SERVICE.value

    # 3. Valid header
    r3 = client.post("/internal/ai/jobs", json=payload, headers={"X-AI-Service-Token": "production-secret-token-xyz"})
    assert r3.status_code == 202


def test_storage_path_traversal_rejection():
    """Verifies that attempts to escape object storage directories raise exceptions."""
    storage = MockStorageProvider()
    malicious_keys = [
        "../../etc/passwd",
        "/absolute/root/path.jpg",
        "evidence/../../../secret.env",
        "http://attacker.com/exploit.jpg",
        "https://evil.org/malware.exe",
        "tenants/../other_tenant/file.jpg",
    ]

    for key in malicious_keys:
        with pytest.raises(Exception, match="forbidden"):
            storage.sanitize_key(key)


def test_cryptographic_checksum_tampering():
    """Verifies that modified image payloads trigger IntegrityCheckException."""
    storage = MockStorageProvider()
    original_data = b"VALID_FIRESPHERE_FIRE_PHOTOGRAPH_PAYLOAD"
    correct_hash = storage.compute_sha256(original_data)
    storage.store_object("firesphere-evidence", "evidence/photo1.jpg", original_data)

    # Valid checksum succeeds
    retrieved = storage.fetch_object("firesphere-evidence", "evidence/photo1.jpg", expected_checksum=correct_hash)
    assert retrieved == original_data

    # Tampered checksum raises IntegrityCheckException
    tampered_hash = "f" * 64
    with pytest.raises(IntegrityCheckException, match="integrity check"):
        storage.fetch_object("firesphere-evidence", "evidence/photo1.jpg", expected_checksum=tampered_hash)


def test_tenant_context_isolation(client: TestClient):
    """Verifies tenant identity is enforced and preserved in job metadata."""
    insp_id = uuid.uuid4()
    eid = uuid.uuid4()
    t_id = "tenant-mumbai-central"

    payload = {
        "inspectionId": str(insp_id),
        "evidenceIds": [str(eid)],
        "tenantId": t_id,
        "testMode": True,
    }

    resp = client.post("/internal/ai/jobs", json=payload)
    assert resp.status_code == 202
    data = resp.json()
    assert data["tenantId"] == t_id

    # Verify query status preserves tenant
    job_id = data["jobId"]
    st_resp = client.get(f"/internal/ai/jobs/{job_id}")
    assert st_resp.status_code == 200
    assert st_resp.json()["tenantId"] == t_id


def test_no_real_secrets_in_codebase():
    """Audits repository files to confirm no live API keys, tokens, or passwords are hardcoded."""
    root_dir = Path(__file__).resolve().parent.parent
    secret_patterns = [
        re.compile(r"""(?:aws_secret_access_key|api_key|password)\s*=\s*['"][a-zA-Z0-9_\-+/]{20,}['"]""", re.IGNORECASE),
        re.compile(r"""(?:AKIA[0-9A-Z]{16})"""),  # AWS Access Key ID pattern
    ]

    for py_file in root_dir.glob("app/**/*.py"):
        text = py_file.read_text(encoding="utf-8", errors="ignore")
        for pat in secret_patterns:
            matches = pat.findall(text)
            assert len(matches) == 0, f"Potential hardcoded secret found in {py_file}: {matches}"
