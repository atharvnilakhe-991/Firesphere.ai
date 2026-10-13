package ai.firesphere.gis.dto;

import jakarta.validation.constraints.*;

public record HazardZoneUpdateRequest(
        @NotBlank(message = "Hazard name is required")
        @Size(max = 150, message = "Hazard name cannot exceed 150 characters")
        String hazardName,

        @NotBlank(message = "Hazard type is required")
        @Size(max = 60, message = "Hazard type cannot exceed 60 characters")
        String hazardType,

        @NotNull(message = "Center latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        Double centerLatitude,

        @NotNull(message = "Center longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        Double centerLongitude,

        @NotNull(message = "Radius in meters is required")
        @Positive(message = "Radius must be positive")
        Double radiusMeters,

        @NotBlank(message = "Severity is required")
        @Size(max = 30, message = "Severity cannot exceed 30 characters")
        String severity,

        Boolean active,

        @Size(max = 1000, message = "Evacuation protocol cannot exceed 1000 characters")
        String evacuationProtocol,

        String polygonWkt
) {}
