"""Human-in-the-loop review lifecycle manager and officer remarks handling."""

from typing import Optional
from app.schemas.report import ReviewState


class ReviewLifecycleManager:
    """Manages human officer review states and ensures strict compliance boundaries."""

    ALLOWED_STATES = {
        ReviewState.DRAFT,
        ReviewState.UNDER_REVIEW,
        ReviewState.OFFICER_EDITED,
        ReviewState.OFFICER_ACCEPTED,
        ReviewState.SUPERSEDED,
    }

    FORBIDDEN_KEYWORDS = {
        "AI_APPROVED",
        "AI_REJECTED",
        "AI_NOC_APPROVED",
        "AI_NOC_REJECTED",
        "APPROVED",
        "REJECTED",
    }

    @classmethod
    def validate_state(cls, state: str) -> ReviewState:
        """Validates that report state is an approved human-review state."""
        normalized = (state or "").strip().upper()
        if normalized in cls.FORBIDDEN_KEYWORDS:
            raise ValueError(
                f"Review state '{state}' is strictly forbidden. The AI service possesses no authority "
                "to approve or reject Fire NOC compliance. Approved states are: DRAFT, UNDER_REVIEW, "
                "OFFICER_EDITED, OFFICER_ACCEPTED, or SUPERSEDED."
            )

        try:
            return ReviewState(normalized)
        except ValueError:
            raise ValueError(f"Invalid review state '{state}'. Expected one of {list(cls.ALLOWED_STATES)}")

    @classmethod
    def format_officer_remarks(cls, remarks: Optional[str]) -> Optional[str]:
        """Sanitizes and formats officer remarks, clearly tagging them as human-authored content."""
        if not remarks or not remarks.strip():
            return None

        cleaned = remarks.strip()
        return f"[OFFICER REMARKS — HUMAN AUTHORED]: {cleaned}"
