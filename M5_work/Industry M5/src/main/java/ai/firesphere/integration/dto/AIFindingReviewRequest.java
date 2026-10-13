package ai.firesphere.integration.dto;

import jakarta.validation.constraints.NotBlank;

public record AIFindingReviewRequest(
        @NotBlank(message = "Review decision is required (APPROVED, DISMISSED, ESCALATED)")
        String decision,

        String reviewerNotes,

        @NotBlank(message = "Reviewer officer ID or badge is required")
        String reviewerBadge
) {}
