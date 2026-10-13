package ai.firesphere.gis.foundation.infrastructure;

public record InfrastructureItemDTO(
        String id,
        String code,
        String infrastructureType, // FIRE_HYDRANT, FIRE_STATION
        double latitude,
        double longitude,
        double distanceMeters,
        String status, // OPERABLE, INOPERABLE, ACTIVE
        String address,
        String operationalDetails
) {}
