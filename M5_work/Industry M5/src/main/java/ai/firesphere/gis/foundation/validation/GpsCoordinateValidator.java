package ai.firesphere.gis.foundation.validation;

import ai.firesphere.gis.foundation.model.LocationAccuracyLevel;
import ai.firesphere.gis.foundation.model.LocationValidationResult;
import ai.firesphere.gis.foundation.model.LocationValidationStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Strict GPS coordinate and telemetry validator.
 * Validates geographical coordinate ranges, numerical sanity (NaN/Infinity),
 * accuracy confidence intervals, and malformed inputs.
 * NEVER fabricates or silently replaces invalid coordinates.
 */
@Component
public class GpsCoordinateValidator {

    public static final double MIN_LATITUDE = -90.0;
    public static final double MAX_LATITUDE = 90.0;
    public static final double MIN_LONGITUDE = -180.0;
    public static final double MAX_LONGITUDE = 180.0;

    /**
     * Threshold in meters beyond which location accuracy is considered too degraded for tactical dispatch.
     */
    public static final double MAX_TACTICAL_ACCURACY_METERS = 200.0;

    /**
     * Threshold in meters beyond which location accuracy is considered poor/coarse.
     */
    public static final double POOR_ACCURACY_THRESHOLD_METERS = 50.0;

    /**
     * Validates Double latitude, longitude, and optional accuracy in meters.
     */
    public LocationValidationResult validate(Double latitude, Double longitude, Double accuracyMeters) {
        List<String> errors = new ArrayList<>();

        if (latitude == null) {
            errors.add("Latitude is missing or null");
        } else if (latitude.isNaN() || latitude.isInfinite()) {
            errors.add("Latitude is malformed (NaN or Infinite)");
        } else if (latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) {
            errors.add(String.format("Latitude %.6f is out of bounds [%.1f, %.1f]", latitude, MIN_LATITUDE, MAX_LATITUDE));
        }

        if (longitude == null) {
            errors.add("Longitude is missing or null");
        } else if (longitude.isNaN() || longitude.isInfinite()) {
            errors.add("Longitude is malformed (NaN or Infinite)");
        } else if (longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
            errors.add(String.format("Longitude %.6f is out of bounds [%.1f, %.1f]", longitude, MIN_LONGITUDE, MAX_LONGITUDE));
        }

        if (!errors.isEmpty()) {
            return LocationValidationResult.invalid(errors);
        }

        // Validate accuracy information
        LocationAccuracyLevel accuracyLevel = LocationAccuracyLevel.fromAccuracyMeters(accuracyMeters);

        if (accuracyMeters == null) {
            // Valid coordinates, but accuracy telemetry not provided
            return new LocationValidationResult(
                    true,
                    LocationValidationStatus.UNVERIFIED,
                    LocationAccuracyLevel.UNKNOWN,
                    latitude,
                    longitude,
                    null,
                    List.of("Location coordinates are valid, but horizontal accuracy telemetry was not provided")
            );
        }

        if (accuracyMeters.isNaN() || accuracyMeters.isInfinite() || accuracyMeters < 0.0) {
            return new LocationValidationResult(
                    true,
                    LocationValidationStatus.APPROXIMATE,
                    LocationAccuracyLevel.UNKNOWN,
                    latitude,
                    longitude,
                    null,
                    List.of("Provided accuracy metric is malformed or negative; coordinates accepted as approximate")
            );
        }

        if (accuracyMeters > MAX_TACTICAL_ACCURACY_METERS) {
            return new LocationValidationResult(
                    true,
                    LocationValidationStatus.POOR_ACCURACY,
                    LocationAccuracyLevel.POOR,
                    latitude,
                    longitude,
                    accuracyMeters,
                    List.of(String.format("Horizontal accuracy of %.1fm exceeds tactical safety limit of %.1fm", accuracyMeters, MAX_TACTICAL_ACCURACY_METERS))
            );
        }

        if (accuracyMeters > POOR_ACCURACY_THRESHOLD_METERS) {
            return new LocationValidationResult(
                    true,
                    LocationValidationStatus.APPROXIMATE,
                    LocationAccuracyLevel.POOR,
                    latitude,
                    longitude,
                    accuracyMeters,
                    List.of(String.format("Horizontal accuracy is coarse (%.1fm > %.1fm)", accuracyMeters, POOR_ACCURACY_THRESHOLD_METERS))
            );
        }

        return LocationValidationResult.valid(latitude, longitude, accuracyMeters, accuracyLevel);
    }

    /**
     * Convenience method to parse and validate string representations of coordinates.
     */
    public LocationValidationResult validateStrings(String latStr, String lonStr, String accuracyStr) {
        List<String> errors = new ArrayList<>();
        Double lat = null;
        Double lon = null;
        Double acc = null;

        if (latStr == null || latStr.isBlank()) {
            errors.add("Latitude string is missing or empty");
        } else {
            try {
                lat = Double.parseDouble(latStr.trim());
            } catch (NumberFormatException e) {
                errors.add("Latitude '" + latStr + "' is not a valid floating-point number");
            }
        }

        if (lonStr == null || lonStr.isBlank()) {
            errors.add("Longitude string is missing or empty");
        } else {
            try {
                lon = Double.parseDouble(lonStr.trim());
            } catch (NumberFormatException e) {
                errors.add("Longitude '" + lonStr + "' is not a valid floating-point number");
            }
        }

        if (accuracyStr != null && !accuracyStr.isBlank()) {
            try {
                acc = Double.parseDouble(accuracyStr.trim());
            } catch (NumberFormatException e) {
                errors.add("Accuracy metric '" + accuracyStr + "' is not a valid number");
            }
        }

        if (!errors.isEmpty()) {
            return LocationValidationResult.invalid(errors);
        }

        return validate(lat, lon, acc);
    }
}
