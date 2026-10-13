package com.firesphere.backend.checklist.dto;

import com.firesphere.backend.checklist.entity.ChecklistResponseStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InspectionChecklistItemDetailDto(
        UUID responseId,
        UUID templateItemId,
        Integer itemSequence,
        String itemCode,
        String itemTitle,
        String parameterDescription,
        String requirementType,
        String verificationMethod,
        Boolean evidenceRequired,
        String aiCategoryTag,
        String helpGuidance,
        ChecklistResponseStatus officerResponse,
        String officerRemarks,
        UUID officerId,
        OffsetDateTime respondedAt,
        Integer revisionNumber,
        List<LinkedEvidenceDto> linkedEvidence,
        List<AiSuggestionLinkDto> aiSuggestions
) {}
