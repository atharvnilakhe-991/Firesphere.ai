package com.firesphere.backend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReviewFindingRequest(
        @NotBlank(message = "Review status is required")
        @Pattern(regexp = "CONFIRMED|DISMISSED|MODIFIED", message = "Status must be CONFIRMED, DISMISSED, or MODIFIED")
        String reviewStatus,
        String officerNotes,
        Boolean hazardVerified
) {}
