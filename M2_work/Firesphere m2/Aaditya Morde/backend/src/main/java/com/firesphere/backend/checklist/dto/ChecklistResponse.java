package com.firesphere.backend.checklist.dto;

import com.firesphere.backend.checklist.entity.ChecklistStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ChecklistResponse(
        UUID id,
        UUID applicationId,
        String checklistType,
        ChecklistStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<ChecklistItemResponse> items
) {}
