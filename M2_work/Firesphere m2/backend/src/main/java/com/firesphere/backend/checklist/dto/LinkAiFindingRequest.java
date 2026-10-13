package com.firesphere.backend.checklist.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LinkAiFindingRequest(
        @NotNull(message = "Finding ID is required")
        UUID findingId,
        String suggestedCompliance,
        Double confidence,
        String linkProvenance,
        String sourceProvenance,
        String mappingReason
) {
    public LinkAiFindingRequest(UUID findingId, String suggestedCompliance, Double confidence, String linkProvenance) {
        this(findingId, suggestedCompliance, confidence, linkProvenance, "CROSS_VALIDATED", null);
    }
}
