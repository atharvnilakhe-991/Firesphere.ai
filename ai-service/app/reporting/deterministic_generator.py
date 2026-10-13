"""Deterministic, audit-grade AI inspection report draft generator."""

from collections import Counter
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional
import uuid
from app.core.config import settings
from app.reporting.corrective_actions import CorrectiveActionEngine
from app.reporting.generator import BaseReportGenerator
from app.reporting.limitations import AILimitationsProvider
from app.reporting.review import ReviewLifecycleManager
from app.reporting.traceability import TraceabilityEngine
from app.schemas.finding import Finding, FindingSeverity
from app.schemas.report import (
    InspectionReportDraft,
    ReportAuditMetadata,
    ReportEvidenceItem,
    ReportExecutiveSummary,
    ReportFindingItem,
    ReportModelInfo,
    ReportRiskSummary,
    ReportStatus,
    ReportUnresolvedItem,
    ReportVerificationAction,
    ReviewState,
)


class DeterministicReportGenerator(BaseReportGenerator):
    """Generates deterministic, structured, evidence-traceable AI inspection report drafts.

    Adheres strictly to human-in-the-loop governance:
    - Produces an advisory report draft for reviewing human Fire Safety Officers.
    - Never fabricates building details, code sections, penalty numbers, or deadlines.
    - Ensures identical inputs produce identical, reproducible report text and structure.
    """

    SEVERITY_RANK = {
        FindingSeverity.CRITICAL.value: 5,
        FindingSeverity.HIGH.value: 4,
        FindingSeverity.MEDIUM.value: 3,
        FindingSeverity.LOW.value: 2,
        FindingSeverity.INFORMATIONAL.value: 1,
    }

    FINDING_DESCRIPTIONS = {
        "FIRE_EXTINGUISHER_MISSING": "AI analysis identified a possible vacant or absent fire extinguisher at a designated station.",
        "FIRE_EXTINGUISHER_BLOCKED": "AI analysis detected possible physical obstructions impeding immediate access to a fire extinguisher.",
        "FIRE_EXTINGUISHER_EXPIRED": "AI analysis detected an indicator suggesting an expired service collar or low-pressure condition on an extinguisher.",
        "EMERGENCY_EXIT_OBSTRUCTED": "AI analysis identified an apparent physical obstruction within an emergency exit doorway or corridor.",
        "EXIT_SIGNAGE_MISSING": "AI analysis detected possible missing, unlit, or obscured illuminated emergency exit signage.",
        "SPRINKLER_HEAD_DEFECT": "AI analysis identified possible physical distortion, paint overspray, or clearance defect on a sprinkler head.",
        "ELECTRICAL_PANEL_HAZARD": "AI analysis detected possible unlatched panel doors or combustible materials near electrical switchgear.",
        "COMBUSTIBLE_MATERIAL_HAZARD": "AI analysis identified possible excessive combustible materials stored near potential heat or ignition sources.",
        "MANUAL_CALL_POINT_BLOCKED": "AI analysis detected possible obstructions restricting access to a manual fire alarm call point.",
        "HOSE_REEL_OBSTRUCTED": "AI analysis identified possible access blockage or cabinet obstruction at a fire hose reel station.",
        "GENERIC_FIRE_SAFETY_DEFECT": "AI analysis identified an advisory visual condition flagged for officer review.",
    }

    AMBIGUITY_CHECKS = {
        "FIRE_EXTINGUISHER_MISSING": "Check if camera viewing angle conceals an extinguisher mounted nearby or if bracket is decommissioned.",
        "FIRE_EXTINGUISHER_BLOCKED": "Determine whether the apparent obstruction is transient (e.g. movable cart) or a permanent site fixture.",
        "FIRE_EXTINGUISHER_EXPIRED": "Confirm pressure gauge reading and inspect physical maintenance collar dates directly on cylinder.",
        "EMERGENCY_EXIT_OBSTRUCTED": "Verify whether detected objects permanently obstruct egress and test door latch functionality on site.",
        "EXIT_SIGNAGE_MISSING": "Verify whether alternative directional signage exists and test emergency illumination power supply.",
        "SPRINKLER_HEAD_DEFECT": "Inspect deflector clearance and confirm whether thermal bulb element is intact.",
        "ELECTRICAL_PANEL_HAZARD": "Verify physical working clearance (minimum 1m) and confirm panel interior is properly secured.",
        "COMBUSTIBLE_MATERIAL_HAZARD": "Assess volume and fuel load of stored materials relative to distance from ignition sources.",
        "MANUAL_CALL_POINT_BLOCKED": "Confirm clear line-of-sight approach and operational condition of alarm pull lever.",
        "HOSE_REEL_OBSTRUCTED": "Verify cabinet door swings freely 180 degrees and inspect nozzle shutoff valve.",
        "GENERIC_FIRE_SAFETY_DEFECT": "Perform visual walkthrough of the flagged zone to assess actual site conditions.",
    }

    def generate_draft(
        self,
        inspection_id: uuid.UUID,
        run_id: uuid.UUID,
        findings: List[Finding],
        evidence_ids: List[uuid.UUID],
        evidence_quality_records: Optional[Dict[str, str]] = None,
        evidence_warnings: Optional[Dict[str, List[str]]] = None,
        model_name: str = "firesphere-orchestrator-ensemble",
        model_version: str = "0.3.0",
        calibration_status: str = "NOT_CALIBRATED",
        execution_duration_ms: Optional[float] = None,
        occupancy_context: str = "UNKNOWN",
        historical_context: str = "UNAVAILABLE",
        officer_remarks: Optional[str] = None,
        checklist_data: Optional[Dict[str, Any]] = None,
        is_test_data: bool = False,
        cv_model_name: Optional[str] = None,
        cv_model_version: Optional[str] = None,
        cv_status: Optional[str] = None,
        cv_approval_status: Optional[str] = None,
        cv_calibration_status: Optional[str] = None,
        vlm_model_name: Optional[str] = None,
        vlm_model_version: Optional[str] = None,
        vlm_status: Optional[str] = None,
        overall_status: Optional[str] = None,
    ) -> InspectionReportDraft:
        """Generates a structured, evidence-traceable inspection report draft."""
        trace = TraceabilityEngine()
        qual_records = evidence_quality_records or {}
        warn_records = evidence_warnings or {}

        # Check if any finding indicates test data
        has_test_findings = any(f.isTestData for f in findings)
        effective_is_test = is_test_data or has_test_findings

        # 1. Deterministic sorting of findings
        sorted_findings = sorted(
            findings,
            key=lambda f: (
                -self.SEVERITY_RANK.get(f.severity.value if hasattr(f.severity, "value") else str(f.severity), 0),
                -(f.operationalRiskScore if f.operationalRiskScore is not None else -1),
                f.findingType,
                str(f.findingId),
            ),
        )

        # 2. Executive Summary Generation
        total_findings = len(sorted_findings)
        unique_types = len({f.findingType for f in sorted_findings})

        highest_sev = None
        max_rank = 0
        for f in sorted_findings:
            sev_str = f.severity.value if hasattr(f.severity, "value") else str(f.severity)
            rank = self.SEVERITY_RANK.get(sev_str, 0)
            if rank > max_rank:
                max_rank = rank
                highest_sev = sev_str

        highest_risk_score = max(
            [f.operationalRiskScore for f in sorted_findings if f.operationalRiskScore is not None],
            default=None,
        )
        highest_risk_level = next(
            (f.operationalRiskLevel for f in sorted_findings if f.operationalRiskLevel),
            None,
        )

        if total_findings == 0:
            exec_narrative = (
                f"AI analysis completed across {len(evidence_ids)} submitted evidence item(s). "
                "No AI findings were generated from the submitted evidence. "
                "Note: The absence of AI detections indicates only that no supported visual defects were recognized "
                "by the visual analysis models; it does NOT certify that the building is compliant or free of fire hazards. "
                "Mandatory human Fire Officer physical inspection is required before any regulatory compliance decision."
            )
        else:
            risk_score_str = str(highest_risk_score) if highest_risk_score is not None else "Not available from AI analysis"
            risk_lvl_str = highest_risk_level if highest_risk_level else "Not assessed"
            exec_narrative = (
                f"AI analysis identified {total_findings} finding(s) across {len(evidence_ids)} submitted evidence item(s) "
                f"encompassing {unique_types} distinct safety defect category(ies). "
                f"The highest operational severity is {highest_sev} with a maximum operational risk score of "
                f"{risk_score_str} ({risk_lvl_str}). "
                "All visual findings are advisory observations. Human Fire Safety Officer on-site verification "
                "is mandatory before any statutory or Fire NOC compliance decision."
            )

        exec_summary = ReportExecutiveSummary(
            totalFindings=total_findings,
            uniqueFindingTypes=unique_types,
            highestSeverity=highest_sev,
            highestRiskLevel=highest_risk_level,
            highestRiskScore=highest_risk_score,
            evidenceCount=len(evidence_ids),
            requiresOfficerReview=True,
            findingsRequiringOfficerReview=total_findings,
            narrative=exec_narrative,
        )
        trace.record_statement(
            report_section="EXECUTIVE_SUMMARY",
            statement_text=exec_narrative,
            source_type="PIPELINE_METADATA",
            source_id=str(run_id),
            source_field="narrative",
        )

        # 3. Evidence Reviewed Section
        evidence_items: List[ReportEvidenceItem] = []
        for eid in evidence_ids:
            eid_str = str(eid)
            linked_fids = [f.findingId for f in sorted_findings if f.evidenceId == eid]
            e_item = ReportEvidenceItem(
                evidenceId=eid,
                evidenceQuality=qual_records.get(eid_str, "ACCEPTABLE"),
                processingStatus="SUCCESS",
                warnings=warn_records.get(eid_str, []),
                findingsCount=len(linked_fids),
                associatedFindingIds=linked_fids,
            )
            evidence_items.append(e_item)
            trace.record_statement(
                report_section="EVIDENCE_REVIEWED",
                statement_text=f"Evidence {eid_str} evaluated with quality rating '{e_item.evidenceQuality}'.",
                source_type="EVIDENCE",
                source_id=eid_str,
                evidence_id=eid,
                source_field="evidenceQuality",
            )

        # 4. Structured Findings Section
        report_findings: List[ReportFindingItem] = []
        for f in sorted_findings:
            desc = self.FINDING_DESCRIPTIONS.get(
                f.findingType,
                f"AI analysis identified a possible {f.findingType.replace('_', ' ').lower()} condition.",
            )
            rf_item = ReportFindingItem(
                findingId=f.findingId,
                evidenceId=f.evidenceId,
                findingType=f.findingType,
                confidence=f.confidence,
                confidenceLevel=getattr(f, "confidenceLevel", "MEDIUM"),
                calibrationStatus=getattr(f, "calibrationStatus", calibration_status),
                severity=f.severity,
                severityReason=getattr(f, "severityReason", None),
                operationalRiskScore=getattr(f, "operationalRiskScore", None),
                operationalRiskLevel=getattr(f, "operationalRiskLevel", None),
                riskFactors=getattr(f, "riskFactors", None),
                description=desc,
                explanation=f.explanation,
                verificationGuidance=getattr(f, "verificationGuidance", []),
                evidenceQuality=getattr(f, "evidenceQuality", qual_records.get(str(f.evidenceId), "ACCEPTABLE")),
                complianceStatus=getattr(f, "complianceStatus", "REQUIRES_OFFICER_VERIFICATION"),
                boundingBox=f.boundingBox,
                isTestData=f.isTestData,
                tenantId=getattr(f, "tenantId", None),
                inspectionId=getattr(f, "inspectionId", inspection_id),
                analysisRunId=getattr(f, "analysisRunId", run_id),
                officerVerificationStatus=getattr(f, "officerVerificationStatus", "UNREVIEWED"),
                knownLimitations=getattr(f, "knownLimitations", []),
                reconciliationStatus=getattr(f, "reconciliationStatus", "UNRECONCILED"),
                conflictType=getattr(f, "conflictType", "NONE"),
                conflictDetails=getattr(f, "conflictDetails", None),
                provenance=getattr(f, "provenance", []),
                validationStatus=getattr(f, "validationStatus", "VALID"),
                evidenceReviewed=getattr(f, "evidenceReviewed", False),
                officerAssessment=getattr(f, "officerAssessment", None),
                officerRemarks=getattr(f, "officerRemarks", None),
                officerCorrections=getattr(f, "officerCorrections", None),
            )
            report_findings.append(rf_item)

            trace.record_statement(
                report_section="FINDINGS",
                statement_text=desc,
                source_type="FINDING",
                source_id=str(f.findingId),
                evidence_id=f.evidenceId,
                finding_id=f.findingId,
                source_field="description",
            )
            trace.record_statement(
                report_section="FINDINGS",
                statement_text=f.explanation,
                source_type="FINDING",
                source_id=str(f.findingId),
                evidence_id=f.evidenceId,
                finding_id=f.findingId,
                source_field="explanation",
            )

        # 5. Operational Risk Summary Section
        risk_counts = dict(Counter(rf.operationalRiskLevel for rf in report_findings if rf.operationalRiskLevel))
        if total_findings == 0:
            risk_narrative = (
                "No operational fire safety non-compliances were identified by the automated CV analysis. "
                "Comprehensive on-site physical evaluation remains necessary."
            )
        else:
            dist_desc = ", ".join(f"{cnt} {lvl}" for lvl, cnt in sorted(risk_counts.items()))
            risk_narrative = (
                f"The AI analysis synthesized {total_findings} total finding(s) with risk distribution: {dist_desc}. "
                f"Maximum assessed operational risk level is {highest_risk_level or 'UNSPECIFIED'} "
                f"(score: {highest_risk_score or 0}/100)."
            )

        risk_summary = ReportRiskSummary(
            highestSeverity=highest_sev,
            highestRiskLevel=highest_risk_level,
            highestRiskScore=highest_risk_score,
            totalFindings=total_findings,
            riskDistribution=risk_counts,
            requiresOfficerVerification=True,
            narrative=risk_narrative,
        )
        trace.record_statement(
            report_section="RISK_SUMMARY",
            statement_text=risk_narrative,
            source_type="RISK_ENGINE",
            source_id=str(run_id),
            source_field="narrative",
        )

        # 6. Verification Actions Section
        verification_actions: List[ReportVerificationAction] = []
        for idx, rf in enumerate(report_findings, 1):
            guidance_text = (
                rf.verificationGuidance[0]
                if rf.verificationGuidance
                else f"Physically verify physical conditions of {rf.findingType.replace('_', ' ').lower()} on site."
            )
            ambiguity = self.AMBIGUITY_CHECKS.get(
                rf.findingType,
                "Verify whether observed visual features reflect permanent site non-compliance.",
            )
            va_item = ReportVerificationAction(
                actionId=f"VA-{idx:03d}",
                findingId=rf.findingId,
                findingType=rf.findingType,
                action=guidance_text,
                ambiguityCheck=ambiguity,
                priority=rf.severity.value if hasattr(rf.severity, "value") else str(rf.severity),
            )
            verification_actions.append(va_item)
            trace.record_statement(
                report_section="VERIFICATION_ACTIONS",
                statement_text=f"Action {va_item.actionId}: {guidance_text}",
                source_type="FINDING",
                source_id=str(rf.findingId),
                evidence_id=rf.evidenceId,
                finding_id=rf.findingId,
                source_field="verificationGuidance",
            )

        # 7. Corrective Actions Section
        corrective_actions: List[ReportCorrectiveAction] = []
        for idx, rf in enumerate(report_findings, 1):
            ca_item = CorrectiveActionEngine.generate_suggestion(idx, rf.findingId, rf.findingType)
            corrective_actions.append(ca_item)
            trace.record_statement(
                report_section="CORRECTIVE_ACTIONS",
                statement_text=f"Suggestion {ca_item.suggestionId}: {ca_item.suggestedAction}",
                source_type="FINDING",
                source_id=str(rf.findingId),
                evidence_id=rf.evidenceId,
                finding_id=rf.findingId,
                source_field="suggestedAction",
            )

        # 8. Unresolved Items Section
        unresolved_items: List[ReportUnresolvedItem] = []
        unres_idx = 1

        # Check for uncalibrated model
        if calibration_status == "NOT_CALIBRATED":
            unresolved_items.append(
                ReportUnresolvedItem(
                    itemId=f"UNRES-{unres_idx:03d}",
                    category="UNCALIBRATED_MODEL",
                    description="The CV model confidence scores are uncalibrated due to absence of verified validation dataset.",
                    recommendedAction="Treat raw confidence as qualitative feature activation; do not rely on it as statistical certainty.",
                )
            )
            unres_idx += 1

        # Check for marginal confidence findings
        for rf in report_findings:
            if rf.confidence < 0.60:
                unresolved_items.append(
                    ReportUnresolvedItem(
                        itemId=f"UNRES-{unres_idx:03d}",
                        category="LOW_CONFIDENCE_DETECTION",
                        description=f"Finding {rf.findingId} ({rf.findingType}) was detected with marginal confidence ({rf.confidence:.2f}).",
                        recommendedAction="Verify on-site whether this detection represents a true visual defect or camera artifact.",
                    )
                )
                unres_idx += 1

        # Check for degraded or unusable evidence quality
        for e_item in evidence_items:
            if e_item.evidenceQuality == "UNUSABLE":
                unresolved_items.append(
                    ReportUnresolvedItem(
                        itemId=f"UNRES-{unres_idx:03d}",
                        category="UNUSABLE_EVIDENCE",
                        description=f"Evidence {e_item.evidenceId} exhibits UNUSABLE quality with severe degradation.",
                        recommendedAction="Request immediate recapture of clear photographic evidence or conduct direct physical inspection.",
                    )
                )
                unres_idx += 1
            elif e_item.evidenceQuality == "POOR":
                unresolved_items.append(
                    ReportUnresolvedItem(
                        itemId=f"UNRES-{unres_idx:03d}",
                        category="DEGRADED_EVIDENCE_QUALITY",
                        description=f"Evidence {e_item.evidenceId} exhibits POOR photographic quality.",
                        recommendedAction="Request capture of clear, well-illuminated photographic evidence or inspect in person.",
                    )
                )
                unres_idx += 1

        # Check for unknown context
        if occupancy_context == "UNKNOWN":
            unresolved_items.append(
                ReportUnresolvedItem(
                    itemId=f"UNRES-{unres_idx:03d}",
                    category="MISSING_OCCUPANCY_CONTEXT",
                    description="Premises occupancy classification is UNKNOWN; risk score reflects baseline assumptions.",
                    recommendedAction="Verify occupancy type (e.g. assembly, industrial, commercial) during manual review.",
                )
            )
            unres_idx += 1

        # Check for synthetic test data
        if effective_is_test:
            unresolved_items.append(
                ReportUnresolvedItem(
                    itemId=f"UNRES-{unres_idx:03d}",
                    category="SYNTHETIC_TEST_DATA",
                    description="Analysis was conducted under testMode with synthetic test fixtures.",
                    recommendedAction="Discard for operational use; valid only for automated integration testing.",
                )
            )
            unres_idx += 1

        # Check for cross-model disagreements or discrepancies (Phase 3)
        for rf in report_findings:
            if getattr(rf, "conflictType", "NONE") not in ("NONE", None):
                conflict_desc = (
                    rf.conflictDetails.get("description", "Discrepancy detected between AI models.")
                    if rf.conflictDetails else "Model predictions diverged."
                )
                unresolved_items.append(
                    ReportUnresolvedItem(
                        itemId=f"UNRES-{unres_idx:03d}",
                        category=f"MODEL_DISAGREEMENT_{rf.conflictType}",
                        description=f"Model discrepancy on finding {rf.findingId} ({rf.findingType}): {conflict_desc}",
                        recommendedAction="Authorized Fire Officer on-site physical evaluation required to adjudicate model disagreement.",
                    )
                )
                unres_idx += 1

            if getattr(rf, "officerAssessment", None) == "ADDITIONAL_INSPECTION_REQUIRED":
                unresolved_items.append(
                    ReportUnresolvedItem(
                        itemId=f"UNRES-{unres_idx:03d}",
                        category="ADDITIONAL_INSPECTION_REQUIRED",
                        description=f"Finding {rf.findingId} requires additional physical on-site inspection as determined by Fire Officer.",
                        recommendedAction="Schedule follow-up physical inspection to re-evaluate flagged hazard condition.",
                    )
                )
                unres_idx += 1

        # Check for unavailable or failed VLM component
        effective_vlm_status = vlm_status or "UNAVAILABLE"
        if effective_vlm_status in ("UNAVAILABLE", "FAILED", "GEMINI_DISABLED", "DISABLED", "NOT_CONFIGURED"):
            unresolved_items.append(
                ReportUnresolvedItem(
                    itemId=f"UNRES-{unres_idx:03d}",
                    category="VLM_COMPONENT_UNAVAILABLE" if effective_vlm_status in ("UNAVAILABLE", "GEMINI_DISABLED", "DISABLED", "NOT_CONFIGURED") else "VLM_COMPONENT_FAILED",
                    description=f"VLM visual reasoning component was {effective_vlm_status}; multi-modal scene reasoning was not available for this draft.",
                    recommendedAction="Authorized Fire Officer physical visual inspection is mandatory to evaluate contextual hazards.",
                )
            )
            unres_idx += 1

        # Check for unapproved CV model state
        effective_cv_approval = cv_approval_status or "NOT_APPROVED"
        if effective_cv_approval == "NOT_APPROVED":
            unresolved_items.append(
                ReportUnresolvedItem(
                    itemId=f"UNRES-{unres_idx:03d}",
                    category="CV_MODEL_NOT_APPROVED",
                    description="CV baseline model approval state is NOT_APPROVED; detections are advisory research findings only.",
                    recommendedAction="All equipment presence and compliance must be independently verified by an authorized Fire Officer.",
                )
            )
            unres_idx += 1

        # 9. AI Limitations Section
        has_poor_qual = any(e.evidenceQuality in ("POOR", "UNUSABLE") for e in evidence_items)
        has_low_conf = any(rf.confidence < 0.60 for rf in report_findings)
        ai_limitations = AILimitationsProvider.get_limitations(
            has_findings=(total_findings > 0),
            has_low_confidence=has_low_conf,
            has_poor_quality=has_poor_qual,
            is_uncalibrated=(calibration_status == "NOT_CALIBRATED"),
            is_test_data=effective_is_test,
        )

        # 10. Officer Remarks & Checklist Formatting
        formatted_remarks = ReviewLifecycleManager.format_officer_remarks(officer_remarks)
        if checklist_data:
            checklist_summary = f"Checklist observations supplied: {len(checklist_data)} item(s) recorded."
        else:
            checklist_summary = "Checklist data was not provided to the AI report generator."

        # 11. Model & Audit Metadata
        model_info = ReportModelInfo(
            modelName=model_name,
            modelVersion=model_version,
            calibrationStatus=calibration_status,
            executionDurationMs=execution_duration_ms,
            overallStatus=overall_status or "COMPLETED",
            cvModelName=cv_model_name or "firesphere-perception-baseline-v0.8.0",
            cvModelVersion=cv_model_version or "0.8.0",
            cvStatus=cv_status or "NOT_APPROVED_GATED",
            cvApprovalStatus=effective_cv_approval,
            cvCalibrationStatus=cv_calibration_status or "NOT_CALIBRATED",
            vlmModelName=vlm_model_name or "gemini-2.5-flash",
            vlmModelVersion=vlm_model_version or "2.5-flash",
            vlmStatus=effective_vlm_status,
        )

        report_id = uuid.uuid4()
        now_utc = datetime.now(timezone.utc)
        audit_meta = ReportAuditMetadata(
            reportId=report_id,
            inspectionId=inspection_id,
            runId=run_id,
            generatedAt=now_utc,
            generatorVersion="0.5.0-phase5",
            modelName=model_name,
            modelVersion=model_version,
            calibrationStatus=calibration_status,
            isTestData=effective_is_test,
            aiDecisionAuthority="NONE (ADVISORY_DRAFT_ONLY)",
        )

        # 12. Assemble Complete Inspection Report Draft
        return InspectionReportDraft(
            reportId=report_id,
            inspectionId=inspection_id,
            runId=run_id,
            generatedAt=now_utc,
            reportVersion="0.5.0-phase5",
            status=ReportStatus.DRAFT,
            reviewState=ReviewState.DRAFT,
            generatedBy="FireSphere-AI-DeterministicReportGenerator-v0.5.0",
            dataClassification="TEST_DATA" if effective_is_test else "PRODUCTION_ADVISORY",
            isTestData=effective_is_test,
            testDataWarning="TEST DATA — NOT FOR OPERATIONAL OR COMPLIANCE USE." if effective_is_test else None,
            modelInformation=model_info,
            summary=exec_summary,
            evidenceReviewed=evidence_items,
            findings=report_findings,
            riskSummary=risk_summary,
            verificationActions=verification_actions,
            correctiveActions=corrective_actions,
            unresolvedItems=unresolved_items,
            aiLimitations=ai_limitations,
            traceability=trace.get_records(),
            auditMetadata=audit_meta,
            officerRemarks=formatted_remarks,
            checklistSummary=checklist_summary,
        )
