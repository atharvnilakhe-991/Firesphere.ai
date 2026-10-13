package com.firesphere.backend.checklist.dto;

import com.firesphere.backend.checklist.entity.ChecklistLifecycleStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InspectionChecklistResponseDto(
        UUID checklistId,
        UUID inspectionId,
        UUID applicationId,
        UUID templateId,
        String templateCode,
        String templateTitle,
        Integer templateVersion,
        ChecklistLifecycleStatus status,
        Integer totalItems,
        Integer reviewedItems,
        Integer compliantCount,
        Integer nonCompliantCount,
        Integer notApplicableCount,
        Integer needsFurtherInspectionCount,
        Integer pendingCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt,
        UUID completedByOfficer,
        String summaryNotes,
        List<InspectionChecklistItemDetailDto> items
) {}
