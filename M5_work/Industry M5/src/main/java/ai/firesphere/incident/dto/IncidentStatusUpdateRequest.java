package ai.firesphere.incident.dto;

import ai.firesphere.incident.model.IncidentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IncidentStatusUpdateRequest(
        @NotNull(message = "New status is required")
        IncidentStatus status,

        @Size(max = 500, message = "Status reason cannot exceed 500 characters")
        String reason
) {}
