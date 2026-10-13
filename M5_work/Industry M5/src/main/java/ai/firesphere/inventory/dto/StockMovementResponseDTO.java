package ai.firesphere.inventory.dto;

import ai.firesphere.inventory.model.StockMovement;
import ai.firesphere.inventory.model.StockMovementType;

import java.time.Instant;
import java.util.UUID;

public record StockMovementResponseDTO(
        UUID id,
        UUID itemId,
        StockMovementType movementType,
        Integer quantity,
        Integer resultingQuantity,
        UUID incidentId,
        String destinationOrSource,
        String remarks,
        String createdBy,
        Instant createdAt
) {
    public static StockMovementResponseDTO fromEntity(StockMovement entity) {
        return new StockMovementResponseDTO(
                entity.getId(),
                entity.getItemId(),
                entity.getMovementType(),
                entity.getQuantity(),
                entity.getResultingQuantity(),
                entity.getIncidentId(),
                entity.getDestinationOrSource(),
                entity.getRemarks(),
                entity.getCreatedBy(),
                entity.getCreatedAt()
        );
    }
}
