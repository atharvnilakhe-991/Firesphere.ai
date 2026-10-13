package ai.firesphere.gis.util;

import org.h2.value.Value;
import org.h2.value.ValueGeometry;
import org.locationtech.jts.geom.Geometry;

public class H2SpatialFunctions {

    public static double stDistance(Value v1, Value v2) {
        Geometry geom1 = toGeometry(v1);
        Geometry geom2 = toGeometry(v2);
        if (geom1 == null || geom2 == null) {
            return Double.MAX_VALUE;
        }
        return geom1.distance(geom2);
    }

    public static boolean stContains(Value v1, Value v2) {
        Geometry geom1 = toGeometry(v1);
        Geometry geom2 = toGeometry(v2);
        if (geom1 == null || geom2 == null) {
            return false;
        }
        return geom1.contains(geom2);
    }

    public static boolean stIntersects(Value v1, Value v2) {
        Geometry geom1 = toGeometry(v1);
        Geometry geom2 = toGeometry(v2);
        if (geom1 == null || geom2 == null) {
            return false;
        }
        return geom1.intersects(geom2);
    }

    public static boolean stDWithin(Value v1, Value v2, Value vDist) {
        Geometry geom1 = toGeometry(v1);
        Geometry geom2 = toGeometry(v2);
        if (geom1 == null || geom2 == null || vDist == null) {
            return false;
        }
        return geom1.distance(geom2) <= vDist.getDouble();
    }

    private static Geometry toGeometry(Value val) {
        if (val == null || val.containsNull()) return null;
        if (val instanceof ValueGeometry vg) {
            return vg.getGeometry();
        }
        try {
            byte[] bytes = val.getBytes();
            if (bytes != null && bytes.length > 0) {
                return new org.locationtech.jts.io.WKBReader().read(bytes);
            }
            String str = val.getString();
            if (str != null && !str.isBlank()) {
                return new org.locationtech.jts.io.WKTReader().read(str);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
