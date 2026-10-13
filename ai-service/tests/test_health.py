"""Tests for health and readiness endpoints."""

from fastapi.testclient import TestClient


def test_health_endpoint_success(client: TestClient):
    """Verifies that GET /health returns 200 OK and expected structure."""
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "HEALTHY"
    assert data["service"] == "firesphere-ai-service"
    assert "version" in data
    assert "environment" in data
    assert "timestamp" in data


def test_readiness_endpoint_success(client: TestClient):
    """Verifies that GET /ready returns 200 OK and honest component statuses."""
    response = client.get("/ready")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "READY"
    assert "components" in data
    components = data["components"]
    assert "modelRunner" in components
    assert "evidenceStorage" in components
    assert "preprocessor" in components
    assert components["modelRunner"] == "READY"
    assert components["preprocessor"] == "READY"
    # Unconfigured storage is honestly reported as NOT_CONFIGURED without false reporting
    assert components["evidenceStorage"] in ["READY", "NOT_CONFIGURED"]
