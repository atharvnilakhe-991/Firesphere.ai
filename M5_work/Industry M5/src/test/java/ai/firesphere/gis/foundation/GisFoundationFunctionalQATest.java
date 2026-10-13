package ai.firesphere.gis.foundation;

import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.gis.foundation.display.MapDisplayDTO;
import ai.firesphere.gis.foundation.display.MapDisplayService;
import ai.firesphere.gis.foundation.display.MapProviderStatus;
import ai.firesphere.gis.foundation.dto.MapDisplayRequest;
import ai.firesphere.gis.foundation.infrastructure.InfrastructureItemDTO;
import ai.firesphere.gis.foundation.infrastructure.NearbyInfrastructureDTO;
import ai.firesphere.gis.foundation.infrastructure.NearbyInfrastructureService;
import ai.firesphere.gis.foundation.model.LocationAccuracyLevel;
import ai.firesphere.gis.foundation.model.LocationRecord;
import ai.firesphere.gis.foundation.model.LocationReferenceType;
import ai.firesphere.gis.foundation.model.LocationValidationResult;
import ai.firesphere.gis.foundation.model.LocationValidationStatus;
import ai.firesphere.gis.foundation.navigation.NavigationHandoffService;
import ai.firesphere.gis.foundation.navigation.NavigationHandoffStatus;
import ai.firesphere.gis.foundation.navigation.OfficerNavigationHandoffDTO;
import ai.firesphere.gis.foundation.validation.GpsCoordinateValidator;
import ai.firesphere.gis.model.Hydrant;
import ai.firesphere.gis.repository.HydrantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Functional QA Suite for GIS Foundation.
 * Verifies strict non-fabrication of coordinates, tenant isolation in spatial lookups,
 * boundary validations, empty results, map degraded states, and navigation handoff failures.
 */
class GisFoundationFunctionalQATest {

    private GpsCoordinateValidator validator;
    private HydrantRepository mockHydrantRepository;
    private NearbyInfrastructureService infrastructureService;
    private MapDisplayService mapDisplayService;
    private NavigationHandoffService navigationService;

