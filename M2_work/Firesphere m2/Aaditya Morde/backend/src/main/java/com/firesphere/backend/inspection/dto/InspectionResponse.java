package com.firesphere.backend.inspection.dto;

import com.firesphere.backend.inspection.entity.InspectionStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InspectionResponse(
        UUID id,
        UUID applicationId,
        String inspectionType,
        OffsetDateTime scheduledAt,
        UUID assignedOfficer,
        InspectionStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String remarks,
        String location
) {}
