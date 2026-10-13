"""Health and readiness response schemas."""

from datetime import datetime, timezone
from typing import Dict, Any
from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    """Payload returned by the liveness check endpoint."""

    status: str = Field(default="HEALTHY", description="Service liveness status")
    service: str = Field(description="Name of the service")
    version: str = Field(description="Semantic version of the AI service")
    environment: str = Field(description="Deployment environment (e.g. development, production)")
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc), description="UTC timestamp of the health check")


class ReadinessResponse(BaseModel):
    """Payload returned by the readiness check endpoint."""

    status: str = Field(description="Service readiness status (READY or NOT_READY)")
    service: str = Field(description="Name of the service")
    version: str = Field(description="Semantic version of the AI service")
    components: Dict[str, str] = Field(
        description="Individual status of required internal and external components"
    )
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc), description="UTC timestamp of readiness check")
