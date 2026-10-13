package ai.firesphere.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IncidentCloseRequest(
        @NotBlank(message = "Closure remarks are required")
        @Size(max = 1000, message = "Closure remarks cannot exceed 1000 characters")
        String remarks
) {}
