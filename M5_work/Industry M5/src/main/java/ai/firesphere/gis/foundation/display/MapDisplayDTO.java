package ai.firesphere.gis.foundation.display;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

public record MapDisplayDTO(
        double centerLatitude,
        double centerLongitude,
        int zoomLevel,
        BoundingBox boundingBox,
        List<MapMarkerDTO> markers,
        MapProviderStatus providerStatus,
        boolean offlineCapable,
        String statusMessage,
        Instant generatedAt
) {

    public MapDisplayDTO {
        if (markers == null) {
            markers = Collections.emptyList();
        }
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }
    }

    public record BoundingBox(
            double minLatitude,
            double minLongitude,
            double maxLatitude,
            double maxLongitude
    ) {}
}
