package ai.firesphere.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record StockIssueRequest(
        @NotNull(message = "Item ID is required")
        UUID itemId,

        @NotNull(message = "Quantity to issue is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity,

        UUID incidentId,

        @Size(max = 100, message = "Destination cannot exceed 100 characters")
        String destination,

        @Size(max = 500, message = "Remarks cannot exceed 500 characters")
        String remarks
) {}
