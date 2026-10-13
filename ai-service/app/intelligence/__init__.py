"""FireSphere AI Intelligence Layer: Confidence, Severity, Risk, Explainability, and Finding Mapping."""

from app.intelligence.aggregation import InspectionSummaryAggregator
from app.intelligence.confidence import (
    BaseConfidenceCalibrator,
    ConfidenceInterpreter,
    IsotonicRegressionCalibrator,
    TemperatureScalingCalibrator,
    UncalibratedPassThroughCalibrator,
    default_calibrator,
)
from app.intelligence.evidence_quality import EvidenceQualityEvaluator
from app.intelligence.explainability import (
    BaseExplainer,
    ExplainabilityEngine,
    RiskModelExplainer,
    VisionExplanationProvider,
    explainability_engine,
)
from app.intelligence.finding_mapper import FindingMapper
from app.intelligence.risk import RiskAssessment, RiskFactor, RiskScoringEngine
from app.intelligence.severity import SEVERITY_PROFILES, SeverityEngine, SeverityProfile
from app.intelligence.verification import OfficerVerificationGuidance

__all__ = [
    "BaseConfidenceCalibrator",
    "BaseExplainer",
    "ConfidenceInterpreter",
    "EvidenceQualityEvaluator",
    "ExplainabilityEngine",
    "FindingMapper",
    "InspectionSummaryAggregator",
    "IsotonicRegressionCalibrator",
    "OfficerVerificationGuidance",
    "RiskAssessment",
    "RiskFactor",
    "RiskModelExplainer",
    "RiskScoringEngine",
    "SeverityEngine",
    "SeverityProfile",
    "TemperatureScalingCalibrator",
    "UncalibratedPassThroughCalibrator",
    "VisionExplanationProvider",
    "default_calibrator",
    "explainability_engine",
    "SEVERITY_PROFILES",
]
