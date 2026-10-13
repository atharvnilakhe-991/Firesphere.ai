"""Context-aware AI operational risk scoring engine with transparent factor contributions."""

from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field
from app.schemas.finding import FindingSeverity


class RiskFactor(BaseModel):
    """Contributing factor to the AI operational risk prioritization score."""

    factor: str = Field(description="Name of the contributing factor")
    contribution: int = Field(description="Score points contributed by this factor")
    description: str = Field(description="Operational rationale for factor contribution")


class RiskAssessment(BaseModel):
    """Comprehensive AI operational risk assessment result."""

    riskScore: int = Field(ge=0, le=100, description="Composite operational risk score [0 - 100]")
    riskLevel: str = Field(description="Operational risk level: LOW, MEDIUM, HIGH, CRITICAL")
    factors: List[RiskFactor] = Field(default_factory=list, description="Transparent contributing factors")
    disclaimer: str = Field(
        default=(
            "AI_OPERATIONAL_RISK_SCORE is an automated operational workflow prioritization metric. "
            "It is NOT a legally binding fire-risk calculation or statutory safety rating."
        ),
        description="Regulatory disclaimer",
    )


class RiskScoringEngine:
    """Calculates context-aware operational risk scores based on available factual evidence."""

    SEVERITY_WEIGHTS: Dict[FindingSeverity, int] = {
        FindingSeverity.CRITICAL: 50,
        FindingSeverity.HIGH: 40,
        FindingSeverity.MEDIUM: 25,
        FindingSeverity.LOW: 15,
        FindingSeverity.INFORMATIONAL: 5,
    }

    OCCUPANCY_WEIGHTS: Dict[str, int] = {
        "HAZARDOUS": 10,
        "INDUSTRIAL": 8,
        "ASSEMBLY": 8,
        "COMMERCIAL": 5,
        "EDUCATIONAL": 5,
        "INSTITUTIONAL": 5,
        "RESIDENTIAL": 2,
        "UNKNOWN": 0,
    }

    @classmethod
    def calculate_risk(
        cls,
        severity: FindingSeverity,
        confidence: float,
        evidence_quality: str = "ACCEPTABLE",
        occupancy_context: str = "UNKNOWN",
        historical_context: str = "UNAVAILABLE",
    ) -> RiskAssessment:
        """Computes operational risk score in [0, 100] and risk level.

        Scoring Model (Total Max 100):
        1. Finding Severity Contribution: Max 50 points
        2. Model Detection Confidence: Max 25 points (confidence * 25)
        3. Visual Evidence Quality: Max 15 points (GOOD=15, ACCEPTABLE=10, POOR=5, UNUSABLE=0)
        4. Premises Occupancy Context: Max 10 points (if verified; 0 if UNKNOWN)
        """
        factors: List[RiskFactor] = []

        # 1. Severity Contribution
        sev_points = cls.SEVERITY_WEIGHTS.get(severity, 25)
        factors.append(
            RiskFactor(
                factor="finding_severity",
                contribution=sev_points,
                description=f"Base operational weight for {severity.value} severity.",
            )
        )

        # 2. Confidence Contribution
        conf_points = int(round(max(0.0, min(1.0, confidence)) * 25))
        factors.append(
            RiskFactor(
                factor="model_confidence",
                contribution=conf_points,
                description=f"Detection activation confidence contribution ({confidence * 100:.1f}%).",
            )
        )

        # 3. Evidence Quality Contribution
        from app.intelligence.evidence_quality import EvidenceQualityEvaluator
        qual_points = EvidenceQualityEvaluator.get_quality_factor_contribution(evidence_quality)
        factors.append(
            RiskFactor(
                factor="evidence_quality",
                contribution=qual_points,
                description=f"Photographic quality rating '{evidence_quality}'.",
            )
        )

        # 4. Contextual Factors (Occupancy & History)
        occ_key = (occupancy_context or "UNKNOWN").strip().upper()
        occ_points = cls.OCCUPANCY_WEIGHTS.get(occ_key, 0)
        factors.append(
            RiskFactor(
                factor="occupancy_context",
                contribution=occ_points,
                description=(
                    f"Occupancy context '{occ_key}'."
                    if occ_key != "UNKNOWN"
                    else "Occupancy context is UNKNOWN (no contribution added)."
                ),
            )
        )

        # Note: Historical context is documented but does not modify the score if UNAVAILABLE
        hist_key = (historical_context or "UNAVAILABLE").strip().upper()
        if hist_key != "UNAVAILABLE":
            factors.append(
                RiskFactor(
                    factor="historical_context",
                    contribution=0,
                    description=f"Historical inspection record noted as '{hist_key}'.",
                )
            )

        # Aggregate total score
        raw_score = sev_points + conf_points + qual_points + occ_points
        total_score = max(0, min(100, raw_score))

        # Categorize risk level
        if total_score >= 85:
            risk_level = "CRITICAL"
        elif total_score >= 65:
            risk_level = "HIGH"
        elif total_score >= 40:
            risk_level = "MEDIUM"
        else:
            risk_level = "LOW"

        return RiskAssessment(
            riskScore=total_score,
            riskLevel=risk_level,
            factors=factors,
        )
