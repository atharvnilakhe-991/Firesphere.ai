package com.firesphere.backend.checklist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReviewAiLinkRequest(
        @NotBlank(message = "Review status is required")
        @Pattern(regexp = "CONFIRMED|DISMISSED", message = "Status must be CONFIRMED or DISMISSED")
        String reviewStatus,
        String officerComment
) {}
