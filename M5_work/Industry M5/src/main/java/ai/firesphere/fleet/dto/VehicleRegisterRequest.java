package ai.firesphere.fleet.dto;

import ai.firesphere.fleet.model.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record VehicleRegisterRequest(
        @NotBlank(message = "Registration number is required")
        @Size(max = 32, message = "Registration number cannot exceed 32 characters")
        String registrationNumber,

        @NotBlank(message = "Call sign is required")
        @Size(max = 50, message = "Call sign cannot exceed 50 characters")
        String callSign,

        @NotNull(message = "Vehicle type is required")
        VehicleType vehicleType,

        @NotNull(message = "Assigned station ID is required")
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
