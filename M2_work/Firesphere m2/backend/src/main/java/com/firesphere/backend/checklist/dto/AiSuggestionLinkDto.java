package com.firesphere.backend.checklist.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AiSuggestionLinkDto(
        UUID linkId,
        UUID findingId,
        String findingType,
        String severity,
        Double confidence,
        String suggestedCompliance,
        String linkProvenance,
        String sourceProvenance,
        String mappingReason,
        String modelName,
        String modelVersion,
        String linkStatus,
        String reviewStatus,
        String officerComment,
        String description,
        String boundingBoxJson,
        OffsetDateTime createdAt
) {
    // Backward-compatible constructor for 13-parameter calls
    public AiSuggestionLinkDto(
            UUID linkId,
            UUID findingId,
            String findingType,
            String severity,
            Double confidence,
            String suggestedCompliance,
            String linkProvenance,
            String linkStatus,
            String reviewStatus,
            String officerComment,
            String description,
            String boundingBoxJson,
            OffsetDateTime createdAt
    ) {
        this(
                linkId,
                findingId,
                findingType,
                severity,
                confidence,
                suggestedCompliance,
                linkProvenance,
                "AUTOMATIC".equalsIgnoreCase(linkProvenance) ? "CROSS_VALIDATED" : linkProvenance,
                null,
                null,
                null,
                linkStatus,
                reviewStatus,
                officerComment,
                description,
                boundingBoxJson,
                createdAt
        );
    }
}
