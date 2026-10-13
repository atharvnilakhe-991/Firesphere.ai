package com.firesphere.backend.inspection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ScheduleInspectionRequest(
        @NotBlank String inspectionType,
        @NotNull OffsetDateTime scheduledAt,
        @NotNull UUID assignedOfficerId
) {
    public ScheduleInspectionRequest(String inspectionType, OffsetDateTime scheduledAt, Long assignedOfficerId) {
        this(inspectionType, scheduledAt, assignedOfficerId != null ? new UUID(0L, assignedOfficerId) : null);
    }
}
