package ai.firesphere.incident.dto;

import ai.firesphere.incident.model.IncidentStatus;
import ai.firesphere.incident.model.IncidentStatusHistory;

import java.time.Instant;
import java.util.UUID;

public record IncidentStatusHistoryDTO(
        UUID id,
        UUID incidentId,
        IncidentStatus previousStatus,
        IncidentStatus newStatus,
        Instant transitionedAt,
        String transitionedBy,
        String reason
) {
    public static IncidentStatusHistoryDTO fromEntity(IncidentStatusHistory entity) {
        return new IncidentStatusHistoryDTO(
                entity.getId(),
                entity.getIncidentId(),
                entity.getPreviousStatus(),
                entity.getNewStatus(),
                entity.getTransitionedAt(),
                entity.getTransitionedBy(),
                entity.getReason()
        );
    }
}
