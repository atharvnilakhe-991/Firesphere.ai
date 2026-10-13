"""FireSphere.ai — Phase 5 Vision-Language Model (VLM) Schemas.

Defines API request contracts, component status envelopes, and observation
validation models for independent VLM visual analysis.
"""

from typing import Any, Dict, List, Optional
import uuid
from pydantic import BaseModel, Field

from app.models.gemini_schemas import GeminiAnalysisResult, GeminiObservation


class VLMAnalysisRequest(BaseModel):
    """Request contract for independent VLM visual inspection analysis."""

    evidenceId: uuid.UUID = Field(
        description="Unique inspection evidence identifier",
    )
    tenantId: Optional[str] = Field(
        default="default",
        description="Tenant organization identifier for isolation enforcement",
    )
    inspectionId: Optional[uuid.UUID] = Field(
        default=None,
        description="Optional parent inspection identifier if linked to a session",
    )
    expectedSha256: Optional[str] = Field(
        default=None,
        description="Optional expected SHA-256 hash for evidence integrity verification",
    )
    imageBytesBase64: Optional[str] = Field(
        default=None,
        description="Optional base64-encoded raw image bytes for direct evaluation",
    )


class VLMStatusResponse(BaseModel):
    """Component status envelope for independent VLM analysis."""

    service: str = Field(default="firesphere-vlm-service")
    provider: str = Field(default="google-gemini")
    model: str = Field(description="Configured model identifier")
    modelVersion: str = Field(default="gemini-genai-v2")
    enabled: bool = Field(description="Whether VLM analysis is enabled in configuration")
    status: str = Field(description="Component readiness: READY, DISABLED, NOT_CONFIGURED, BLOCKED")
    runtimeDevice: str = Field(default="cpu", description="Execution hardware: cpu or cuda")
    realInferenceStatus: str = Field(
        description="Status of live inference: OPERATIONAL or BLOCKED"
    )
    blockerReason: Optional[str] = Field(
        default=None,
        description="Specific technical blocker if real inference is blocked",
    )
    governancePolicy: str = Field(
        default="ADVISORY_ONLY_HUMAN_OFFICER_VERIFICATION_MANDATORY",
        description="Strict safety invariant",
    )
