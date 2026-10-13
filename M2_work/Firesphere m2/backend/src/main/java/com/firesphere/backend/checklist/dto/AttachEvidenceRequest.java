package com.firesphere.backend.checklist.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AttachEvidenceRequest(
        @NotNull(message = "Evidence ID is mandatory")
        UUID evidenceId,
        String notes
) {}
