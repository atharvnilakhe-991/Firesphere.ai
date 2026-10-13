package ai.firesphere.gis.foundation.infrastructure;

import java.util.Collections;
import java.util.List;

public record NearbyInfrastructureDTO(
        double searchLatitude,
        double searchLongitude,
        double radiusMeters,
        int totalFound,
        Double nearestDistanceMeters,
        List<InfrastructureItemDTO> items
) {

    public NearbyInfrastructureDTO {
        if (items == null) {
            items = Collections.emptyList();
        }
    }

    public static NearbyInfrastructureDTO empty(double lat, double lon, double radiusMeters) {
        return new NearbyInfrastructureDTO(
                lat,
                lon,
                radiusMeters,
                0,
                null,
                Collections.emptyList()
        );
    }
}
