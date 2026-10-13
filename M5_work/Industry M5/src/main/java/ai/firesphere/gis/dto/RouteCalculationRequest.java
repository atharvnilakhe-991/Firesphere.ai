package ai.firesphere.gis.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record RouteCalculationRequest(
        @NotNull(message = "Start latitude is required")
        @DecimalMin(value = "-90.0", message = "Start latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Start latitude must be between -90 and 90")
        Double startLatitude,

        @NotNull(message = "Start longitude is required")
        @DecimalMin(value = "-180.0", message = "Start longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Start longitude must be between -180 and 180")
        Double startLongitude,

        @NotNull(message = "Destination latitude is required")
        @DecimalMin(value = "-90.0", message = "Destination latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Destination latitude must be between -90 and 90")
        Double destinationLatitude,

        @NotNull(message = "Destination longitude is required")
        @DecimalMin(value = "-180.0", message = "Destination longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Destination longitude must be between -180 and 180")
        Double destinationLongitude
) {}
