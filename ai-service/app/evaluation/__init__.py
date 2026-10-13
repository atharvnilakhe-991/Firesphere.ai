"""Evaluation and explainability package."""

from app.evaluation.metrics import (
    SEVERITY_MAPPING,
    EXPLANATION_TEMPLATES,
    calculate_severity,
    generate_explanation,
)

__all__ = [
    "SEVERITY_MAPPING",
    "EXPLANATION_TEMPLATES",
    "calculate_severity",
    "generate_explanation",
]
