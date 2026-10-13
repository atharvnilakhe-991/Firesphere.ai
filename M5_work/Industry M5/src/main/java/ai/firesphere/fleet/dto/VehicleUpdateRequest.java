package ai.firesphere.fleet.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request payload for modifying mutable vehicle metadata.
 * Does NOT allow modifying registrationNumber, status, or tenantId.
 */
public record VehicleUpdateRequest(
        @Size(max = 50, message = "Call sign cannot exceed 50 characters")
        String callSign,

        UUID assignedStationId,

        @Size(max = 50, message = "Make cannot exceed 50 characters")
        String make,

        @Size(max = 50, message = "Model cannot exceed 50 characters")
        String model,

        Integer manufactureYear,
        Integer waterCapacityLiters,
        Integer foamCapacityLiters,
        Integer pumpCapacityLpm,

        @Size(max = 1000, message = "Operational notes cannot exceed 1000 characters")
        String operationalNotes
) {}
