package ai.firesphere.contracts.gis;

import java.util.List;
import java.util.UUID;

/**
 * Service contract defining integration with GIS routing providers (OpenStreetMap/OSRM, Google Maps, PostGIS).
 */
public interface MapRoutingProviderContract {

    RouteCalculationResult calculateEmergencyRoute(double startLat, double startLon, double destLat, double destLon);

    double calculateDistanceMeters(double lat1, double lon1, double lat2, double lon2);

    boolean isPointInHazardZone(double lat, double lon, UUID hazardZoneId);

    record RouteCalculationResult(
            double distanceMeters,
            long estimatedDurationSeconds,
            List<GeoWaypoint> waypoints,
            String routeGeometryPolyline
    ) {}

    record GeoWaypoint(
            double latitude,
            double longitude,
            int sequence,
            String instruction
    ) {}
}
