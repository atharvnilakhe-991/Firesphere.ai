package ai.firesphere.dispatch.dto;

import ai.firesphere.dispatch.model.DispatchPriority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record DispatchCreateRequest(
        @NotNull(message = "Incident ID is required")
        UUID incidentId,

        @NotNull(message = "Station ID is required")
        UUID stationId,

        @NotNull(message = "Dispatch priority is required")
        DispatchPriority priority,

        List<UUID> assignedVehicleIds,

        List<UUID> assignedResponderIds,

        @Size(max = 1000, message = "Operational notes cannot exceed 1000 characters")
        String operationalNotes
) {}
