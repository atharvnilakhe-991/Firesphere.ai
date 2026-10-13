package ai.firesphere.gis.foundation.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Standard location abstraction for properties, buildings, inspections, and operational scenes.
 * Encapsulates geographic coordinates, reference linkage, descriptive text, and accuracy telemetry.
 */
public record LocationRecord(
        String referenceId,
        LocationReferenceType referenceType,
        Double latitude,
        Double longitude,
        String address,
        String locationDescription,
        Double accuracyMeters,
        LocationAccuracyLevel accuracyLevel,
        LocationValidationStatus validationStatus,
        Instant recordedAt
) {

    public LocationRecord {
        Objects.requireNonNull(referenceId, "Reference ID must not be null");
        if (referenceType == null) {
            referenceType = LocationReferenceType.OTHER;
        }
        if (accuracyLevel == null) {
            accuracyLevel = LocationAccuracyLevel.fromAccuracyMeters(accuracyMeters);
        }
        if (validationStatus == null) {
            validationStatus = LocationValidationStatus.UNVERIFIED;
        }
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
    }

    public static LocationRecord of(
            String referenceId,
            LocationReferenceType referenceType,
            Double latitude,
            Double longitude,
            String address,
            Double accuracyMeters
    ) {
        LocationAccuracyLevel level = LocationAccuracyLevel.fromAccuracyMeters(accuracyMeters);
        LocationValidationStatus status = (latitude != null && longitude != null)
                ? LocationValidationStatus.VALID
                : LocationValidationStatus.INVALID;
        return new LocationRecord(
                referenceId,
                referenceType,
                latitude,
                longitude,
                address,
                null,
                accuracyMeters,
                level,
                status,
                Instant.now()
        );
    }
}
