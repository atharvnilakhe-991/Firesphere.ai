package com.firesphere.backend.inspection.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OfficerAssignmentResponse(
        UUID id,
        String reference,
        UUID applicationId,
        String applicationNumber,
        String title,
        String address,
        String propertyType,
        String inspectionType,
        String status,
        OffsetDateTime scheduledAt,
        String priority,
        String syncState
) {}
