package ai.firesphere.gis;

import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.gis.dto.*;
import ai.firesphere.gis.service.GisOperationalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GisSpatialProximityIntegrationTest {

    @Autowired
    private GisOperationalService gisService;

    private final UUID tenant1 = UUID.randomUUID();
    private final UUID tenant2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenant1);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testNearbyHydrants_DistanceOrderingAndRadiusExclusion() {
        TenantContext.setTenantId(tenant1);

        // Reference query center: [19.0760, 72.8777] (e.g. Incident Scene)
        // Hydrant A: ~0m away
        HydrantResponseDTO hydA = gisService.registerHydrant(new HydrantCreateRequest(
                "HYD-A-CLOSE", 19.0760, 72.8777, 65.0, 1500.0, true, "On-scene hydrant"
        ));

        // Hydrant B: ~260m away (lat +0.002, lon +0.0013)
        HydrantResponseDTO hydB = gisService.registerHydrant(new HydrantCreateRequest(
                "HYD-B-MEDIUM", 19.0780, 72.8790, 60.0, 1200.0, true, "260m away hydrant"
        ));

        // Hydrant C: ~11.5 km away (lat +0.074, lon +0.0723)
        HydrantResponseDTO hydC = gisService.registerHydrant(new HydrantCreateRequest(
                "HYD-C-FAR", 19.1500, 72.9500, 50.0, 1000.0, true, "11km away hydrant"
        ));

        // Query with 1000m radius
        List<HydrantResponseDTO> nearby = gisService.findNearbyHydrants(19.0760, 72.8777, 1000.0);

        // Must return A and B, exclude C
        assertEquals(2, nearby.size(), "Should return exactly 2 hydrants within 1000m radius");
        assertEquals("HYD-A-CLOSE", nearby.get(0).hydrantCode());
        assertEquals("HYD-B-MEDIUM", nearby.get(1).hydrantCode());

        // Assert distance ordering: A distance < B distance
        assertTrue(nearby.get(0).distanceMeters() < nearby.get(1).distanceMeters());
        assertTrue(nearby.get(0).distanceMeters() < 10.0);
        assertTrue(nearby.get(1).distanceMeters() > 200.0 && nearby.get(1).distanceMeters() < 350.0);
    }

    @Test
    void testNearbyHydrants_OperabilityFilter() {
        TenantContext.setTenantId(tenant1);

        gisService.registerHydrant(new HydrantCreateRequest(
                "HYD-OPERABLE", 19.0760, 72.8777, 65.0, 1500.0, true, "Operational"
        ));
        gisService.registerHydrant(new HydrantCreateRequest(
                "HYD-BROKEN", 19.0765, 72.8778, 0.0, 0.0, false, "Damaged stem"
        ));

        // Query all nearby
        List<HydrantResponseDTO> allNearby = gisService.findNearbyHydrants(19.0760, 72.8777, 500.0, false);
        assertEquals(2, allNearby.size());

        // Query operable only
        List<HydrantResponseDTO> operableOnly = gisService.findNearbyHydrants(19.0760, 72.8777, 500.0, true);
        assertEquals(1, operableOnly.size());
        assertEquals("HYD-OPERABLE", operableOnly.get(0).hydrantCode());
    }

    @Test
    void testTenantIsolation_HydrantsAndProximity() {
        // Register under Tenant 1
        TenantContext.setTenantId(tenant1);
        HydrantResponseDTO t1Hydrant = gisService.registerHydrant(new HydrantCreateRequest(
                "HYD-TENANT-1", 19.0760, 72.8777, 65.0, 1500.0, true, "Tenant 1 Facility"
        ));

        // Switch to Tenant 2
        TenantContext.setTenantId(tenant2);
        List<HydrantResponseDTO> t2Nearby = gisService.findNearbyHydrants(19.0760, 72.8777, 1000.0);
        assertTrue(t2Nearby.isEmpty(), "Tenant 2 proximity query must not return Tenant 1 hydrants");

        assertThrows(ResourceNotFoundException.class, () -> gisService.getHydrantById(t1Hydrant.id()),
                "Tenant 2 direct lookup of Tenant 1 hydrant must throw 404");
    }

    @Test
    void testHazardZone_PointInZoneContainment() {
        TenantContext.setTenantId(tenant1);

        // Bounding polygon around 19.0800, 72.8800 (Refinery Zone)
        String polygonWkt = "POLYGON ((72.8700 19.0700, 72.8900 19.0700, 72.8900 19.0900, 72.8700 19.0900, 72.8700 19.0700))";
        gisService.createHazardZone(new HazardZoneCreateRequest(
                "Industrial Hazmat Perimeter", "CHEMICAL", "HZ-IND-01",
                19.0800, 72.8800, 1000.0, "CRITICAL", "Full Hazmat Suit Required", polygonWkt
        ));

        // Point inside: [19.0820, 72.8810]
        List<HazardZoneResponseDTO> insideZones = gisService.findHazardZonesContainingPoint(19.0820, 72.8810);
        assertEquals(1, insideZones.size());
        assertEquals("HZ-IND-01", insideZones.get(0).zoneCode());

        // Point outside: [19.0600, 72.8600]
        List<HazardZoneResponseDTO> outsideZones = gisService.findHazardZonesContainingPoint(19.0600, 72.8600);
        assertTrue(outsideZones.isEmpty(), "Coordinates outside polygon must not be contained");
    }

    @Test
    void testHazardZone_CircularBufferContainment() {
        TenantContext.setTenantId(tenant1);

        // Circular hazard zone of radius 500m centered at [19.1000, 72.9000]
        gisService.createHazardZone(new HazardZoneCreateRequest(
                "LPG Cylinder Depot", "LPG_STORAGE", "HZ-LPG-01",
                19.1000, 72.9000, 500.0, "HIGH", "Water mist barrier", null
        ));

        // Center point -> inside
        List<HazardZoneResponseDTO> centerMatch = gisService.findHazardZonesContainingPoint(19.1000, 72.9000);
        assertEquals(1, centerMatch.size());

        // Point ~150m away -> inside
        List<HazardZoneResponseDTO> nearMatch = gisService.findHazardZonesContainingPoint(19.1010, 72.9005);
        assertEquals(1, nearMatch.size());

        // Point ~3km away -> outside
        List<HazardZoneResponseDTO> farMatch = gisService.findHazardZonesContainingPoint(19.1300, 72.9300);
        assertTrue(farMatch.isEmpty());
    }

    @Test
    void testTenantIsolation_HazardZones() {
        TenantContext.setTenantId(tenant1);
        HazardZoneResponseDTO zone = gisService.createHazardZone(new HazardZoneCreateRequest(
                "Secret Chemical Lab", "CHEMICAL", "HZ-LAB-T1",
                19.0800, 72.8800, 500.0, "HIGH", "Evacuate", null
        ));

        // Switch to Tenant 2
        TenantContext.setTenantId(tenant2);
        List<HazardZoneResponseDTO> contained = gisService.findHazardZonesContainingPoint(19.0800, 72.8800);
        assertTrue(contained.isEmpty(), "Tenant 2 containment query must not find Tenant 1 hazard zones");

        assertThrows(ResourceNotFoundException.class, () -> gisService.getHazardZoneById(zone.id()));
    }

    @Test
    void testHazardZone_BoundaryPointContainmentAndInvalidGeometryRejection() {
        TenantContext.setTenantId(tenant1);

        // Bounding polygon from [19.0700, 72.8700] to [19.0900, 72.8900]
        String polygonWkt = "POLYGON ((72.8700 19.0700, 72.8900 19.0700, 72.8900 19.0900, 72.8700 19.0900, 72.8700 19.0700))";
        gisService.createHazardZone(new HazardZoneCreateRequest(
                "Refinery Perimeter Fence", "CHEMICAL", "HZ-BOUNDARY-01",
                19.0800, 72.8800, 1000.0, "CRITICAL", "Boundary Protocol", polygonWkt
        ));

        // Point exactly on the southern boundary segment [lat = 19.0700, lon = 72.8800]
        List<HazardZoneResponseDTO> boundaryMatches = gisService.findHazardZonesContainingPoint(19.0700, 72.8800);
        assertEquals(1, boundaryMatches.size(), "Point on perimeter boundary must be matched via ST_Intersects");
        assertEquals("HZ-BOUNDARY-01", boundaryMatches.get(0).zoneCode());

        // Self-intersecting / bowtie polygon -> must be rejected
        String bowtieWkt = "POLYGON ((72.87 19.07, 72.89 19.09, 72.89 19.07, 72.87 19.09, 72.87 19.07))";
        assertThrows(IllegalArgumentException.class, () ->
                gisService.createHazardZone(new HazardZoneCreateRequest(
                        "Malformed Bowtie", "CHEMICAL", "HZ-BOWTIE",
                        19.0800, 72.8800, 500.0, "HIGH", null, bowtieWkt
                )), "Self-intersecting geometry must be rejected");

        // Point geometry supplied instead of Polygon/MultiPolygon -> must be rejected
        String pointWkt = "POINT (72.8800 19.0800)";
        assertThrows(IllegalArgumentException.class, () ->
                gisService.createHazardZone(new HazardZoneCreateRequest(
                        "Point Zone", "CHEMICAL", "HZ-POINT",
                        19.0800, 72.8800, 500.0, "HIGH", null, pointWkt
                )), "Non-polygon geometry type must be rejected");
    }

    @Test
    void testHydrant_CoordinateConsistencyAndSrid() {
        TenantContext.setTenantId(tenant1);

        HydrantResponseDTO created = gisService.registerHydrant(new HydrantCreateRequest(
                "HYD-SYNC-TEST", 19.0760, 72.8777, 65.0, 1500.0, true, "Original Position"
        ));

        assertEquals(19.0760, created.latitude());
        assertEquals(72.8777, created.longitude());

        // Update coordinates
        HydrantResponseDTO updated = gisService.updateHydrant(created.id(), new HydrantUpdateRequest(
                19.0810, 72.8820, 70.0, 1600.0, true, "Relocated Valve"
        ));

        assertEquals(19.0810, updated.latitude(), "Latitude must immediately update");
        assertEquals(72.8820, updated.longitude(), "Longitude must immediately update");

        // Verify retrieval matches updated coordinates
        HydrantResponseDTO reloaded = gisService.getHydrantById(created.id());
        assertEquals(19.0810, reloaded.latitude());
        assertEquals(72.8820, reloaded.longitude());
        assertEquals("Relocated Valve", reloaded.address());
    }
}
