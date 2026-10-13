package com.firesphere.backend.checklist.dto;

import java.util.List;
import java.util.UUID;

public record AiChecklistMappingResult(
        UUID inspectionId,
        UUID checklistInstanceId,
        String templateCode,
        int totalFindingsCount,
        int mappedCount,
        int unmappedCount,
        int ambiguousCount,
        List<AiFindingMappingDto> mappings,
        List<UnmappedFindingDto> unmappedFindings
) {}
