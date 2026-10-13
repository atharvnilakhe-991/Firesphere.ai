package com.firesphere.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalAiRequest(
        UUID inspectionId,
        List<UUID> evidenceIds,
        Boolean testMode,
        String occupancyContext,
        String historicalContext,
        String tenantId
) {
    public ExternalAiRequest {
        if (inspectionId == null) {
            throw new IllegalArgumentException("inspectionId must not be null");
        }
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            throw new IllegalArgumentException("evidenceIds must not be null or empty");
        }
        if (testMode == null) {
            testMode = false;
        }
        if (occupancyContext == null) {
            occupancyContext = "UNKNOWN";
        }
        if (historicalContext == null) {
            historicalContext = "UNAVAILABLE";
        }
        if (tenantId == null) {
            tenantId = "default";
        }
    }

    public ExternalAiRequest(UUID inspectionId, List<UUID> evidenceIds) {
        this(inspectionId, evidenceIds, false, "UNKNOWN", "UNAVAILABLE", "default");
    }
}
