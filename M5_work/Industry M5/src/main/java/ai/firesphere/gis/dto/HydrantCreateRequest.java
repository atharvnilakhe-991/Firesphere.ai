package ai.firesphere.gis.dto;

import jakarta.validation.constraints.*;

public record HydrantCreateRequest(
        @NotBlank(message = "Hydrant code is required")
        @Size(max = 32, message = "Hydrant code cannot exceed 32 characters")
        String hydrantCode,

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        Double latitude,

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        Double longitude,

        Double pressurePsi,
        Double flowRateLpm,
        Boolean operable,

        @Size(max = 300, message = "Address cannot exceed 300 characters")
        String address
) {}
