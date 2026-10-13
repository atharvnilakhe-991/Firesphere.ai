package ai.firesphere.inventory.dto;

import ai.firesphere.inventory.model.InventoryItem;

import java.time.Instant;
import java.util.UUID;

public record InventoryItemResponseDTO(
        UUID id,
        String sku,
        String name,
        String category,
        UUID stationId,
        Integer quantityOnHand,
        Integer minimumThreshold,
        String unitOfMeasure,
        String description,
        String locationInStore,
        String batchNumber,
        boolean isActive,
        boolean lowStockAlert,
        Instant createdAt,
        Instant updatedAt
) {
    public static InventoryItemResponseDTO fromEntity(InventoryItem entity) {
        return new InventoryItemResponseDTO(
                entity.getId(),
                entity.getSku(),
                entity.getName(),
                entity.getCategory(),
                entity.getStationId(),
                entity.getQuantityOnHand(),
                entity.getMinimumThreshold(),
                entity.getUnitOfMeasure(),
                entity.getDescription(),
                entity.getLocationInStore(),
                entity.getBatchNumber(),
                entity.isActive(),
                entity.isLowStock(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
