package ai.firesphere.fleet.dto;

import jakarta.validation.constraints.NotNull;

public record VehicleTelematicsUpdateRequest(
        @NotNull(message = "Latitude is required")
        Double latitude,

        @NotNull(message = "Longitude is required")
        Double longitude,

        Double speedKmh,
        Double headingDegrees,
        Double fuelPercent,
        Double waterLevelPercent
) {}
