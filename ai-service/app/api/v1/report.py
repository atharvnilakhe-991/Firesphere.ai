"""Inspection report drafting API endpoint."""

from fastapi import APIRouter, Depends, status
from app.core.auth import verify_service_token
from app.schemas.report import (
    InspectionReportDraft,
    InspectionReportDraftRequest,
)
from app.services.inspection_report_service import inspection_report_service

router = APIRouter(tags=["AI Inspection Report Drafting"])


@router.post(
    "/inspection-report-draft",
    response_model=InspectionReportDraft,
    status_code=status.HTTP_200_OK,
    summary="Generate AI Inspection Report Draft",
    description=(
        "Converts structured inspection findings, risk evaluations, and evidence into an "
        "evidence-traceable inspection report draft for reviewing Fire Safety Officers.\n\n"
        "**Strict Governance Rule**: This AI service NEVER autonomously approves or rejects "
        "a Fire NOC. Output is strictly an advisory draft requiring authorized human Fire Officer verification."
    ),
)
async def generate_inspection_report_draft(
    request: InspectionReportDraftRequest,
    _authenticated: bool = Depends(verify_service_token),
) -> InspectionReportDraft:
    """Generates an executive, evidence-traceable inspection report draft."""
    return inspection_report_service.generate_report_draft(request)
