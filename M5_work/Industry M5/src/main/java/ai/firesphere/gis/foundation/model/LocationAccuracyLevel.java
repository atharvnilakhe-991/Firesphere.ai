package ai.firesphere.gis.foundation.model;

/**
 * Categorical GPS accuracy thresholds:
 * - HIGH: <= 15.0 meters (standard for RTK or good mobile GPS fix)
 * - MODERATE: 15.0 - 50.0 meters (cell-tower assisted or indoor degraded fix)
 * - POOR: > 50.0 meters (coarse network estimation)
 * - UNKNOWN: Accuracy metric not provided
 */
public enum LocationAccuracyLevel {
    HIGH,
    MODERATE,
    POOR,
    UNKNOWN;

    public static LocationAccuracyLevel fromAccuracyMeters(Double accuracyMeters) {
        if (accuracyMeters == null || accuracyMeters.isNaN() || accuracyMeters.isInfinite() || accuracyMeters < 0.0) {
            return UNKNOWN;
        }
        if (accuracyMeters <= 15.0) {
            return HIGH;
        } else if (accuracyMeters <= 50.0) {
            return MODERATE;
        } else {
            return POOR;
        }
    }
}
