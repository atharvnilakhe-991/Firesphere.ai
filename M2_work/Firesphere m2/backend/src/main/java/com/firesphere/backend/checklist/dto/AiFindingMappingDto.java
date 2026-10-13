package com.firesphere.backend.checklist.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AiFindingMappingDto(
        UUID linkId,
        UUID findingId,
        UUID inspectionId,
        UUID checklistInstanceId,
        UUID templateItemId,
        String itemCode,
        String itemTitle,
        String sourceProvenance,
        String originalCategory,
        String findingSeverity,
        Double confidence,
        UUID evidenceId,
        String mappingStatus,
        String suggestedCompliance,
        String linkStatus,
        String reviewStatus,
        String mappingReason,
        String officerComment,
        String modelName,
        String modelVersion,
        String conflictType,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
