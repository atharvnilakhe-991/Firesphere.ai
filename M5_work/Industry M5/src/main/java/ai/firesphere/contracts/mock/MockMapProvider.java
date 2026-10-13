package ai.firesphere.contracts.mock;

import ai.firesphere.contracts.gis.MapRoutingProviderContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Clean development and testing mock implementation for MapRoutingProviderContract.
 * Employs haversine geodesic approximation when external GIS routing engines are unattached.
 */
@Service
@ConditionalOnMissingBean(name = "productionMapProvider")
public class MockMapProvider implements MapRoutingProviderContract {

    private static final Logger log = LoggerFactory.getLogger(MockMapProvider.class);
    private static final double EARTH_RADIUS_METERS = 6371000.0;

    @Override
    public RouteCalculationResult calculateEmergencyRoute(double startLat, double startLon, double destLat, double destLon) {
        double distanceMeters = calculateDistanceMeters(startLat, startLon, destLat, destLon);
        // Approximate emergency speed: 50 km/h (13.88 m/s) with siren priority
        long estimatedDurationSeconds = Math.max(30L, Math.round(distanceMeters / 13.88));

        log.info("[MOCK MAP] Route calculated from [{}, {}] to [{}, {}]: {}m, {}s",
                startLat, startLon, destLat, destLon, distanceMeters, estimatedDurationSeconds);

        List<GeoWaypoint> waypoints = List.of(
                new GeoWaypoint(startLat, startLon, 1, "Depart fire station bay with lights & sirens"),
                new GeoWaypoint((startLat + destLat) / 2.0, (startLon + destLon) / 2.0, 2, "Continue onto primary arterial emergency corridor"),
                new GeoWaypoint(destLat, destLon, 3, "Arrive on scene; stage apparatus at safe standoff distance")
        );

        return new RouteCalculationResult(distanceMeters, estimatedDurationSeconds, waypoints, "polyline_mock_encoded_string");
    }

    @Override
    public double calculateDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    @Override
    public boolean isPointInHazardZone(double lat, double lon, UUID hazardZoneId) {
        log.info("[MOCK MAP] Checking point [{}, {}] against hazard zone {}", lat, lon, hazardZoneId);
        return false;
    }
}
