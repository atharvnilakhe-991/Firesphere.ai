"""FireSphere.ai — Phase 5 Independent VLM Analysis API Endpoints.

Exposes endpoints for component status and independent vision-language model analysis.
Enforces service authentication, tenant isolation, and advisory-only boundaries.
"""

import base64
from typing import Optional
import uuid
from fastapi import APIRouter, Depends, Header, HTTPException, status

from app.core.auth import verify_service_token
from app.core.logging import logger
from app.models.gemini_schemas import GeminiAnalysisResult
from app.schemas.vlm import VLMAnalysisRequest, VLMStatusResponse
from app.services.vlm_service import vlm_service

router = APIRouter(tags=["Vision-Language Model (VLM) Analysis"])


@router.get(
    "/vlm/status",
    response_model=VLMStatusResponse,
    status_code=status.HTTP_200_OK,
    summary="Get VLM Component Status",
    description="Returns component readiness, configured model, runtime device, and inference availability.",
)
async def get_vlm_status() -> VLMStatusResponse:
    """Returns honest status of the VLM analysis component."""
    return VLMStatusResponse(**vlm_service.get_status())


@router.post(
    "/vlm-analysis",
    response_model=GeminiAnalysisResult,
    status_code=status.HTTP_200_OK,
    summary="Execute Independent VLM Visual Inspection Analysis",
    description=(
        "Analyzes inspection evidence photograph independently using the configured Vision-Language Model. "
        "Returns evidence-linked, structured advisory observations with visual descriptions and verification guidance. "
        "NOTE: Strictly advisory; does NOT issue legal Fire NOC compliance decisions."
    ),
)
async def analyze_evidence_vlm(
    request: VLMAnalysisRequest,
    x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-ID"),
    _authenticated: bool = Depends(verify_service_token),
) -> GeminiAnalysisResult:
    """Entry point for independent VLM visual analysis."""
    # Tenant boundary enforcement: if X-Tenant-ID header is supplied, verify consistency
    effective_tenant = x_tenant_id or request.tenantId or "default"
    if x_tenant_id and request.tenantId and x_tenant_id != request.tenantId:
        logger.warning(
            f"VLM API: Tenant mismatch | header={x_tenant_id} != body={request.tenantId}"
        )
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Header X-Tenant-ID does not match request tenantId.",
        )

    # Decode base64 image bytes if provided
    raw_bytes: Optional[bytes] = None
    if request.imageBytesBase64:
        try:
            raw_bytes = base64.b64decode(request.imageBytesBase64)
        except Exception as b64_err:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail=f"Malformed base64 image payload: {type(b64_err).__name__}",
            )

    return vlm_service.analyze_evidence(
        evidence_id=request.evidenceId,
        tenant_id=effective_tenant,
        image_bytes=raw_bytes,
        expected_sha256=request.expectedSha256,
        context={"inspectionId": str(request.inspectionId)} if request.inspectionId else None,
    )
