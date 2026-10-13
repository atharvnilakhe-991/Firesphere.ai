package com.firesphere.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiAnalysisRequest(
        UUID documentId,
        String analysisType,
        UUID inspectionId,
        List<UUID> evidenceIds,
        Boolean testMode
) {
    public AiAnalysisRequest(UUID documentId, String analysisType) {
        this(documentId, analysisType, null, null, false);
    }

    public AiAnalysisRequest(UUID documentId, String analysisType, UUID inspectionId, List<UUID> evidenceIds) {
        this(documentId, analysisType, inspectionId, evidenceIds, false);
    }
}
