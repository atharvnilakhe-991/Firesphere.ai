package com.firesphere.backend.ai.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ExternalAiResponse(
        String status, // e.g., "SUCCESS", "FAILED"
        String modelName,
        String modelVersion,
        OffsetDateTime timestamp,
        List<Finding> findings,
        String errorDetail
) {
    public record Finding(String label, Double confidence) {}
}
