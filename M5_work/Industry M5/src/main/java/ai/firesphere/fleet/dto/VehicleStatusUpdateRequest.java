package ai.firesphere.fleet.dto;

import ai.firesphere.fleet.model.VehicleStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VehicleStatusUpdateRequest(
        @NotNull(message = "Vehicle status is required")
        VehicleStatus status,

        @Size(max = 500, message = "Status reason cannot exceed 500 characters")
        String reason
) {}
