package ai.firesphere.gis.foundation.dto;

import ai.firesphere.gis.foundation.model.LocationReferenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MapDisplayRequest(
        @NotBlank(message = "Reference ID is required")
        String referenceId,

        LocationReferenceType referenceType,

        @NotNull(message = "Latitude is required")
        Double latitude,

        @NotNull(message = "Longitude is required")
        Double longitude,

        String address,

        Double accuracyMeters
) {}
