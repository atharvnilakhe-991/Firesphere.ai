"""Evidence quality evaluation integrating Phase 2 OpenCV image quality metrics."""

from typing import Optional
from app.cv.quality import ImageQualityReport


class EvidenceQualityEvaluator:
    """Evaluates and standardizes photographic evidence quality for downstream AI intelligence."""

    @classmethod
    def evaluate(cls, quality_report: Optional[ImageQualityReport]) -> str:
        """Determines evidence quality rating: GOOD, ACCEPTABLE, POOR, or UNUSABLE.

        Rules:
        - UNUSABLE: Quality report is missing, acceptable flag is False, or fatal errors exist.
        - POOR: Warnings include severe blur (blurScore < 15) or extreme brightness (< 20 or > 245).
        - ACCEPTABLE: Image has minor advisory warnings (e.g. lighting variation, aspect ratio skew) but is usable.
        - GOOD: Image passed with no warnings, clear resolution, and optimal sharpness.
        """
        if quality_report is None or not quality_report.isAcceptable or quality_report.errors:
            return "UNUSABLE"

        if not quality_report.warnings:
            return "GOOD"

        # Check for severe degradation warnings
        if quality_report.blurScore < 15.0:
            return "POOR"
        if quality_report.brightnessScore < 20.0 or quality_report.brightnessScore > 245.0:
            return "POOR"

        # Minor warnings (acceptable for advisory inspection review)
        return "ACCEPTABLE"

    @classmethod
    def get_quality_factor_contribution(cls, quality_rating: str) -> int:
        """Computes risk factor contribution based on evidence quality (out of 15 max points)."""
        mapping = {
            "GOOD": 15,
            "ACCEPTABLE": 10,
            "POOR": 5,
            "UNUSABLE": 0,
        }
        return mapping.get(quality_rating, 10)
