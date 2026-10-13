package com.firesphere.backend.checklist.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record RecordOfficerResponseRequest(
        @NotBlank(message = "Response status is mandatory")
        String responseStatus,
        String officerRemarks,
        List<UUID> linkedEvidenceIds
) {}
