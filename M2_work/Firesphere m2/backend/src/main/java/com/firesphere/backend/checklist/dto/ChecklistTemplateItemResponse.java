package com.firesphere.backend.checklist.dto;

import java.util.UUID;

public record ChecklistTemplateItemResponse(
        UUID itemId,
        Integer itemSequence,
        String itemCode,
        String itemTitle,
        String parameterDescription,
        String requirementType,
        String verificationMethod,
        Boolean evidenceRequired,
        String aiCategoryTag,
        Integer penaltyPoints,
        String helpGuidance
) {}
