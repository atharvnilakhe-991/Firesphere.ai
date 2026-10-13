package ai.firesphere.fleet.dto;

import ai.firesphere.fleet.model.Vehicle;
import ai.firesphere.fleet.model.VehicleStatus;
import ai.firesphere.fleet.model.VehicleType;

import java.time.Instant;
import java.util.UUID;

public record VehicleResponseDTO(
        UUID id,
        String registrationNumber,
        String callSign,
        VehicleType vehicleType,
        VehicleStatus status,
        UUID assignedStationId,
        String make,
        String model,
        Integer manufactureYear,
        Integer waterCapacityLiters,
        Integer foamCapacityLiters,
        Integer pumpCapacityLpm,
        Double currentLatitude,
        Double currentLongitude,
        Double fuelPercent,
        Double waterLevelPercent,
        String operationalNotes,
        boolean available,
        Instant createdAt,
        Instant updatedAt
) {
    public static VehicleResponseDTO fromEntity(Vehicle entity) {
        return new VehicleResponseDTO(
                entity.getId(),
                entity.getRegistrationNumber(),
                entity.getCallSign(),
                entity.getVehicleType(),
                entity.getStatus(),
                entity.getAssignedStationId(),
                entity.getMake(),
                entity.getModel(),
                entity.getManufactureYear(),
                entity.getWaterCapacityLiters(),
                entity.getFoamCapacityLiters(),
                entity.getPumpCapacityLpm(),
                entity.getCurrentLatitude(),
                entity.getCurrentLongitude(),
                entity.getFuelPercent(),
                entity.getWaterLevelPercent(),
                entity.getOperationalNotes(),
                entity.isAvailable(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
