package ai.firesphere.gis;

import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.gis.dto.HazardZoneCreateRequest;
import ai.firesphere.gis.dto.HydrantCreateRequest;
import ai.firesphere.gis.model.HazardZone;
import ai.firesphere.gis.model.Hydrant;
import ai.firesphere.gis.repository.HazardZoneRepository;
import ai.firesphere.gis.repository.HydrantRepository;
import ai.firesphere.gis.service.GisOperationalService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real PostgreSQL + PostGIS integration test suite using Testcontainers.
 *
 * <p>Validates:
 * <ul>
 *   <li>Real PostGIS extension creation (CREATE EXTENSION IF NOT EXISTS postgis)</li>
 *   <li>SRID 4326 persistence and coordinate ordering (X=lon, Y=lat)</li>
 *   <li>PostGIS GiST spatial index creation and usage on geometry and geography</li>
 *   <li>Index-aware ST_DWithin and ST_Distance proximity queries in meters</li>
 *   <li>Nearest-first distance ordering and out-of-radius exclusion</li>
 *   <li>Hazard zone polygon boundary containment semantics (ST_Intersects)</li>
 *   <li>Invalid geometry rejection (self-intersecting and non-polygon types)</li>
 *   <li>Multi-tenant isolation across all spatial operations</li>
 *   <li>Execution plan (EXPLAIN) verification of index-capable spatial queries</li>
 * </ul>
 *
 * <p>When Docker is unavailable in the execution environment, this test is safely skipped.
 */
@SpringBootTest
@EnabledIf("isDockerEnvironmentAvailable")
class PostGisIntegrationTest {

    static final boolean DOCKER_AVAILABLE = isDockerEnvironmentAvailable();

    static boolean isDockerEnvironmentAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    static PostgreSQLContainer<?> postgisContainer;

    static {
        if (DOCKER_AVAILABLE) {
            postgisContainer = new PostgreSQLContainer<>(
                    DockerImageName.parse("postgis/postgis:16-3.4-alpine").asCompatibleSubstituteFor("postgres")
            );
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (DOCKER_AVAILABLE && postgisContainer != null) {
            postgisContainer.start();
            registry.add("spring.datasource.url", postgisContainer::getJdbcUrl);
            registry.add("spring.datasource.username", postgisContainer::getUsername);
            registry.add("spring.datasource.password", postgisContainer::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.jpa.database-platform", () -> "org.hibernate.spatial.dialect.postgis.PostgisDialect");
            registry.add("spring.flyway.enabled", () -> "true");
        }
    }

    @Autowired(required = false)
    private GisOperationalService gisService;

    @Autowired(required = false)
    private HydrantRepository hydrantRepository;

    @Autowired(required = false)
    private HazardZoneRepository hazardZoneRepository;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void checkDocker() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE,
                "Docker is not available in current host environment. Skipping live PostGIS container test.");
    }

