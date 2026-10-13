package com.firesphere.backend.checklist.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LinkedEvidenceDto(
        UUID evidenceId,
        String fileName,
        String contentType,
        String storageKey,
        String notes,
        OffsetDateTime attachedAt
) {}
