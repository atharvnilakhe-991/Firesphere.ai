"""Tailored AI limitation statement provider for inspection reports."""

from typing import List


class AILimitationsProvider:
    """Assembles relevant, factual operational limitations for an AI inspection report draft."""

    @classmethod
    def get_limitations(
        cls,
        has_findings: bool,
        has_low_confidence: bool = False,
        has_poor_quality: bool = False,
        is_uncalibrated: bool = True,
        is_test_data: bool = False,
    ) -> List[str]:
        """Returns tailored limitation statements based on the analysis context."""
        limitations: List[str] = [
            (
                "ADVISORY DRAFT STATUS: This report is an automated AI-assisted draft prepared to assist human Fire Officers. "
                "All visual observations and findings require mandatory on-site physical verification before any regulatory action."
            ),
            (
                "ZERO REGULATORY AUTHORITY: The AI service does not possess statutory authority to approve, reject, or certify "
                "Fire NOC applications or issue statutory compliance violation notices."
            ),
            (
                "CONFIDENCE VS LEGAL CERTAINTY: Detection confidence scores reflect statistical feature activation patterns within "
                "the computer vision neural network and must not be interpreted as legal certainty of a fire code violation."
            ),
            (
                "OPERATIONAL RISK METRIC: The AI Operational Risk Score is an internal workflow prioritization metric and is not a "
                "statutory, actuarial, or legally binding fire risk rating."
            ),
        ]

        if is_uncalibrated:
            limitations.append(
                "UNCALIBRATED INFERENCE: The underlying computer vision adapter operates with uncalibrated confidence (calibrationStatus='NOT_CALIBRATED') "
                "pending an annotated validation dataset."
            )

        if has_poor_quality:
            limitations.append(
                "IMAGE QUALITY SENSITIVITY: Suboptimal focus (blur), extreme lighting, or skewed camera angles may produce false positives "
                "or obscure valid hazards. Physical visual confirmation is strongly advised."
            )

        if has_low_confidence:
            limitations.append(
                "MARGINAL CONFIDENCE FINDINGS: One or more detections exhibit marginal confidence and must be treated with heightened "
                "caution during manual officer review."
            )

        if not has_findings:
            limitations.append(
                "ABSENCE OF DETECTIONS: The absence of AI findings indicates only that no supported defects were detected in the supplied "
                "photographs; it does not constitute certification that the building is compliant or free of fire hazards."
            )

        if is_test_data:
            limitations.append(
                "SYNTHETIC TEST DATA WARNING: This report was generated from synthetic test fixtures for integration verification. "
                "It is strictly invalid for operational, administrative, or statutory compliance use."
            )

        return limitations