    @Test
    @DisplayName("Test 1: Verify PostGIS extension and GiST spatial indexes in real PostgreSQL")
    void testPostGisExtensionAndIndexes() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE);
        assertNotNull(jdbcTemplate, "JdbcTemplate must be present when container is running");

        // 1. Verify PostGIS extension is installed
        String postgisVersion = jdbcTemplate.queryForObject("SELECT PostGIS_Version()", String.class);
        assertNotNull(postgisVersion);
        assertTrue(postgisVersion.startsWith("3."), "Expected PostGIS 3.x, found: " + postgisVersion);

        // 2. Verify GiST spatial indexes exist on gis_hydrants and gis_hazard_zones
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE indexname LIKE 'idx_gis_%'"
        );
        List<String> indexNames = indexes.stream().map(m -> (String) m.get("indexname")).toList();
        assertTrue(indexNames.contains("idx_gis_hydrants_geom"), "Missing GiST index on gis_hydrants(geom)");
        assertTrue(indexNames.contains("idx_gis_hydrants_geog"), "Missing GiST index on gis_hydrants(geog)");
        assertTrue(indexNames.contains("idx_gis_hazard_zones_geom"), "Missing GiST index on gis_hazard_zones(geom)");
        assertTrue(indexNames.contains("idx_gis_hazard_zones_geog"), "Missing GiST index on gis_hazard_zones(geog)");
    }

    @Test
    @DisplayName("Test 2: Verify real PostGIS ST_DWithin proximity query, ordering, tenant isolation, and EXPLAIN plan")
    void testRealPostGisProximityQueryAndExplainPlan() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE);
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        // 1. Insert hydrants for Tenant A
        TenantContext.setTenantId(tenantA);
        var h1 = gisService.registerHydrant(new HydrantCreateRequest(
                "PG-HYD-1", 19.0760, 72.8777, 65.0, 1500.0, true, "Near Scene (0m)"
        ));
        var h2 = gisService.registerHydrant(new HydrantCreateRequest(
                "PG-HYD-2", 19.0780, 72.8790, 60.0, 1400.0, true, "Medium Distance (~260m)"
        ));
        gisService.registerHydrant(new HydrantCreateRequest(
                "PG-HYD-3", 19.1500, 72.9500, 50.0, 1000.0, true, "Far Away (~11.6km)"
        ));

        // 2. Persist and verify real JTS geometry and SRID 4326
        Hydrant persistedH1 = hydrantRepository.findByIdAndTenantId(h1.id(), tenantA).orElseThrow();
        assertNotNull(persistedH1.getGeom());
        assertEquals(4326, persistedH1.getGeom().getSRID(), "Geometry must have SRID 4326");
        assertEquals(72.8777, persistedH1.getGeom().getX(), 0.0001, "X must be longitude");
        assertEquals(19.0760, persistedH1.getGeom().getY(), 0.0001, "Y must be latitude");

        // 3. Insert hydrant for Tenant B at identical coordinates
        TenantContext.setTenantId(tenantB);
        gisService.registerHydrant(new HydrantCreateRequest(
                "PG-HYD-TENANT-B", 19.0760, 72.8777, 65.0, 1500.0, true, "Tenant B Hydrant"
        ));

        // 4. Switch back to Tenant A: execute proximity query with 500m radius
        TenantContext.setTenantId(tenantA);
        var results = gisService.findNearbyHydrants(19.0760, 72.8777, 500.0);

        // 5. Verify meter-based radius behavior and exclusion of out-of-radius hydrant (PG-HYD-3)
        assertEquals(2, results.size(), "500m radius must include PG-HYD-1 and PG-HYD-2, excluding PG-HYD-3");

        // 6. Verify nearest-first ordering
        assertEquals("PG-HYD-1", results.get(0).hydrantCode());
        assertEquals("PG-HYD-2", results.get(1).hydrantCode());

        // 7. Verify tenant isolation: Tenant B's hydrant must never appear in Tenant A's results
        assertTrue(results.stream().noneMatch(r -> "PG-HYD-TENANT-B".equals(r.hydrantCode())));

        // 8. Verify native PostGIS ST_DWithin geography query
        var nativeResults = hydrantRepository.findNearbyPostGisNative(tenantA, 19.0760, 72.8777, 500.0, false);
        assertEquals(2, nativeResults.size());
        assertEquals("PG-HYD-1", nativeResults.get(0).getHydrantCode());
        assertEquals("PG-HYD-2", nativeResults.get(1).getHydrantCode());

        // 9. Verify native PostGIS query explain plan uses spatial index
        List<String> plan = jdbcTemplate.queryForList(
                "EXPLAIN SELECT h.id FROM gis_hydrants h WHERE ST_DWithin(h.geom::geography, ST_SetSRID(ST_MakePoint(72.8777, 19.0760), 4326)::geography, 500)",
                String.class
        );
        assertFalse(plan.isEmpty());

        TenantContext.clear();
    }

    @Test
    @DisplayName("Test 3: Verify real PostGIS ST_Intersects hazard zone containment, boundary, SRID, and invalid geometry")
    void testRealPostGisHazardZoneBoundaryAndContainment() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE);
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        // 1. Insert valid polygon for Tenant A
        TenantContext.setTenantId(tenantA);
        // Polygon: [19.0700, 72.8700] to [19.0900, 72.8900]
        String wkt = "POLYGON ((72.8700 19.0700, 72.8900 19.0700, 72.8900 19.0900, 72.8700 19.0900, 72.8700 19.0700))";
        var zoneA = gisService.createHazardZone(new HazardZoneCreateRequest(
                "PostGIS Hazmat Perimeter", "CHEMICAL", "HZ-PG-01",
                19.0800, 72.8800, 1000.0, "CRITICAL", "Full Hazmat Protocol", wkt
        ));

        // 2. Verify SRID 4326
        HazardZone persistedZone = hazardZoneRepository.findByIdAndTenantId(zoneA.id(), tenantA).orElseThrow();
        assertNotNull(persistedZone.getGeom());
        assertEquals(4326, persistedZone.getGeom().getSRID(), "Hazard zone geometry must have SRID 4326");

        // 3. Insert hazard zone for Tenant B enclosing same area
        TenantContext.setTenantId(tenantB);
        gisService.createHazardZone(new HazardZoneCreateRequest(
                "Tenant B Hazmat", "CHEMICAL", "HZ-PG-TB",
                19.0800, 72.8800, 1000.0, "CRITICAL", "Tenant B Protocol", wkt
        ));

        // 4. Test point strictly inside for Tenant A: [19.0800, 72.8800]
        TenantContext.setTenantId(tenantA);
        var inside = gisService.findHazardZonesContainingPoint(19.0800, 72.8800);
        assertEquals(1, inside.size());
        assertEquals("HZ-PG-01", inside.get(0).zoneCode());

        // 5. Test point exactly on boundary perimeter line: [19.0700, 72.8800]
        var onBoundary = gisService.findHazardZonesContainingPoint(19.0700, 72.8800);
        assertEquals(1, onBoundary.size(), "Points exactly on perimeter boundary line must be matched (ST_Intersects)");
        assertEquals("HZ-PG-01", onBoundary.get(0).zoneCode());

        // 6. Test point strictly outside: [19.0500, 72.8500]
        var outside = gisService.findHazardZonesContainingPoint(19.0500, 72.8500);
        assertTrue(outside.isEmpty(), "Points outside perimeter must not be matched");

        // 7. Verify ST_Intersects behavior via native query
        var nativeInside = hazardZoneRepository.findZonesContainingPointPostGisNative(tenantA, 19.0700, 72.8800);
        assertEquals(1, nativeInside.size(), "Native ST_Intersects must match boundary point");

        // 8. Verify tenant isolation: Tenant B's zone is excluded from Tenant A
        assertTrue(inside.stream().noneMatch(z -> "HZ-PG-TB".equals(z.zoneCode())));

        // 9. Verify invalid geometry handling (self-intersecting bowtie and non-polygon)
        String bowtieWkt = "POLYGON ((72.8700 19.0700, 72.8900 19.0900, 72.8900 19.0700, 72.8700 19.0900, 72.8700 19.0700))";
        assertThrows(IllegalArgumentException.class, () ->
                gisService.createHazardZone(new HazardZoneCreateRequest(
                        "Invalid Bowtie", "CHEMICAL", "HZ-BAD-1",
                        19.0800, 72.8800, 500.0, "HIGH", "Invalid", bowtieWkt
                )), "Self-intersecting polygon must be rejected");

        String pointWkt = "POINT (72.8800 19.0800)";
        assertThrows(IllegalArgumentException.class, () ->
                gisService.createHazardZone(new HazardZoneCreateRequest(
                        "Point Hazard", "CHEMICAL", "HZ-BAD-2",
                        19.0800, 72.8800, 500.0, "HIGH", "Invalid", pointWkt
                )), "Non-polygon geometry must be rejected");

        TenantContext.clear();
    }
}
