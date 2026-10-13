"""API endpoints for asynchronous AI job creation, status polling, and result retrieval."""

from typing import Optional
import uuid
from fastapi import APIRouter, Depends, Header, HTTPException, status
from app.core.auth import verify_service_token
from app.core.error_codes import AIErrorCode
from app.core.logging import logger
from app.jobs.manager import job_manager
from app.schemas.job import (
    AIJobCreateRequest,
    AIJobResponse,
    AIJobResultResponse,
)

router = APIRouter(tags=["AI Jobs"])


@router.post(
    "/jobs",
    response_model=AIJobResponse,
    status_code=status.HTTP_202_ACCEPTED,
    summary="Enqueue Asynchronous AI Inspection Job",
    description=(
        "Dispatches an asynchronous AI inspection job to the queue. "
        "Returns HTTP 202 Accepted immediately with unique jobId. "
        "Supports client-supplied idempotencyKey to prevent duplicate runs."
    ),
)
async def create_ai_job(
    request: AIJobCreateRequest,
    x_correlation_id: Optional[str] = Header(default=None, alias="X-Correlation-ID"),
    _authenticated: bool = Depends(verify_service_token),
) -> AIJobResponse:
    """Enqueues an AI inspection job asynchronously."""
    correlation_id = x_correlation_id or str(uuid.uuid4())
    logger.info(
        f"Received async AI job request | inspectionId={request.inspectionId} | "
        f"evidenceCount={len(request.evidenceIds)} | tenantId={request.tenantId} | "
        f"idempotencyKey={request.idempotencyKey or 'none'} | correlationId={correlation_id}"
    )

    job_resp, is_duplicate = job_manager.submit_job(request, correlation_id=correlation_id)
    return job_resp


@router.get(
    "/jobs/{job_id}",
    response_model=AIJobResponse,
    status_code=status.HTTP_200_OK,
    summary="Get AI Job Status",
    description="Retrieves current status, attempt count, and run/report identities for an AI job.",
)
async def get_ai_job_status(
    job_id: uuid.UUID,
    _authenticated: bool = Depends(verify_service_token),
) -> AIJobResponse:
    """Returns current status and execution metadata of the specified AI job."""
    job_resp = job_manager.get_job(job_id)
    if not job_resp:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={
                "status": "FAILED",
                "errorCode": AIErrorCode.JOB_NOT_FOUND.value,
                "message": f"AI job '{job_id}' was not found in job registry.",
            },
        )
    return job_resp


@router.get(
    "/jobs/{job_id}/result",
    response_model=AIJobResultResponse,
    status_code=status.HTTP_200_OK,
    summary="Get AI Job Complete Result",
    description=(
        "Retrieves structured inspection analysis and report draft results for a completed AI job. "
        "Returns 404 if job does not exist, or current state if still in progress."
    ),
)
async def get_ai_job_result(
    job_id: uuid.UUID,
    _authenticated: bool = Depends(verify_service_token),
) -> AIJobResultResponse:
    """Returns final structured outputs (analysis + report draft) for a completed AI job."""
    result_resp = job_manager.get_job_result(job_id)
    if not result_resp:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={
                "status": "FAILED",
                "errorCode": AIErrorCode.JOB_NOT_FOUND.value,
                "message": f"AI job '{job_id}' was not found in job registry.",
            },
        )
    return result_resp
