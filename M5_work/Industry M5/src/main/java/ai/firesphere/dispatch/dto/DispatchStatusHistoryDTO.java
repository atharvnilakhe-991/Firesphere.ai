package ai.firesphere.dispatch.dto;

import ai.firesphere.dispatch.model.DispatchStatus;
import ai.firesphere.dispatch.model.DispatchStatusHistory;

import java.time.Instant;
import java.util.UUID;

public record DispatchStatusHistoryDTO(
        UUID id,
        UUID dispatchOrderId,
        DispatchStatus previousStatus,
        DispatchStatus newStatus,
        Instant transitionedAt,
        String transitionedBy,
        String reason
) {
    public static DispatchStatusHistoryDTO fromEntity(DispatchStatusHistory entity) {
        return new DispatchStatusHistoryDTO(
                entity.getId(),
                entity.getDispatchOrderId(),
                entity.getPreviousStatus(),
                entity.getNewStatus(),
                entity.getTransitionedAt(),
                entity.getTransitionedBy(),
                entity.getReason()
        );
    }
}