    private final UUID tenant1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID tenant2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenant1);
        validator = new GpsCoordinateValidator();
        mockHydrantRepository = Mockito.mock(HydrantRepository.class);
        infrastructureService = new NearbyInfrastructureService(mockHydrantRepository, validator);
        mapDisplayService = new MapDisplayService(validator);
        navigationService = new NavigationHandoffService(validator);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        infrastructureService.clearFixtures();
    }

    @Test
    @DisplayName("Non-Fabrication Guarantee: Missing or invalid coordinates are NEVER replaced with defaults")
    void testStrictNonFabricationOfCoordinates() {
        // 1. Null latitude
        LocationValidationResult nullLat = validator.validate(null, 77.5946, 10.0);
        assertThat(nullLat.valid()).isFalse();
        assertThat(nullLat.latitude()).isNull();
        assertThat(nullLat.longitude()).isNull();
        assertThat(nullLat.status()).isEqualTo(LocationValidationStatus.INVALID);

        // 2. Out of bounds latitude (> 90.0)
        LocationValidationResult outLat = validator.validate(91.0, 77.5946, 10.0);
        assertThat(outLat.valid()).isFalse();
        assertThat(outLat.latitude()).isNull();
        assertThat(outLat.longitude()).isNull();

        // 3. Out of bounds longitude (< -180.0)
        LocationValidationResult outLon = validator.validate(12.9716, -181.0, 10.0);
        assertThat(outLon.valid()).isFalse();
        assertThat(outLon.latitude()).isNull();
        assertThat(outLon.longitude()).isNull();

        // 4. NaN / Infinite coordinates
        LocationValidationResult nanLat = validator.validate(Double.NaN, 77.5946, 10.0);
        assertThat(nanLat.valid()).isFalse();
        assertThat(nanLat.latitude()).isNull();

        LocationValidationResult infLon = validator.validate(12.9716, Double.POSITIVE_INFINITY, 10.0);
        assertThat(infLon.valid()).isFalse();
        assertThat(infLon.longitude()).isNull();
    }

    @Test
    @DisplayName("Tenant Isolation: Spatial infrastructure query strictly filters hydrants by authenticated tenant")
    void testNearbyInfrastructureTenantIsolation() {
        Hydrant hydrantTenant1 = new Hydrant();
        hydrantTenant1.setId(UUID.randomUUID());
        hydrantTenant1.setTenantId(tenant1);
        hydrantTenant1.setHydrantCode("HYD-T1-001");
        hydrantTenant1.setLatitude(12.9716);
        hydrantTenant1.setLongitude(77.5946);
        hydrantTenant1.setOperable(true);
        hydrantTenant1.setPressurePsi(65.0);
        hydrantTenant1.setFlowRateLpm(1200.0);

        Hydrant hydrantTenant2 = new Hydrant();
        hydrantTenant2.setId(UUID.randomUUID());
        hydrantTenant2.setTenantId(tenant2);
        hydrantTenant2.setHydrantCode("HYD-T2-002");
        hydrantTenant2.setLatitude(12.9716);
        hydrantTenant2.setLongitude(77.5946);
        hydrantTenant2.setOperable(true);

        when(mockHydrantRepository.findAllByTenantIdAndOperable(eq(tenant1), eq(true)))
                .thenReturn(List.of(hydrantTenant1));
        when(mockHydrantRepository.findAllByTenantIdAndOperable(eq(tenant2), eq(true)))
                .thenReturn(List.of(hydrantTenant2));

        // Query under Tenant 1: Must return ONLY Tenant 1's hydrant
        TenantContext.setTenantId(tenant1);
        NearbyInfrastructureDTO t1Results = infrastructureService.findNearby(12.9716, 77.5946, 500.0, true);
        assertThat(t1Results.totalFound()).isEqualTo(1);
        assertThat(t1Results.items().get(0).code()).isEqualTo("HYD-T1-001");

        // Query under Tenant 2: Must return ONLY Tenant 2's hydrant
        TenantContext.setTenantId(tenant2);
        NearbyInfrastructureDTO t2Results = infrastructureService.findNearby(12.9716, 77.5946, 500.0, true);
        assertThat(t2Results.totalFound()).isEqualTo(1);
        assertThat(t2Results.items().get(0).code()).isEqualTo("HYD-T2-002");
    }

    @Test
    @DisplayName("Empty Search Results: Returns safe empty structure without exceptions")
    void testNearbyInfrastructureEmptyResults() {
        when(mockHydrantRepository.findAllByTenantIdAndOperable(any(), any()))
                .thenReturn(List.of());

        NearbyInfrastructureDTO results = infrastructureService.findNearby(12.9716, 77.5946, 100.0, true);
        assertThat(results.totalFound()).isEqualTo(0);
        assertThat(results.nearestDistanceMeters()).isNull();
        assertThat(results.items()).isEmpty();
    }

    @Test
    @DisplayName("Radius Validation: Zero, negative, and invalid radius values are rejected")
    void testNearbyInfrastructureRadiusValidation() {
        assertThatThrownBy(() -> infrastructureService.findNearby(12.9716, 77.5946, 0.0, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive non-zero number");

        assertThatThrownBy(() -> infrastructureService.findNearby(12.9716, 77.5946, -50.0, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive non-zero number");

        assertThatThrownBy(() -> infrastructureService.findNearby(12.9716, 77.5946, Double.NaN, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Map Display Provider Status: Unconfigured provider reports MOCK_MODE, and degraded state reports UNAVAILABLE")
    void testMapDisplayProviderStatus() {
        LocationRecord location = LocationRecord.of(
                "PROP-TEST-01",
                LocationReferenceType.PROPERTY,
                12.9716,
                77.5946,
                "City Mall",
                15.0
        );

        // 1. Normal state -> MOCK_MODE
        MapDisplayDTO display = mapDisplayService.generateMapDisplay(location);
        assertThat(display).isNotNull();
        assertThat(display.providerStatus()).isEqualTo(MapProviderStatus.MOCK_MODE);
        assertThat(display.centerLatitude()).isEqualTo(12.9716);
        assertThat(display.centerLongitude()).isEqualTo(77.5946);
        assertThat(display.boundingBox()).isNotNull();

        // 2. Simulated degraded/unavailable state -> UNAVAILABLE
        mapDisplayService.setSimulatedProviderUnavailable(true);
        MapDisplayDTO unavailableDisplay = mapDisplayService.generateMapDisplay(location);
        assertThat(unavailableDisplay.providerStatus()).isEqualTo(MapProviderStatus.UNAVAILABLE);
        assertThat(unavailableDisplay.markers()).isEmpty();
        mapDisplayService.setSimulatedProviderUnavailable(false);
    }

    @Test
    @DisplayName("Navigation Handoff Failures: Missing and invalid coordinates fail with distinct status codes")
    void testNavigationHandoffFailures() {
        // Missing coordinates
        OfficerNavigationHandoffDTO missingCoords = navigationService.prepareHandoff(
                null, 77.5946, "Target Station", "Main St"
        );
        assertThat(missingCoords.status()).isEqualTo(NavigationHandoffStatus.MISSING_COORDINATES);
        assertThat(missingCoords.universalGeoUri()).isNull();

        // Invalid coordinates
        OfficerNavigationHandoffDTO invalidCoords = navigationService.prepareHandoff(
                95.0, 77.5946, "Target Station", "Main St"
        );
        assertThat(invalidCoords.status()).isEqualTo(NavigationHandoffStatus.INVALID_DESTINATION);
        assertThat(invalidCoords.statusMessage()).contains("out of bounds");

        // Null label defaults to "Incident Scene" gracefully
        OfficerNavigationHandoffDTO nullLabel = navigationService.prepareHandoff(
                12.9716, 77.5946, null, "Main St"
        );
        assertThat(nullLabel.status()).isEqualTo(NavigationHandoffStatus.READY);
        assertThat(nullLabel.destinationLabel()).isEqualTo("Incident Scene");
        assertThat(nullLabel.universalGeoUri()).contains("Incident+Scene");
    }
}
