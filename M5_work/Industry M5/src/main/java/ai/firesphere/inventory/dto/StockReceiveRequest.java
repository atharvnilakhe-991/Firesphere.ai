package ai.firesphere.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockReceiveRequest(
        @NotNull(message = "Quantity to receive is required")
        @Min(value = 1, message = "Quantity to receive must be at least 1")
        Integer quantity,

        @Size(max = 100, message = "Source description cannot exceed 100 characters")
        String source,

        @Size(max = 500, message = "Remarks cannot exceed 500 characters")
        String remarks,

        @Size(max = 60, message = "Batch number cannot exceed 60 characters")
        String batchNumber
) {}
