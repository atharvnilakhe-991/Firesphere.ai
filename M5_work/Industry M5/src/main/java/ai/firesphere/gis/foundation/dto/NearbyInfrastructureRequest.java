package ai.firesphere.gis.foundation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record NearbyInfrastructureRequest(
        @NotNull(message = "Latitude is required")
        Double latitude,

        @NotNull(message = "Longitude is required")
        Double longitude,

        @Positive(message = "Radius in meters must be positive")
        Double radiusMeters,

        Boolean operableOnly
) {
    public NearbyInfrastructureRequest {
        if (radiusMeters == null || radiusMeters <= 0.0) {
            radiusMeters = 500.0;
        }
    }
}
