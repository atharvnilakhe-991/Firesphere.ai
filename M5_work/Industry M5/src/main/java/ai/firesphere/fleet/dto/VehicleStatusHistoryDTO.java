package ai.firesphere.fleet.dto;

import ai.firesphere.fleet.model.VehicleStatus;
import ai.firesphere.fleet.model.VehicleStatusHistory;

import java.time.Instant;
import java.util.UUID;

public record VehicleStatusHistoryDTO(
        UUID id,
        UUID vehicleId,
        VehicleStatus previousStatus,
        VehicleStatus newStatus,
        Instant transitionedAt,
        String transitionedBy,
        String reason
) {
    public static VehicleStatusHistoryDTO fromEntity(VehicleStatusHistory entity) {
        return new VehicleStatusHistoryDTO(
                entity.getId(),
                entity.getVehicleId(),
                entity.getPreviousStatus(),
                entity.getNewStatus(),
                entity.getTransitionedAt(),
                entity.getTransitionedBy(),
                entity.getReason()
        );
    }
}
