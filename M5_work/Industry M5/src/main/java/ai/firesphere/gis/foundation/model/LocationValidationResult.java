package ai.firesphere.gis.foundation.model;

import java.util.Collections;
import java.util.List;

/**
 * Result container for GPS coordinate and accuracy validation checks.
 */
public record LocationValidationResult(
        boolean valid,
        LocationValidationStatus status,
        LocationAccuracyLevel accuracyLevel,
        Double latitude,
        Double longitude,
        Double accuracyMeters,
        List<String> validationErrors
) {

    public LocationValidationResult {
        if (validationErrors == null) {
            validationErrors = Collections.emptyList();
        }
    }

    public static LocationValidationResult valid(Double lat, Double lon, Double accuracy, LocationAccuracyLevel level) {
        return new LocationValidationResult(
                true,
                LocationValidationStatus.VALID,
                level != null ? level : LocationAccuracyLevel.UNKNOWN,
                lat,
                lon,
                accuracy,
                Collections.emptyList()
        );
    }

    public static LocationValidationResult approximate(Double lat, Double lon, Double accuracy, LocationAccuracyLevel level, String reason) {
        return new LocationValidationResult(
                true,
                LocationValidationStatus.APPROXIMATE,
                level != null ? level : LocationAccuracyLevel.UNKNOWN,
                lat,
                lon,
                accuracy,
                List.of(reason)
        );
    }

    public static LocationValidationResult invalid(List<String> errors) {
        return new LocationValidationResult(
                false,
                LocationValidationStatus.INVALID,
                LocationAccuracyLevel.UNKNOWN,
                null,
                null,
                null,
                errors
        );
    }

    public static LocationValidationResult poorAccuracy(Double lat, Double lon, Double accuracy, String warning) {
        return new LocationValidationResult(
                true,
                LocationValidationStatus.POOR_ACCURACY,
                LocationAccuracyLevel.POOR,
                lat,
                lon,
                accuracy,
                List.of(warning)
        );
    }
}
