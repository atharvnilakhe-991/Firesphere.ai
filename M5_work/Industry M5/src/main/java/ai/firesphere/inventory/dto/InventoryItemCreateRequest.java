package ai.firesphere.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record InventoryItemCreateRequest(
        @NotBlank(message = "SKU is required")
        @Size(max = 64, message = "SKU cannot exceed 64 characters")
        String sku,

        @NotBlank(message = "Item name is required")
        @Size(max = 150, message = "Item name cannot exceed 150 characters")
        String name,

        @NotBlank(message = "Category is required")
        @Size(max = 60, message = "Category cannot exceed 60 characters")
        String category,

        @NotNull(message = "Station ID is required")
        UUID stationId,

        @NotNull(message = "Initial quantity is required")
        @Min(value = 0, message = "Quantity cannot be negative")
        Integer initialQuantity,

        @NotNull(message = "Minimum threshold is required")
        @Min(value = 0, message = "Threshold cannot be negative")
        Integer minimumThreshold,

        @NotBlank(message = "Unit of measure is required")
        String unitOfMeasure,

        @Size(max = 500, message = "Description cannot exceed 500 characters")
        String description,

        @Size(max = 100, message = "Location in store cannot exceed 100 characters")
        String locationInStore,

        @Size(max = 60, message = "Batch number cannot exceed 60 characters")
        String batchNumber
) {
    public InventoryItemCreateRequest(
            String sku,
            String name,
            String category,
            UUID stationId,
            Integer initialQuantity,
            Integer minimumThreshold,
            String unitOfMeasure,
            String batchNumber) {
        this(sku, name, category, stationId, initialQuantity, minimumThreshold, unitOfMeasure, null, null, batchNumber);
    }
}
