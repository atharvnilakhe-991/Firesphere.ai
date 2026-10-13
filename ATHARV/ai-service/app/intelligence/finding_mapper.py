"""Finding mapper constructing fully structured, explainable, and reviewable AI findings."""

from datetime import datetime, timezone
from typing import Any, Dict, List, Optional
import uuid
from app.intelligence.confidence import ConfidenceInterpreter, default_calibrator
from app.intelligence.explainability import explainability_engine
from app.intelligence.risk import RiskScoringEngine
from app.intelligence.severity import SeverityEngine
from app.intelligence.verification import OfficerVerificationGuidance
from app.models.base_model import RawDetection
from app.schemas.finding import BoundingBox, ComplianceStatus, Finding


class FindingMapper:
    """Transforms raw CV detections into enriched, explainable, officer-reviewable AI findings."""

    @classmethod
    def map_detection(
        cls,
        detection: RawDetection,
        evidence_id: uuid.UUID,
        model_name: str,
        model_version: str,
        transformed_bounding_box: Optional[BoundingBox] = None,
        evidence_quality: str = "ACCEPTABLE",
        occupancy_context: str = "UNKNOWN",
        historical_context: str = "UNAVAILABLE",
        is_test_data: bool = False,
        tenant_id: Optional[str] = None,
        inspection_id: Optional[uuid.UUID] = None,
        analysis_run_id: Optional[uuid.UUID] = None,
    ) -> Finding:
        """Constructs an enriched Finding object from model output and contextual metadata."""
        from app.models.model_approval_gate import model_approval_gate

        # 1. Confidence validation & categorical interpretation
        raw_conf = ConfidenceInterpreter.validate_bounds(detection.confidence)
        conf_level = ConfidenceInterpreter.interpret(raw_conf)
        calibrated_conf, cal_status = default_calibrator.calibrate(raw_conf)

        # 2. Operational severity evaluation & reasoning
        severity = SeverityEngine.evaluate_severity(detection.className, raw_conf)
        severity_reason = SeverityEngine.generate_severity_reason(detection.className, severity)

        # 3. Context-aware operational risk assessment
        risk_assessment = RiskScoringEngine.calculate_risk(
            severity=severity,
            confidence=raw_conf,
            evidence_quality=evidence_quality,
            occupancy_context=occupancy_context,
            historical_context=historical_context,
        )

        # 4. Evidence-grounded explainability (WHAT, WHERE, CONFIDENCE, WHY, VERIFY)
        explanation = explainability_engine.generate_explanation(
            finding_type=detection.className,
            confidence=raw_conf,
            confidence_level=conf_level,
            bounding_box=transformed_bounding_box,
            evidence_quality=evidence_quality,
        )

        # 5. On-site officer verification guidance checklist
        verification_checklist = OfficerVerificationGuidance.get_guidance(detection.className)

        # 6. Retrieve known model limitations
        known_limitations = model_approval_gate.get_known_limitations_for_class(detection.className)

        # 7. Create enriched Finding instance with Phase 10 identity linkage
        return Finding(
            findingId=uuid.uuid4(),
            evidenceId=evidence_id,
            findingType=detection.className,
            confidence=raw_conf,
            confidenceLevel=conf_level,
            calibrationStatus=cal_status,
            severity=severity,
            severityReason=severity_reason,
            operationalRiskScore=risk_assessment.riskScore,
            operationalRiskLevel=risk_assessment.riskLevel,
            riskFactors=[f.model_dump() for f in risk_assessment.factors],
            explanation=explanation,
            verificationGuidance=verification_checklist,
            evidenceQuality=evidence_quality,
            complianceStatus=ComplianceStatus.REQUIRES_OFFICER_VERIFICATION,
            occupancyContext=occupancy_context or "UNKNOWN",
            historicalContext=historical_context or "UNAVAILABLE",
            model=model_name,
            modelVersion=model_version,
            boundingBox=transformed_bounding_box,
            isTestData=is_test_data,
            tenantId=tenant_id,
            inspectionId=inspection_id,
            analysisRunId=analysis_run_id,
            perceptionClass=detection.className,
            knownLimitations=known_limitations,
            officerVerificationStatus="UNREVIEWED",
            modelApprovalStatus="NOT_APPROVED",
            createdAt=datetime.now(timezone.utc),
        )
