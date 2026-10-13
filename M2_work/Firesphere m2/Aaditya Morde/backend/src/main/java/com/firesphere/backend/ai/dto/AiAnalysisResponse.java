package com.firesphere.backend.ai.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AiAnalysisResponse(
        UUID runId,
        UUID applicationId,
        UUID documentId,
        String status,
        OffsetDateTime requestedAt,
        OffsetDateTime completedAt,
        String modelName,
        String modelVersion,
        String failureReason,
        List<AiFindingDto> findings
) {
    public record AiFindingDto(String finding, Double confidence) {}
}
