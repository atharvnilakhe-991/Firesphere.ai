package ai.firesphere.operations.officer.dto;

import ai.firesphere.operations.officer.model.OfficerTaskStatus;
import jakarta.validation.constraints.NotNull;

public record OfficerTaskStatusUpdateRequest(
        @NotNull(message = "status is mandatory")
        OfficerTaskStatus status,

        String notes
) {}
