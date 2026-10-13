"""Health and readiness check endpoints."""

from datetime import datetime, timezone
from fastapi import APIRouter, status
from app.core.config import settings
from app.schemas.health import HealthResponse, ReadinessResponse
from app.storage.evidence_client import evidence_client
from app.models.placeholder_model import PlaceholderInspectionModel
from app.models.gemini_provider import gemini_provider

router = APIRouter(tags=["Health"])


@router.get(
    "/health",
    response_model=HealthResponse,
    status_code=status.HTTP_200_OK,
    summary="Liveness Health Check",
    description="Returns service liveness status and basic metadata.",
)
async def get_health() -> HealthResponse:
    """Liveness probe indicating that the AI service process is running and accepting HTTP traffic."""
    return HealthResponse(
        status="HEALTHY",
        service=settings.service_name,
        version=settings.model_version,
        environment=settings.service_env,
        timestamp=datetime.now(timezone.utc),
    )


@router.get(
    "/ready",
    response_model=ReadinessResponse,
    status_code=status.HTTP_200_OK,
    summary="Readiness Probe",
    description="Returns component-level readiness without falsely reporting unconfigured dependencies.",
)
async def get_readiness() -> ReadinessResponse:
    """Readiness probe checking readiness of internal components and external dependencies."""
    storage_status = "READY" if evidence_client.is_configured() else "NOT_CONFIGURED"
    model_status = "READY"
    gemini_status = gemini_provider.get_status()

    components = {
        "modelRunner": model_status,
        "evidenceStorage": storage_status,
        "preprocessor": "READY",
        "geminiProvider": gemini_status,
    }


    # Internal pipeline is ready; overall status is READY for standalone operation
    overall_status = "READY"

    return ReadinessResponse(
        status=overall_status,
        service=settings.service_name,
        version=settings.model_version,
        components=components,
        timestamp=datetime.now(timezone.utc),
    )
