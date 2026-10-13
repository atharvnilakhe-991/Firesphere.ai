"""Shared pytest fixtures for FireSphere AI Service test suite."""

import uuid
import pytest
from fastapi.testclient import TestClient
from app.main import app


@pytest.fixture
def client() -> TestClient:
    """Provides a FastAPI TestClient instance."""
    return TestClient(app)


@pytest.fixture
def sample_inspection_id() -> str:
    """Returns a valid random inspection UUID string."""
    return str(uuid.uuid4())


@pytest.fixture
def sample_evidence_ids() -> list:
    """Returns a list of two valid random evidence UUID strings."""
    return [str(uuid.uuid4()), str(uuid.uuid4())]


@pytest.fixture
def valid_inspection_payload(sample_inspection_id, sample_evidence_ids) -> dict:
    """Returns a valid inspection analysis request dictionary."""
    return {
        "inspectionId": sample_inspection_id,
        "evidenceIds": sample_evidence_ids,
        "testMode": False,
    }
