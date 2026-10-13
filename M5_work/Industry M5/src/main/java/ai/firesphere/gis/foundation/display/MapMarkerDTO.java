package ai.firesphere.gis.foundation.display;

public record MapMarkerDTO(
        String id,
        String referenceId,
        String markerType, // PROPERTY, INSPECTION, HYDRANT, HAZARD_ZONE, INCIDENT
        String title,
        String subtitle,
        double latitude,
        double longitude,
        String statusColor, // HEX code e.g. #38A169 (green), #E53E3E (red), #3182CE (blue)
        Double accuracyRadiusMeters,
        String iconKey
) {}
