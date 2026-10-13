"""Inspection-level finding aggregation, deduplication, and summary reporting."""

from collections import Counter
from typing import Any, Dict, List, Optional
import uuid
from app.schemas.finding import Finding, FindingSeverity
from app.schemas.inspection import InspectionAISummary


class InspectionSummaryAggregator:
    """Aggregates findings and evidence quality metrics into an executive AI summary for reviewing officers."""

    SEVERITY_RANK: Dict[str, int] = {
        FindingSeverity.CRITICAL.value: 5,
        FindingSeverity.HIGH.value: 4,
        FindingSeverity.MEDIUM.value: 3,
        FindingSeverity.LOW.value: 2,
        FindingSeverity.INFORMATIONAL.value: 1,
    }

    RISK_RANK: Dict[str, int] = {
        "CRITICAL": 4,
        "HIGH": 3,
        "MEDIUM": 2,
        "LOW": 1,
    }

    @classmethod
    def deduplicate_findings(cls, findings: List[Finding]) -> List[Finding]:
        """Deduplicates findings that identify identical defects on the same evidence photograph."""
        unique_findings: List[Finding] = []
        seen_keys = set()

        for f in findings:
            # Create a spatial fingerprint if bounding box exists
            if f.boundingBox:
                box_key = (
                    round(f.boundingBox.xMin, 2),
                    round(f.boundingBox.yMin, 2),
                    round(f.boundingBox.xMax, 2),
                    round(f.boundingBox.yMax, 2),
                )
            else:
                box_key = None

            dedup_key = (str(f.evidenceId), f.findingType, box_key)
            if dedup_key in seen_keys:
                continue
            seen_keys.add(dedup_key)
            unique_findings.append(f)

        return unique_findings

    @classmethod
    def aggregate(
        cls,
        findings: List[Finding],
        evidence_ids: List[uuid.UUID],
        evidence_quality_records: Dict[str, str],
        has_quality_warnings: bool = False,
    ) -> InspectionAISummary:
        """Constructs an InspectionAISummary from all findings and evidence quality evaluations."""
        deduped = cls.deduplicate_findings(findings)
        total_findings = len(deduped)
        unique_types = len({f.findingType for f in deduped})

        # Determine highest severity
        highest_sev_val: Optional[str] = None
        max_sev_rank = 0
        for f in deduped:
            sev_str = f.severity.value if hasattr(f.severity, "value") else str(f.severity)
            rank = cls.SEVERITY_RANK.get(sev_str, 0)
            if rank > max_sev_rank:
                max_sev_rank = rank
                highest_sev_val = sev_str

        # Determine highest risk score and risk level
        highest_risk_score = max([f.operationalRiskScore for f in deduped if f.operationalRiskScore is not None], default=None)
        highest_risk_level: Optional[str] = None
        max_risk_rank = 0
        for f in deduped:
            if f.operationalRiskLevel:
                rank = cls.RISK_RANK.get(f.operationalRiskLevel, 0)
                if rank > max_risk_rank:
                    max_risk_rank = rank
                    highest_risk_level = f.operationalRiskLevel

        # Confidence distribution
        conf_levels = [f.confidenceLevel for f in deduped if f.confidenceLevel]
        conf_counter = dict(Counter(conf_levels))
        avg_conf = (
            round(sum(f.confidence for f in deduped) / float(total_findings), 3)
            if total_findings > 0
            else None
        )
        confidence_summary: Dict[str, Any] = {
            "distribution": conf_counter,
            "averageConfidence": avg_conf,
        }

        # Evidence quality distribution
        quality_counter = dict(Counter(evidence_quality_records.values()))

        # Officer review is required if any findings were flagged or if evidence had warnings
        requires_review = (total_findings > 0) or has_quality_warnings

        return InspectionAISummary(
            totalFindings=total_findings,
            uniqueFindingTypes=unique_types,
            highestSeverity=highest_sev_val,
            highestRiskLevel=highest_risk_level,
            highestRiskScore=highest_risk_score,
            evidenceCount=len(evidence_ids),
            requiresOfficerReview=requires_review,
            findingsRequiringOfficerReview=total_findings,
            confidenceSummary=confidence_summary,
            evidenceQualitySummary=quality_counter,
            calibrationStatus="NOT_CALIBRATED",
        )
