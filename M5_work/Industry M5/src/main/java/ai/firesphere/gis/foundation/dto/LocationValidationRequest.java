package ai.firesphere.gis.foundation.dto;

import jakarta.validation.constraints.NotNull;

public record LocationValidationRequest(
        @NotNull(message = "Latitude is required")
        Double latitude,

        @NotNull(message = "Longitude is required")
        Double longitude,

        Double accuracyMeters
) {}
