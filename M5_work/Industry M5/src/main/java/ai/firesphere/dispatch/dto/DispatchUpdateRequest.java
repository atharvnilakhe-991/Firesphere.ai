package ai.firesphere.dispatch.dto;

import ai.firesphere.dispatch.model.DispatchPriority;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Request payload for modifying mutable dispatch order metadata.
 * Does NOT allow modifying status, incidentId, tenantId, or dispatchCode.
 */
public record DispatchUpdateRequest(
        DispatchPriority priority,
        UUID stationId,
        List<UUID> assignedVehicleIds,
        List<UUID> assignedResponderIds,
        @Size(max = 1000, message = "Operational notes cannot exceed 1000 characters")
        String operationalNotes
) {}
