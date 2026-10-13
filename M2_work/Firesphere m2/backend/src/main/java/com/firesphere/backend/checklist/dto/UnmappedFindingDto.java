package com.firesphere.backend.checklist.dto;

import java.util.UUID;

public record UnmappedFindingDto(
        UUID findingId,
        UUID inspectionId,
        UUID evidenceId,
        String originalCategory,
        String findingSeverity,
        Double confidence,
        String status, // UNMAPPED or AMBIGUOUS
        String reason,
        String sourceProvenance,
        String modelName,
        String modelVersion
) {}
