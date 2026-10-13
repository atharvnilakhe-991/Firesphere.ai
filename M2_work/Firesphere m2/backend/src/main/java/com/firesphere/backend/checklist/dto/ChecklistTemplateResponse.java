package com.firesphere.backend.checklist.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ChecklistTemplateResponse(
        UUID templateId,
        String checklistCode,
        String title,
        String occupancyType,
        String inspectionType,
        String nbcReferenceClause,
        String sourceAuthority,
        Integer version,
        Boolean isActive,
        String description,
        Integer itemCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<ChecklistTemplateItemResponse> items
) {}
