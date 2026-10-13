package ai.firesphere.gis.foundation.dto;

import jakarta.validation.constraints.NotNull;

public record NavigationHandoffRequest(
        @NotNull(message = "Destination latitude is required")
        Double destinationLatitude,

        @NotNull(message = "Destination longitude is required")
        Double destinationLongitude,

        String destinationLabel,

        String destinationAddress
) {}
