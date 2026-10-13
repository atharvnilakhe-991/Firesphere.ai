package ai.firesphere.inventory.dto;

import ai.firesphere.inventory.model.StockMovementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockAdjustmentRequest(
        @NotNull(message = "Quantity delta is required")
        Integer quantityDelta,

        StockMovementType movementType,

        @NotBlank(message = "Adjustment reason is required")
        @Size(max = 500, message = "Reason cannot exceed 500 characters")
        String reason,

        @Size(max = 500, message = "Remarks cannot exceed 500 characters")
        String remarks
) {}
