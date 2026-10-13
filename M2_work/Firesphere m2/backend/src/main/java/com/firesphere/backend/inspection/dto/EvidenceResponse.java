package com.firesphere.backend.inspection.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EvidenceResponse(
        UUID id,
        UUID inspectionId,
        String fileName,
        String contentType,
        Long fileSizeBytes,
        String storageKey,
        String sha256Hash,
        String status,
        OffsetDateTime captureTimestamp,
        Double gpsLatitude,
        Double gpsLongitude,
        OffsetDateTime createdAt
) {}
