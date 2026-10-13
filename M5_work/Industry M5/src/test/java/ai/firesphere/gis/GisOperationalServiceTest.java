package ai.firesphere.gis;

import ai.firesphere.common.exception.ConflictException;
import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.contracts.gis.MapRoutingProviderContract;
import ai.firesphere.contracts.mock.MockMapProvider;
import ai.firesphere.gis.dto.*;
import ai.firesphere.gis.model.HazardZone;
import ai.firesphere.gis.model.Hydrant;
import ai.firesphere.gis.repository.HazardZoneRepository;
import ai.firesphere.gis.repository.HydrantRepository;
import ai.firesphere.gis.service.GisOperationalService;
import ai.firesphere.gis.service.GisOperationalServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GisOperationalServiceTest {

    private HydrantRepository hydrantRepository;
    private HazardZoneRepository hazardZoneRepository;
    private MapRoutingProviderContract mapRoutingProvider;
    private GisOperationalService gisService;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        hydrantRepository = mock(HydrantRepository.class);
        hazardZoneRepository = mock(HazardZoneRepository.class);
        mapRoutingProvider = new MockMapProvider();
        gisService = new GisOperationalServiceImpl(hydrantRepository, hazardZoneRepository, mapRoutingProvider);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ========================================================================
    // HYDRANT TESTS
    // ========================================================================

    @Test
    void testRegisterHydrant_Success() {
        HydrantCreateRequest request = new HydrantCreateRequest(
                "HYD-NORTH-01", 19.0760, 72.8777, 65.0, 1500.0, true, "Sector 4 Industrial Park"
        );

        when(hydrantRepository.findByHydrantCodeAndTenantId("HYD-NORTH-01", tenantId)).thenReturn(Optional.empty());
        when(hydrantRepository.save(any(Hydrant.class))).thenAnswer(invocation -> {
            Hydrant h = invocation.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });

        HydrantResponseDTO response = gisService.registerHydrant(request);

        assertNotNull(response);
        assertEquals("HYD-NORTH-01", response.hydrantCode());
        assertEquals(19.0760, response.latitude());
        assertEquals(72.8777, response.longitude());
        assertTrue(response.operable());
        verify(hydrantRepository, times(1)).save(any(Hydrant.class));
    }

    @Test
    void testRegisterHydrant_DuplicateCodeThrowsConflict() {
        HydrantCreateRequest request = new HydrantCreateRequest(
                "HYD-DUP-01", 19.0760, 72.8777, 60.0, 1200.0, true, "Gate 1"
        );

        when(hydrantRepository.findByHydrantCodeAndTenantId("HYD-DUP-01", tenantId))
                .thenReturn(Optional.of(new Hydrant()));

        assertThrows(ConflictException.class, () -> gisService.registerHydrant(request));
        verify(hydrantRepository, never()).save(any(Hydrant.class));
    }

    @Test
    void testRegisterHydrant_InvalidCoordinatesThrowsIllegalArgument() {
        HydrantCreateRequest invalidLat = new HydrantCreateRequest(
                "HYD-INV-01", 95.0, 72.8777, 60.0, 1200.0, true, "Gate 1"
        );
        assertThrows(IllegalArgumentException.class, () -> gisService.registerHydrant(invalidLat));

        HydrantCreateRequest invalidLon = new HydrantCreateRequest(
                "HYD-INV-02", 19.0760, 185.0, 60.0, 1200.0, true, "Gate 1"
        );
        assertThrows(IllegalArgumentException.class, () -> gisService.registerHydrant(invalidLon));
    }

    @Test
    void testGetHydrantById_NotFoundThrowsException() {
        UUID unknownId = UUID.randomUUID();
        when(hydrantRepository.findByIdAndTenantId(unknownId, tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> gisService.getHydrantById(unknownId));
    }

    @Test
    void testGetHydrants_Paginated() {
        Pageable pageable = PageRequest.of(0, 10);
        Hydrant h = new Hydrant();
        h.setId(UUID.randomUUID());
        h.setHydrantCode("HYD-PAGE-01");
        h.setLatitude(19.0760);
        h.setLongitude(72.8777);

        when(hydrantRepository.findAllByTenantId(tenantId, pageable))
                .thenReturn(new PageImpl<>(List.of(h), pageable, 1));

        Page<HydrantResponseDTO> page = gisService.getHydrants(null, pageable);
        assertEquals(1, page.getTotalElements());
        assertEquals("HYD-PAGE-01", page.getContent().get(0).hydrantCode());
    }

    @Test
    void testUpdateHydrant_Success() {
        UUID id = UUID.randomUUID();
        Hydrant existing = new Hydrant();
        existing.setId(id);
        existing.setHydrantCode("HYD-UPD-01");
        existing.setLatitude(19.0760);
        existing.setLongitude(72.8777);

        when(hydrantRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(existing));
        when(hydrantRepository.save(any(Hydrant.class))).thenAnswer(i -> i.getArgument(0));

        HydrantUpdateRequest updateReq = new HydrantUpdateRequest(
                19.0800, 72.8850, 70.0, 1800.0, false, "Updated Address"
        );

        HydrantResponseDTO result = gisService.updateHydrant(id, updateReq);
        assertEquals(19.0800, result.latitude());
        assertEquals(72.8850, result.longitude());
        assertFalse(result.operable());
        assertEquals("Updated Address", result.address());
    }

    @Test
    void testFindNearbyHydrants_RadiusValidation() {
        // Radius <= 0
        assertThrows(IllegalArgumentException.class, () ->
                gisService.findNearbyHydrants(19.0760, 72.8777, -50.0));

        // Radius exceeds max ceiling (100 km)
        assertThrows(IllegalArgumentException.class, () ->
                gisService.findNearbyHydrants(19.0760, 72.8777, 150000.0));
    }

    // ========================================================================
    // HAZARD ZONE TESTS
    // ========================================================================

    @Test
    void testCreateHazardZone_CircularBufferSuccess() {
        HazardZoneCreateRequest request = new HazardZoneCreateRequest(
                "Refinery Fuel Depot", "CHEMICAL", "HZ-REF-01",
                19.0800, 72.8800, 500.0, "CRITICAL", "Full 1km Evacuation", null
        );

        when(hazardZoneRepository.findByZoneCodeAndTenantId("HZ-REF-01", tenantId)).thenReturn(Optional.empty());
        when(hazardZoneRepository.save(any(HazardZone.class))).thenAnswer(i -> {
            HazardZone z = i.getArgument(0);
            z.setId(UUID.randomUUID());
            return z;
        });

        HazardZoneResponseDTO response = gisService.createHazardZone(request);

        assertNotNull(response);
        assertEquals("HZ-REF-01", response.zoneCode());
        assertEquals("CHEMICAL", response.hazardType());
        assertTrue(response.active());
        assertNotNull(response.polygonWkt());
        assertTrue(response.polygonWkt().startsWith("POLYGON"));
    }

    @Test
    void testCreateHazardZone_ExplicitWktSuccess() {
        String wkt = "POLYGON ((72.87 19.07, 72.89 19.07, 72.89 19.09, 72.87 19.09, 72.87 19.07))";
        HazardZoneCreateRequest request = new HazardZoneCreateRequest(
                "Chemical Storage", "CHEMICAL", "HZ-WKT-01",
                19.0800, 72.8800, 500.0, "HIGH", "Level 2 Hazmat Protocol", wkt
        );

        when(hazardZoneRepository.findByZoneCodeAndTenantId("HZ-WKT-01", tenantId)).thenReturn(Optional.empty());
        when(hazardZoneRepository.save(any(HazardZone.class))).thenAnswer(i -> {
            HazardZone z = i.getArgument(0);
            z.setId(UUID.randomUUID());
            return z;
        });

        HazardZoneResponseDTO response = gisService.createHazardZone(request);
        assertNotNull(response);
        assertEquals("HZ-WKT-01", response.zoneCode());
        assertEquals(wkt, response.polygonWkt());
    }

    @Test
    void testCreateHazardZone_MalformedWktThrowsIllegalArgument() {
        HazardZoneCreateRequest malformed = new HazardZoneCreateRequest(
                "Bad Zone", "CHEMICAL", "HZ-BAD-01",
                19.0800, 72.8800, 500.0, "HIGH", null, "POLYGON ((72.87 19.07))"
        );
        assertThrows(IllegalArgumentException.class, () -> gisService.createHazardZone(malformed));
    }

    @Test
    void testFindHazardZonesContainingPoint() {
        HazardZone zone = new HazardZone();
        zone.setId(UUID.randomUUID());
        zone.setZoneCode("HZ-TEST-CONTAIN");
        zone.setHazardName("Hazmat Area");
        zone.setCenterLatitude(19.0800);
        zone.setCenterLongitude(72.8800);
        zone.setRadiusMeters(300.0);
        zone.setActive(true);

        when(hazardZoneRepository.findZonesContainingPoint(eq(tenantId), any(Point.class)))
                .thenReturn(List.of(zone));

        List<HazardZoneResponseDTO> result = gisService.findHazardZonesContainingPoint(19.0800, 72.8800);
        assertEquals(1, result.size());
        assertEquals("HZ-TEST-CONTAIN", result.get(0).zoneCode());
    }

    // ========================================================================
    // ROUTE CALCULATION (CONTRACT MOCK) TESTS
    // ========================================================================

    @Test
    void testCalculateEmergencyRoute_DeterministicBaseline() {
        RouteCalculationRequest request = new RouteCalculationRequest(
                19.0760, 72.8777, 19.0820, 72.8840
        );

        RouteCalculationResponse response = gisService.calculateEmergencyRoute(request);

        assertNotNull(response);
        assertTrue(response.distanceMeters() > 0.0);
        assertTrue(response.estimatedDurationSeconds() > 0);
        assertFalse(response.waypoints().isEmpty());
        assertNotNull(response.polyline());
    }

    @Test
    void testCalculateEmergencyRoute_InvalidCoordinatesThrowsIllegalArgument() {
        RouteCalculationRequest badStart = new RouteCalculationRequest(
                95.0, 72.8777, 19.0820, 72.8840
        );
        assertThrows(IllegalArgumentException.class, () -> gisService.calculateEmergencyRoute(badStart));

        RouteCalculationRequest badDest = new RouteCalculationRequest(
                19.0760, 72.8777, 19.0820, 190.0
        );
        assertThrows(IllegalArgumentException.class, () -> gisService.calculateEmergencyRoute(badDest));
    }
}
