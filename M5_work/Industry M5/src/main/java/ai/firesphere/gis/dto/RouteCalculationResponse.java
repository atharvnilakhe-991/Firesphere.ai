package ai.firesphere.gis.dto;

import ai.firesphere.contracts.gis.MapRoutingProviderContract;

import java.util.List;

public record RouteCalculationResponse(
        double distanceMeters,
        long estimatedDurationSeconds,
        List<MapRoutingProviderContract.GeoWaypoint> waypoints,
        String polyline
) {}
