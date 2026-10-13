package com.firesphere.backend.document.dto;

import com.firesphere.backend.document.entity.DocumentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID applicationId,
        String documentType,
        String originalFileName,
        String storageReference,
        String contentType,
        Long fileSize,
        DocumentStatus status,
        UUID uploadedBy,
        OffsetDateTime uploadedAt,
        UUID verifiedBy,
        OffsetDateTime verifiedAt,
        String rejectionReason
) {}
