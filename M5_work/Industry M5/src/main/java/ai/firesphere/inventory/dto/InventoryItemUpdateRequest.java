package ai.firesphere.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record InventoryItemUpdateRequest(
        @Size(max = 150, message = "Item name cannot exceed 150 characters")
        String name,

        @Size(max = 60, message = "Category cannot exceed 60 characters")
        String category,

        @Size(max = 500, message = "Description cannot exceed 500 characters")
        String description,

        @Min(value = 0, message = "Minimum threshold cannot be negative")
        Integer minimumThreshold,

        @Size(max = 30, message = "Unit of measure cannot exceed 30 characters")
        String unitOfMeasure,

        @Size(max = 100, message = "Location in store cannot exceed 100 characters")
        String locationInStore,

        @Size(max = 60, message = "Batch number cannot exceed 60 characters")
        String batchNumber,

        Boolean isActive
) {}
