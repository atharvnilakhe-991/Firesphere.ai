package ai.firesphere.fleet.dto;

import ai.firesphere.fleet.model.Vehicle;
import ai.firesphere.fleet.model.VehicleStatus;
import ai.firesphere.fleet.model.VehicleType;

import java.util.UUID;

public record VehicleReadinessDTO(
        UUID vehicleId,
        String registrationNumber,
        String callSign,
        VehicleType vehicleType,
        VehicleStatus status,
        boolean available,
        String readinessState,
        UUID assignedStationId
) {
    public static VehicleReadinessDTO fromEntity(Vehicle entity) {
        String readinessState;
        switch (entity.getStatus()) {
            case AVAILABLE -> readinessState = "OPERATIONAL_READY";
            case ASSIGNED, DISPATCHED, EN_ROUTE, ON_SCENE -> readinessState = "DEPLOYED";
            case MAINTENANCE -> readinessState = "UNDER_MAINTENANCE";
            case OUT_OF_SERVICE -> readinessState = "OUT_OF_SERVICE";
            default -> readinessState = entity.getStatus().name();
        }

        return new VehicleReadinessDTO(
                entity.getId(),
                entity.getRegistrationNumber(),
                entity.getCallSign(),
                entity.getVehicleType(),
                entity.getStatus(),
                entity.isAvailable(),
                readinessState,
                entity.getAssignedStationId()
        );
    }
}
