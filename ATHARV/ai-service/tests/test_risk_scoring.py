"""Tests for RiskScoringEngine, factor transparency, and context-aware operational risk calculation."""

import pytest
from app.intelligence.risk import RiskScoringEngine
from app.schemas.finding import FindingSeverity


def test_risk_scoring_critical_hazard():
    """Verifies that high-confidence critical findings produce high/critical operational risk scores."""
    assessment = RiskScoringEngine.calculate_risk(
        severity=FindingSeverity.CRITICAL,
        confidence=0.92,
        evidence_quality="GOOD",
        occupancy_context="COMMERCIAL",
        historical_context="UNAVAILABLE",
    )

    assert 85 <= assessment.riskScore <= 100
    assert assessment.riskLevel == "CRITICAL"
    assert len(assessment.factors) >= 4

    # Verify factor names
    factor_names = {f.factor for f in assessment.factors}
    assert "finding_severity" in factor_names
    assert "model_confidence" in factor_names
    assert "evidence_quality" in factor_names
    assert "occupancy_context" in factor_names


def test_missing_occupancy_and_historical_context():
    """Verifies that missing context (UNKNOWN/UNAVAILABLE) is not fabricated and adds zero points."""
    assessment = RiskScoringEngine.calculate_risk(
        severity=FindingSeverity.MEDIUM,
        confidence=0.70,
        evidence_quality="ACCEPTABLE",
        occupancy_context="UNKNOWN",
        historical_context="UNAVAILABLE",
    )

    occ_factor = next(f for f in assessment.factors if f.factor == "occupancy_context")
    assert occ_factor.contribution == 0
    assert "UNKNOWN" in occ_factor.description

    # Ensure no fabricated legal calculation claims
    assert "AI_OPERATIONAL_RISK_SCORE" in assessment.disclaimer
    assert "NOT a legally binding fire-risk calculation" in assessment.disclaimer


def test_evidence_quality_impact_on_risk():
    """Verifies that photographic evidence quality proportionally influences risk contribution."""
    good_assessment = RiskScoringEngine.calculate_risk(
        severity=FindingSeverity.HIGH,
        confidence=0.80,
        evidence_quality="GOOD",
    )
    poor_assessment = RiskScoringEngine.calculate_risk(
        severity=FindingSeverity.HIGH,
        confidence=0.80,
        evidence_quality="POOR",
    )

    good_qual = next(f for f in good_assessment.factors if f.factor == "evidence_quality")
    poor_qual = next(f for f in poor_assessment.factors if f.factor == "evidence_quality")

    assert good_qual.contribution > poor_qual.contribution
    assert good_assessment.riskScore > poor_assessment.riskScore


def test_risk_score_bounds_clamping():
    """Verifies that risk scores remain strictly clamped in [0, 100]."""
    # Max parameters
    max_assessment = RiskScoringEngine.calculate_risk(
        severity=FindingSeverity.CRITICAL,
        confidence=1.0,
        evidence_quality="GOOD",
        occupancy_context="HAZARDOUS",
    )
    assert max_assessment.riskScore <= 100

    # Min parameters
    min_assessment = RiskScoringEngine.calculate_risk(
        severity=FindingSeverity.INFORMATIONAL,
        confidence=0.0,
        evidence_quality="UNUSABLE",
        occupancy_context="UNKNOWN",
    )
    assert min_assessment.riskScore >= 0
    assert min_assessment.riskLevel == "LOW"
