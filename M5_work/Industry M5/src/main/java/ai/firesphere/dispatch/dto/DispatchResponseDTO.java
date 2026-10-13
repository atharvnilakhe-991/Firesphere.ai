package ai.firesphere.dispatch.dto;

import ai.firesphere.dispatch.model.DispatchOrder;
import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.dispatch.model.DispatchStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DispatchResponseDTO(
        UUID id,
        UUID incidentId,
        UUID stationId,
        String dispatchCode,
        DispatchStatus status,
        DispatchPriority priority,
        List<UUID> assignedVehicleIds,
        List<UUID> assignedResponderIds,
        Instant dispatchedAt,
        Instant acknowledgedAt,
        Instant enRouteAt,
        Instant arrivedAt,
        Instant completedAt,
        Instant cancelledAt,
        String cancellationReason,
        String operationalNotes,
        Instant createdAt,
        Instant updatedAt
) {
    public static DispatchResponseDTO fromEntity(DispatchOrder entity) {
        return new DispatchResponseDTO(
                entity.getId(),
                entity.getIncidentId(),
                entity.getStationId(),
                entity.getDispatchCode(),
                entity.getStatus(),
                entity.getPriority(),
                entity.getAssignedVehicleIds() != null ? List.copyOf(entity.getAssignedVehicleIds()) : List.of(),
                entity.getAssignedResponderIds() != null ? List.copyOf(entity.getAssignedResponderIds()) : List.of(),
                entity.getDispatchedAt(),
                entity.getAcknowledgedAt(),
                entity.getEnRouteAt(),
                entity.getArrivedAt(),
                entity.getCompletedAt(),
                entity.getCancelledAt(),
                entity.getCancellationReason(),
                entity.getOperationalNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
