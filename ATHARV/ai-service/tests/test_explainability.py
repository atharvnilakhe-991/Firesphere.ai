"""Tests for ExplainabilityEngine, evidence-grounded narratives, and future SHAP/LIME interfaces."""

import pytest
from app.intelligence.explainability import (
    ExplainabilityEngine,
    RiskModelExplainer,
    VisionExplanationProvider,
    explainability_engine,
)
from app.schemas.finding import BoundingBox, FindingType


def test_vision_explainer_answers_all_core_questions():
    """Verifies that the vision explainer answers WHAT, WHERE, CONFIDENCE, WHY, and VERIFY."""
    explainer = VisionExplanationProvider()
    box = BoundingBox(xMin=0.1, yMin=0.2, xMax=0.4, yMax=0.5)

    payload = explainer.explain(
        finding_type=FindingType.EMERGENCY_EXIT_OBSTRUCTED,
        confidence=0.88,
        confidence_level="HIGH",
        bounding_box=box,
        evidence_quality="GOOD",
    )

    assert "what" in payload
    assert "where" in payload
    assert "confidenceScore" in payload
    assert "confidenceLevel" in payload
    assert "why" in payload
    assert "verify" in payload

    narrative = payload["narrative"]
    assert "WHAT:" in narrative
    assert "WHERE:" in narrative
    assert "CONFIDENCE:" in narrative
    assert "WHY:" in narrative
    assert "VERIFY:" in narrative
    assert "0.88" in narrative
    assert "HIGH" in narrative
    assert "Fire Safety Officer" in narrative


def test_vision_explainer_spatial_quadrant_description():
    """Verifies that bounding boxes in different quadrants are correctly described."""
    explainer = VisionExplanationProvider()

    # Upper-left box
    ul_box = BoundingBox(xMin=0.05, yMin=0.05, xMax=0.25, yMax=0.25)
    ul_desc = explainer._describe_spatial_region(ul_box)
    assert "upper-left" in ul_desc

    # Lower-right box
    lr_box = BoundingBox(xMin=0.75, yMin=0.75, xMax=0.95, yMax=0.95)
    lr_desc = explainer._describe_spatial_region(lr_box)
    assert "lower-right" in lr_desc

    # None box
    none_desc = explainer._describe_spatial_region(None)
    assert "unspecified" in none_desc


def test_no_unsupported_regulatory_claims_in_explanation():
    """Verifies that explanations remain factual observations and do not claim legal violations or NOC rejection."""
    narrative = explainability_engine.generate_explanation(
        finding_type=FindingType.FIRE_EXTINGUISHER_MISSING,
        confidence=0.85,
        confidence_level="HIGH",
        bounding_box=BoundingBox(xMin=0.2, yMin=0.3, xMax=0.4, yMax=0.6),
    )

    # Must NOT claim legal violation or rejection
    assert "This building violates fire code" not in narrative
    assert "reject NOC" not in narrative.lower()
    assert "NOC rejected" not in narrative.lower()
    # Must explicitly state officer verification is required
    assert "Officer" in narrative


def test_future_tabular_risk_explainer_status():
    """Verifies that future tabular SHAP/LIME explainer reports NOT_AVAILABLE without fabricating fake values."""
    risk_explainer = RiskModelExplainer()
    payload = risk_explainer.explain()

    assert payload["explainerStatus"] == "NOT_AVAILABLE"
    assert "SHAP/LIME feature attribution is not available" in payload["message"]
    assert payload["attributions"] == {}
