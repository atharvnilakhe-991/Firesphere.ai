package ai.firesphere.gis.util;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;

/**
 * Spatial utilities for WGS84 (SRID 4326) geometry operations,
 * strict coordinate validation, WKT parsing, and geodesic distance calculations.
 *
 * <p>AUTHORITATIVE COORDINATE CONVENTION:
 * <ul>
 *   <li>X = Longitude (Range: [-180.0, +180.0])</li>
 *   <li>Y = Latitude  (Range: [-90.0, +90.0])</li>
 * </ul>
 */
public final class GeometryUtils {

    public static final int SRID_WGS84 = 4326;
    public static final double EARTH_RADIUS_METERS = 6371000.0;
    public static final double METERS_PER_DEGREE_APPROX = 111320.0;
    public static final double MAX_SEARCH_RADIUS_METERS = 100000.0; // 100 km safe operational ceiling

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), SRID_WGS84);

    private GeometryUtils() {}

    public static GeometryFactory getGeometryFactory() {
        return GF;
    }

    /**
     * Creates a WGS84 JTS Point from longitude and latitude coordinates.
     * Note: JTS Coordinate order is (x=longitude, y=latitude).
     */
    public static Point createPoint(Double longitude, Double latitude) {
        validateCoordinates(latitude, longitude);
        Point point = GF.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(SRID_WGS84);
        return point;
    }

    /**
     * Strictly validates latitude (-90 to +90) and longitude (-180 to +180).
     */
    public static void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null || latitude.isNaN() || latitude.isInfinite()) {
            throw new IllegalArgumentException("Latitude cannot be null, NaN, or infinite");
        }
        if (longitude == null || longitude.isNaN() || longitude.isInfinite()) {
            throw new IllegalArgumentException("Longitude cannot be null, NaN, or infinite");
        }
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException(String.format("Latitude %f is out of bounds [-90.0, +90.0]", latitude));
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException(String.format("Longitude %f is out of bounds [-180.0, +180.0]", longitude));
        }
    }

    /**
     * Validates search radius constraints.
     */
    public static void validateRadius(Double radiusMeters, double maxRadiusMeters) {
        if (radiusMeters == null || radiusMeters.isNaN() || radiusMeters.isInfinite() || radiusMeters <= 0.0) {
            throw new IllegalArgumentException("Radius must be a positive non-zero number");
        }
        if (radiusMeters > maxRadiusMeters) {
            throw new IllegalArgumentException(String.format("Radius %.1f exceeds maximum allowed radius of %.1f meters", radiusMeters, maxRadiusMeters));
        }
    }

    /**
     * Converts a search radius in meters to maximum equivalent degrees accounting for spherical latitude convergence.
     */
    public static double metersToMaxDegrees(double radiusMeters, double latitude) {
        double deltaLat = (radiusMeters / EARTH_RADIUS_METERS) * (180.0 / Math.PI);
        double latRad = Math.toRadians(Math.abs(latitude));
        double cosLat = Math.cos(latRad);
        if (cosLat < 0.0001) cosLat = 0.0001; // Avoid divide-by-zero near poles
        double deltaLon = (radiusMeters / (EARTH_RADIUS_METERS * cosLat)) * (180.0 / Math.PI);
        return Math.max(deltaLat, deltaLon) * 1.05; // 5% buffer ensuring bounding envelope captures all points
    }

    /**
     * Validates that geometry is an acceptable, valid hazard zone type (Polygon or MultiPolygon with SRID 4326).
     */
    public static Geometry validateHazardZoneGeometry(Geometry geom) {
        if (geom == null) {
            throw new IllegalArgumentException("Hazard zone geometry cannot be null");
        }
        if (!(geom instanceof Polygon || geom instanceof MultiPolygon)) {
            throw new IllegalArgumentException(
                    "Hazard zone geometry must be either Polygon or MultiPolygon, found: " + geom.getGeometryType());
        }
        if (geom.isEmpty()) {
            throw new IllegalArgumentException("Hazard zone geometry cannot be empty");
        }
        if (!geom.isValid()) {
            throw new IllegalArgumentException("Hazard zone geometry is spatially invalid (self-intersecting or malformed)");
        }
        geom.setSRID(SRID_WGS84);
        return geom;
    }

    /**
     * Parses a Well-Known Text (WKT) string into a validated JTS Geometry.
     */
    public static Geometry parseWkt(String wkt) {
        if (wkt == null || wkt.trim().isEmpty()) {
            throw new IllegalArgumentException("WKT geometry representation cannot be null or empty");
        }
        try {
            WKTReader reader = new WKTReader(GF);
            Geometry geom = reader.read(wkt.trim());
            return validateHazardZoneGeometry(geom);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Malformed WKT geometry: " + e.getMessage(), e);
        }
    }

    /**
     * Converts a JTS Geometry to a WKT string.
     */
    public static String toWkt(Geometry geometry) {
        if (geometry == null) return null;
        WKTWriter writer = new WKTWriter();
        return writer.write(geometry);
    }

    /**
     * Generates a circular polygon approximation for a given center coordinate and radius in meters.
     */
    public static Polygon createCircularPolygon(double centerLon, double centerLat, double radiusMeters, int numPoints) {
        validateCoordinates(centerLat, centerLon);
        if (radiusMeters <= 0.0) {
            throw new IllegalArgumentException("Radius must be greater than zero");
        }
        int points = Math.max(16, numPoints);
        Coordinate[] coords = new Coordinate[points + 1];

        double latRad = Math.toRadians(centerLat);
        double deltaLat = (radiusMeters / EARTH_RADIUS_METERS) * (180.0 / Math.PI);
        double deltaLon = (radiusMeters / (EARTH_RADIUS_METERS * Math.cos(latRad))) * (180.0 / Math.PI);

        for (int i = 0; i < points; i++) {
            double angle = 2.0 * Math.PI * i / points;
            double lon = centerLon + deltaLon * Math.cos(angle);
            double lat = centerLat + deltaLat * Math.sin(angle);
            coords[i] = new Coordinate(lon, lat);
        }
        coords[points] = coords[0]; // Close the ring

        LinearRing shell = GF.createLinearRing(coords);
        Polygon polygon = GF.createPolygon(shell);
        polygon.setSRID(SRID_WGS84);
        return (Polygon) validateHazardZoneGeometry(polygon);
    }

    /**
     * Calculates geodesic distance in meters using Haversine formula.
     */
    public static double calculateHaversineDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_METERS * c;
    }
}
