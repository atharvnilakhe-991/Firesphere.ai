"""FireSphere.ai — Phase 3 Officer Verification Service.

Enforces server-side authorization, optimistic concurrency locking, audit history preservation,
and strict safety boundaries for human Fire Safety Officer finding reviews.
"""

from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Set
import uuid
from fastapi import HTTPException, status

from app.core.logging import logger
from app.schemas.finding import Finding, FindingSeverity
from app.schemas.verification import (
    OfficerAssessmentType,
    OfficerFindingVerificationRequest,
    OfficerFindingVerificationResponse,
)
from app.services.finding_repository import BaseFindingRepository, finding_repository


class OfficerVerificationService:
    """Coordinates officer reviews, assessment recording, manual corrections, and concurrency control."""

    # Authorized roles permitted to review findings and record safety assessments
    AUTHORIZED_OFFICER_ROLES: Set[str] = {
        "FIRE_OFFICER",
        "LEAD_INSPECTOR",
        "CHIEF_FIRE_OFFICER",
        "FIRE_SAFETY_DIRECTOR",
        "AUTHORIZED_INSPECTOR",
        "ADMIN",
    }

    def __init__(self, repository: Optional[BaseFindingRepository] = None):
        self.repository = repository or finding_repository

    def verify_finding(
        self,
        request: OfficerFindingVerificationRequest,
        auth_verified: bool = True,
        inspection_id: Optional[uuid.UUID] = None,
        tenant_id: Optional[str] = None,
        trusted_officer_id: Optional[str] = None,
        trusted_officer_role: Optional[str] = None,
    ) -> OfficerFindingVerificationResponse:
        """Processes and records an authorized human Fire Officer finding verification.

        Enforces:
        1. Valid service-to-service authentication.
        2. Role-based access control (RBAC): rejecting unauthorized client roles.
        3. Authoritative finding existence in repository (404 on nonexistent findings; no synthetic stubs).
        4. Strict inspection ownership: finding must belong to the URL inspection_id.
        5. Tenant boundary isolation: cross-tenant access is rejected with 403; missing tenant context is rejected with 403 when finding is tenant-scoped.
        6. Trusted identity assertion vs caller-supplied body data: gateway headers take precedence and are verified against body data.
        7. Optimistic concurrency locking via finding version.
        8. Independent evidence-reviewed state (does not auto-confirm hazard).
        9. Full audit history preservation of previous and modified states.
        """
        if not auth_verified:
            logger.warning("OFFICER_VERIFICATION_REJECTED | Service authentication invalid or missing.")
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail={
                    "status": "FAILED",
                    "errorCode": "UNAUTHORIZED_SERVICE",
                    "message": "Authentication required to verify findings.",
                },
            )

        # 1. Trusted Officer Identity vs Caller-Supplied Body Fields
        trusted_identity_assertion = False
        identity_source = "CALLER_SUPPLIED_BODY_PENDING_SPRING_BOOT_VERIFICATION"

        if trusted_officer_role is not None:
            norm_trusted_role = trusted_officer_role.strip().upper()
            if norm_trusted_role not in self.AUTHORIZED_OFFICER_ROLES:
                logger.warning(
                    f"OFFICER_VERIFICATION_FORBIDDEN | Trusted gateway role '{norm_trusted_role}' "
                    f"is not authorized for findingId={request.findingId}"
                )
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail={
                        "status": "FAILED",
                        "errorCode": "FORBIDDEN_ROLE",
                        "message": (
                            f"Role '{norm_trusted_role}' is not authorized to verify fire inspection findings. "
                            "Authorized officer credentials are required."
                        ),
                    },
                )
            body_role_norm = request.officerRole.strip().upper()
            if body_role_norm != norm_trusted_role:
                logger.warning(
                    f"OFFICER_ROLE_MISMATCH | Request body officerRole '{request.officerRole}' "
                    f"contradicts trusted gateway assertion '{norm_trusted_role}'."
                )
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail={
                        "status": "FAILED",
                        "errorCode": "OFFICER_ROLE_MISMATCH",
                        "message": (
                            f"Officer role in request body '{request.officerRole}' does not match "
                            f"trusted gateway assertion '{norm_trusted_role}'."
                        ),
                        "headerOfficerRole": norm_trusted_role,
                        "bodyOfficerRole": request.officerRole,
                    },
                )
            effective_role = norm_trusted_role
            trusted_identity_assertion = True
            identity_source = "TRUSTED_FORWARDED_ASSERTION"
        else:
            role_norm = request.officerRole.strip().upper()
            if role_norm not in self.AUTHORIZED_OFFICER_ROLES:
                logger.warning(
                    f"OFFICER_VERIFICATION_FORBIDDEN | Unauthorized role '{request.officerRole}' "
                    f"attempted to verify findingId={request.findingId} (officerId={request.officerId})"
                )
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail={
                        "status": "FAILED",
                        "errorCode": "FORBIDDEN_ROLE",
                        "message": (
                            f"Role '{request.officerRole}' is not authorized to verify fire inspection findings. "
                            "Authorized officer credentials are required."
                        ),
                    },
                )
            effective_role = role_norm

        if trusted_officer_id is not None:
            norm_trusted_id = trusted_officer_id.strip()
            body_id_norm = request.officerId.strip()
            if body_id_norm != norm_trusted_id:
                logger.warning(
                    f"OFFICER_ID_MISMATCH | Request body officerId '{request.officerId}' "
                    f"contradicts trusted gateway assertion '{norm_trusted_id}'."
                )
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail={
                        "status": "FAILED",
                        "errorCode": "OFFICER_ID_MISMATCH",
                        "message": (
                            f"Officer ID in request body '{request.officerId}' does not match "
                            f"trusted gateway assertion '{norm_trusted_id}'."
                        ),
                        "headerOfficerId": norm_trusted_id,
                        "bodyOfficerId": request.officerId,
                    },
                )
            effective_officer_id = norm_trusted_id
            trusted_identity_assertion = True
            identity_source = "TRUSTED_FORWARDED_ASSERTION"
        else:
            effective_officer_id = request.officerId.strip()

        # 2. Validate URL vs Request Body consistency early
        if inspection_id is not None and request.inspectionId is not None and str(request.inspectionId) != str(inspection_id):
            logger.warning(
                f"INSPECTION_BODY_URL_MISMATCH | URL inspectionId={inspection_id} "
                f"differs from body inspectionId={request.inspectionId}."
            )
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail={
                    "status": "FAILED",
                    "errorCode": "INSPECTION_FINDING_MISMATCH",
                    "message": (
                        f"Inspection ID in request body '{request.inspectionId}' does not match URL inspection ID '{inspection_id}'. "
                        "The URL path is authoritative."
                    ),
                    "urlInspectionId": str(inspection_id),
                    "bodyInspectionId": str(request.inspectionId),
                },
            )

        # 3. Validate Header vs Request Body Tenant ID consistency early
        norm_tenant_hdr = tenant_id.strip() if tenant_id and tenant_id.strip() else None
        norm_tenant_body = request.tenantId.strip() if request.tenantId and request.tenantId.strip() else None
        if norm_tenant_hdr is not None and norm_tenant_body is not None and norm_tenant_hdr != norm_tenant_body:
            logger.warning(
                f"TENANT_HEADER_BODY_MISMATCH | Header tenantId='{norm_tenant_hdr}' "
                f"differs from body tenantId='{norm_tenant_body}'."
            )
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail={
                    "status": "FAILED",
                    "errorCode": "TENANT_CONTEXT_MISMATCH",
                    "message": (
                        f"Tenant ID in request body '{norm_tenant_body}' does not match trusted header tenant ID '{norm_tenant_hdr}'. "
                        "The trusted header context is authoritative."
                    ),
                    "headerTenantId": norm_tenant_hdr,
                    "bodyTenantId": norm_tenant_body,
                },
            )
        target_tenant_id = norm_tenant_hdr or norm_tenant_body

        # 4. Retrieve existing finding from repository (fail-closed if nonexistent)
        finding = self.repository.get_finding(request.findingId)
        if not finding:
            logger.warning(f"OFFICER_VERIFICATION_REJECTED | Finding {request.findingId} not found in repository.")
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail={
                    "status": "FAILED",
                    "errorCode": "FINDING_NOT_FOUND",
                    "message": f"Finding '{request.findingId}' was not found in the inspection repository.",
                    "findingId": str(request.findingId),
                },
            )

        # 5. Authoritative Inspection Ownership Validation
        target_inspection_id = inspection_id or request.inspectionId
        if target_inspection_id is not None:
            if finding.inspectionId is not None and str(finding.inspectionId) != str(target_inspection_id):
                logger.warning(
                    f"INSPECTION_FINDING_MISMATCH | Finding {request.findingId} belongs to inspection "
                    f"'{finding.inspectionId}', but verification requested for inspection '{target_inspection_id}'."
                )
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail={
                        "status": "FAILED",
                        "errorCode": "INSPECTION_FINDING_MISMATCH",
                        "message": (
                            f"Finding '{request.findingId}' does not belong to inspection '{target_inspection_id}'. "
                            "Cross-inspection verification is strictly prohibited."
                        ),
                        "requestedInspectionId": str(target_inspection_id),
                        "actualInspectionId": str(finding.inspectionId),
                    },
                )
            elif finding.inspectionId is None:
                finding.inspectionId = target_inspection_id

        # 6. Tenant Boundary Isolation Enforcement
        if finding.tenantId is not None:
            if target_tenant_id is None:
                logger.warning(
                    f"TENANT_CONTEXT_MISSING | Finding {request.findingId} belongs to tenant "
                    f"'{finding.tenantId}', but no tenant context was provided."
                )
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail={
                        "status": "FAILED",
                        "errorCode": "TENANT_CONTEXT_REQUIRED",
                        "message": (
                            f"Finding '{request.findingId}' is tenant-scoped to '{finding.tenantId}'. "
                            "Trusted tenant context (X-Tenant-ID header) is required to access or verify this finding."
                        ),
                        "findingTenantId": str(finding.tenantId),
                    },
                )
            elif str(finding.tenantId) != str(target_tenant_id):
                logger.warning(
                    f"TENANT_ISOLATION_VIOLATION | Finding {request.findingId} belongs to tenant "
                    f"'{finding.tenantId}', but request context is tenant '{target_tenant_id}'."
                )
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail={
                        "status": "FAILED",
                        "errorCode": "TENANT_ACCESS_FORBIDDEN",
                        "message": "Access to finding across tenant boundaries is forbidden.",
                        "requestedTenantId": str(target_tenant_id),
                        "actualTenantId": str(finding.tenantId),
                    },
                )
        elif target_tenant_id is not None:
            finding.tenantId = target_tenant_id

        # 7. Optimistic Concurrency Control (Version check)
        if request.expectedVersion is not None and request.expectedVersion != finding.version:
            logger.warning(
                f"CONCURRENCY_CONFLICT | FindingId={request.findingId} version mismatch. "
                f"Expected={request.expectedVersion}, Current={finding.version}"
            )
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail={
                    "status": "FAILED",
                    "errorCode": "CONCURRENCY_CONFLICT",
                    "message": (
                        f"Concurrent update conflict: finding version has changed (current: {finding.version}, "
                        f"submitted: {request.expectedVersion}). Please reload the finding and re-submit."
                    ),
                    "currentVersion": finding.version,
                },
            )

        # 8. Snapshot current state for audit history
        prev_assessment = finding.officerAssessment
        prev_reviewed = finding.evidenceReviewed
        prev_category = finding.findingType
        prev_severity = finding.severity.value if hasattr(finding.severity, "value") else str(finding.severity)
        now_utc = datetime.now(timezone.utc)

        # 9. Determine updated verification status
        if request.assessment == OfficerAssessmentType.CONFIRMED_HAZARD:
            new_status = "OFFICER_CONFIRMED"
        elif request.assessment == OfficerAssessmentType.NOT_A_HAZARD:
            new_status = "OFFICER_DISMISSED"
        else:
            new_status = "UNDER_REVIEW"

        # 10. Build corrections dictionary
        corrections: Dict[str, Any] = {}
        if request.categoryOverride:
            corrections["categoryOverride"] = request.categoryOverride
        if request.severityOverride:
            corrections["severityOverride"] = request.severityOverride.value if hasattr(request.severityOverride, "value") else str(request.severityOverride)
        if request.additionalEvidenceIds:
            corrections["additionalEvidenceIds"] = [str(eid) for eid in request.additionalEvidenceIds]

        # 11. Record immutable audit entry
        audit_entry = {
            "timestamp": now_utc.isoformat(),
            "officerId": effective_officer_id,
            "officerRole": effective_role,
            "action": "OFFICER_FINDING_VERIFICATION",
            "previousAssessment": prev_assessment,
            "newAssessment": request.assessment.value,
            "previousEvidenceReviewed": prev_reviewed,
            "newEvidenceReviewed": request.evidenceReviewed,
            "remarks": request.remarks,
            "correctionsApplied": corrections if corrections else None,
            "version": finding.version,
            "serviceAuthenticated": True,
            "trustedIdentityAssertion": trusted_identity_assertion,
            "identitySource": identity_source,
            "tenantId": finding.tenantId,
        }

        # 12. Apply updates to finding
        # STRICT SAFETY INVARIANT: Checking evidenceReviewed does not automatically confirm hazard
        finding.evidenceReviewed = request.evidenceReviewed
        finding.officerAssessment = request.assessment.value
        finding.officerVerificationStatus = new_status
        finding.officerRemarks = request.remarks
        finding.reviewedByOfficerId = effective_officer_id
        finding.reviewedAt = now_utc
        finding.updatedAt = now_utc
        if corrections:
            finding.officerCorrections = corrections
        finding.version += 1
        finding.auditHistory.append(audit_entry)

        # 13. Persist updated finding in repository
        self.repository.update_finding(finding)

        logger.info(
            f"OFFICER_VERIFICATION_RECORDED | findingId={finding.findingId} | "
            f"inspectionId={finding.inspectionId} | officerId={effective_officer_id} | "
            f"assessment={request.assessment.value} | evidenceReviewed={request.evidenceReviewed} | "
            f"newVersion={finding.version} | trustedIdentity={trusted_identity_assertion}"
        )

        return OfficerFindingVerificationResponse(
            findingId=finding.findingId,
            inspectionId=finding.inspectionId,
            tenantId=finding.tenantId,
            verificationStatus=new_status,
            officerAssessment=request.assessment,
            evidenceReviewed=request.evidenceReviewed,
            updatedFinding=finding,
            version=finding.version,
            auditEntry=audit_entry,
            verifiedAt=now_utc,
            persistenceDurable=False,
            persistenceStatus="IN_MEMORY_EPHEMERAL_PENDING_SPRING_BOOT_PERSISTENCE",
            trustedIdentityAssertion=trusted_identity_assertion,
            identitySource=identity_source,
        )


# Global officer verification service singleton
officer_verification_service = OfficerVerificationService()
