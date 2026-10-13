package ai.firesphere.gis;

import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.gis.model.HazardZone;
import ai.firesphere.gis.model.Hydrant;
import ai.firesphere.gis.repository.HazardZoneRepository;
import ai.firesphere.gis.repository.HydrantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SpatialQueryH2Test {

    @Autowired
    private HydrantRepository hydrantRepository;

    @Autowired
    private HazardZoneRepository hazardZoneRepository;

    private final GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);
    private final UUID tenantId = UUID.randomUUID();

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE ALIAS IF NOT EXISTS ST_DISTANCE FOR \"ai.firesphere.gis.util.H2SpatialFunctions.stDistance\"");
        jdbcTemplate.execute("CREATE ALIAS IF NOT EXISTS ST_CONTAINS FOR \"ai.firesphere.gis.util.H2SpatialFunctions.stContains\"");
        jdbcTemplate.execute("CREATE ALIAS IF NOT EXISTS ST_INTERSECTS FOR \"ai.firesphere.gis.util.H2SpatialFunctions.stIntersects\"");
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testSaveAndRetrieveHydrantWithGeometry() {
        Hydrant hydrant = new Hydrant();
        hydrant.setHydrantCode("HYD-TEST-1");
        hydrant.setLatitude(19.0760);
        hydrant.setLongitude(72.8777);
        Point point = gf.createPoint(new Coordinate(72.8777, 19.0760));
        hydrant.setGeom(point);
        hydrant.setOperable(true);
        hydrant.setAddress("Test Station Bay");

        Hydrant saved = hydrantRepository.save(hydrant);
        assertNotNull(saved.getId());
        assertNotNull(saved.getGeom());
        assertEquals(72.8777, saved.getGeom().getX());
        assertEquals(19.0760, saved.getGeom().getY());

        var nearby = hydrantRepository.findNearbyWithinDistance(tenantId, point, 0.05);
        assertEquals(1, nearby.size());
    }

    @Test
    void testHazardZonePointInZoneContainment() {
        HazardZone zone = new HazardZone();
        zone.setZoneCode("HZ-REFINERY-01");
        zone.setHazardName("Petrochemical Storage Tank Area");
        zone.setHazardType("CHEMICAL");
        zone.setSeverity("CRITICAL");
        zone.setCenterLatitude(19.0800);
        zone.setCenterLongitude(72.8800);
        zone.setRadiusMeters(500.0);
        zone.setActive(true);

        Coordinate[] coords = new Coordinate[] {
                new Coordinate(72.8700, 19.0700),
                new Coordinate(72.8900, 19.0700),
                new Coordinate(72.8900, 19.0900),
                new Coordinate(72.8700, 19.0900),
                new Coordinate(72.8700, 19.0700)
        };
        org.locationtech.jts.geom.Polygon polygon = gf.createPolygon(coords);
        zone.setGeom(polygon);

        hazardZoneRepository.save(zone);

        // Point inside
        Point insidePoint = gf.createPoint(new Coordinate(72.8800, 19.0800));
        var insideZones = hazardZoneRepository.findZonesContainingPoint(tenantId, insidePoint);
        assertEquals(1, insideZones.size());
        assertEquals("HZ-REFINERY-01", insideZones.get(0).getZoneCode());

        // Point outside
        Point outsidePoint = gf.createPoint(new Coordinate(72.8500, 19.0500));
        var outsideZones = hazardZoneRepository.findZonesContainingPoint(tenantId, outsidePoint);
        assertTrue(outsideZones.isEmpty());
    }
}
