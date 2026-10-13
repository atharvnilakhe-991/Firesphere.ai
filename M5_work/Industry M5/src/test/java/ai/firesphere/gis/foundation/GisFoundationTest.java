package ai.firesphere.gis.foundation;

import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.gis.foundation.display.MapDisplayDTO;
import ai.firesphere.gis.foundation.display.MapDisplayService;
import ai.firesphere.gis.foundation.display.MapProviderStatus;
import ai.firesphere.gis.foundation.dto.LocationValidationRequest;
import ai.firesphere.gis.foundation.dto.MapDisplayRequest;
import ai.firesphere.gis.foundation.dto.NearbyInfrastructureRequest;
import ai.firesphere.gis.foundation.dto.NavigationHandoffRequest;
import ai.firesphere.gis.foundation.infrastructure.InfrastructureItemDTO;
import ai.firesphere.gis.foundation.infrastructure.NearbyInfrastructureDTO;
import ai.firesphere.gis.foundation.infrastructure.NearbyInfrastructureService;
import ai.firesphere.gis.foundation.model.*;
import ai.firesphere.gis.foundation.navigation.NavigationHandoffService;
import ai.firesphere.gis.foundation.navigation.NavigationHandoffStatus;
import ai.firesphere.gis.foundation.navigation.OfficerNavigationHandoffDTO;
import ai.firesphere.gis.foundation.service.GisFoundationService;
import ai.firesphere.gis.foundation.service.GisFoundationServiceImpl;
import ai.firesphere.gis.foundation.validation.GpsCoordinateValidator;
import ai.firesphere.gis.model.Hydrant;
import ai.firesphere.gis.repository.HydrantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class GisFoundationTest {

    private GpsCoordinateValidator validator;
    private MapDisplayService mapDisplayService;
    private HydrantRepository mockHydrantRepository;
    private NearbyInfrastructureService infrastructureService;
    private NavigationHandoffService navigationService;
    private GisFoundationService foundationService;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantId);
        validator = new GpsCoordinateValidator();
        mapDisplayService = new MapDisplayService(validator);
        mockHydrantRepository = Mockito.mock(HydrantRepository.class);
        infrastructureService = new NearbyInfrastructureService(mockHydrantRepository, validator);
        navigationService = new NavigationHandoffService(validator);
        foundationService = new GisFoundationServiceImpl(
                validator,
                mapDisplayService,
                infrastructureService,
                navigationService
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        mapDisplayService.setSimulatedProviderUnavailable(false);
    }

    // =========================================================================
    // 1. Valid Coordinates Tests
    // =========================================================================

    @Test
    @DisplayName("Validation 1: Valid coordinates within legal ranges pass with HIGH accuracy")
    void testValidCoordinatesSuccess() {
        LocationValidationResult result = foundationService.validateCoordinates(12.9716, 77.5946, 5.0);

        assertThat(result.valid()).isTrue();
        assertThat(result.status()).isEqualTo(LocationValidationStatus.VALID);
        assertThat(result.accuracyLevel()).isEqualTo(LocationAccuracyLevel.HIGH);
        assertThat(result.latitude()).isEqualTo(12.9716);
        assertThat(result.longitude()).isEqualTo(77.5946);
        assertThat(result.validationErrors()).isEmpty();
    }

    @Test
    @DisplayName("Validation 1b: Boundary coordinates (-90, 90, -180, 180) are accepted")
    void testBoundaryCoordinatesAccepted() {
        assertThat(validator.validate(90.0, 180.0, 10.0).valid()).isTrue();
        assertThat(validator.validate(-90.0, -180.0, 10.0).valid()).isTrue();
        assertThat(validator.validate(0.0, 0.0, 10.0).valid()).isTrue();
    }

    // =========================================================================
    // 2. Invalid Latitude Tests
    // =========================================================================

    @Test
    @DisplayName("Validation 2: Invalid latitude (> 90.0 or < -90.0) returns clear error")
    void testInvalidLatitudeFails() {
        LocationValidationResult resultTooHigh = foundationService.validateCoordinates(90.0001, 77.5946, 10.0);
        assertThat(resultTooHigh.valid()).isFalse();
        assertThat(resultTooHigh.status()).isEqualTo(LocationValidationStatus.INVALID);
        assertThat(resultTooHigh.validationErrors()).anyMatch(msg -> msg.contains("Latitude 90.000100 is out of bounds"));

        LocationValidationResult resultTooLow = foundationService.validateCoordinates(-95.5, 77.5946, 10.0);
        assertThat(resultTooLow.valid()).isFalse();
        assertThat(resultTooLow.validationErrors()).anyMatch(msg -> msg.contains("Latitude -95.500000 is out of bounds"));
    }

    // =========================================================================
    // 3. Invalid Longitude Tests
    // =========================================================================

    @Test
    @DisplayName("Validation 3: Invalid longitude (> 180.0 or < -180.0) returns clear error")
    void testInvalidLongitudeFails() {
        LocationValidationResult resultTooHigh = foundationService.validateCoordinates(12.9716, 180.5, 10.0);
        assertThat(resultTooHigh.valid()).isFalse();
        assertThat(resultTooHigh.status()).isEqualTo(LocationValidationStatus.INVALID);
        assertThat(resultTooHigh.validationErrors()).anyMatch(msg -> msg.contains("Longitude 180.500000 is out of bounds"));

        LocationValidationResult resultTooLow = foundationService.validateCoordinates(12.9716, -185.0, 10.0);
        assertThat(resultTooLow.valid()).isFalse();
        assertThat(resultTooLow.validationErrors()).anyMatch(msg -> msg.contains("Longitude -185.000000 is out of bounds"));
    }

    // =========================================================================
    // 4. Missing Coordinates Tests
    // =========================================================================

    @Test
    @DisplayName("Validation 4: Missing coordinates (null latitude or null longitude) fail validation")
    void testMissingCoordinatesFail() {
        LocationValidationResult nullLat = foundationService.validateCoordinates(null, 77.5946, 10.0);
        assertThat(nullLat.valid()).isFalse();
        assertThat(nullLat.validationErrors()).contains("Latitude is missing or null");

        LocationValidationResult nullLon = foundationService.validateCoordinates(12.9716, null, 10.0);
        assertThat(nullLon.valid()).isFalse();
        assertThat(nullLon.validationErrors()).contains("Longitude is missing or null");

        LocationValidationResult bothNull = foundationService.validateCoordinates(null, null, 10.0);
        assertThat(bothNull.valid()).isFalse();
        assertThat(bothNull.validationErrors()).hasSize(2);
    }

    @Test
    @DisplayName("Validation 4b: Malformed coordinates (NaN or Infinite) fail validation")
    void testMalformedCoordinatesFail() {
        LocationValidationResult nanLat = foundationService.validateCoordinates(Double.NaN, 77.5946, 10.0);
        assertThat(nanLat.valid()).isFalse();
        assertThat(nanLat.validationErrors()).contains("Latitude is malformed (NaN or Infinite)");

        LocationValidationResult infLon = foundationService.validateCoordinates(12.9716, Double.POSITIVE_INFINITY, 10.0);
        assertThat(infLon.valid()).isFalse();
        assertThat(infLon.validationErrors()).contains("Longitude is malformed (NaN or Infinite)");
    }

    // =========================================================================
    // 5. Location Accuracy Handling
    // =========================================================================

    @Test
    @DisplayName("Validation 5: Accuracy telemetry categorizes correctly into HIGH, MODERATE, POOR, and UNVERIFIED")
    void testLocationAccuracyHandling() {
        // High accuracy (<= 15m)
        LocationValidationResult high = validator.validate(12.9716, 77.5946, 12.0);
        assertThat(high.status()).isEqualTo(LocationValidationStatus.VALID);
        assertThat(high.accuracyLevel()).isEqualTo(LocationAccuracyLevel.HIGH);

        // Moderate accuracy (15m - 50m)
        LocationValidationResult mod = validator.validate(12.9716, 77.5946, 35.0);
        assertThat(mod.status()).isEqualTo(LocationValidationStatus.VALID);
        assertThat(mod.accuracyLevel()).isEqualTo(LocationAccuracyLevel.MODERATE);

        // Coarse accuracy (> 50m) marked as APPROXIMATE
        LocationValidationResult coarse = validator.validate(12.9716, 77.5946, 75.0);
        assertThat(coarse.status()).isEqualTo(LocationValidationStatus.APPROXIMATE);
        assertThat(coarse.accuracyLevel()).isEqualTo(LocationAccuracyLevel.POOR);

        // Excessive accuracy error (> 200m) marked as POOR_ACCURACY
        LocationValidationResult excessive = validator.validate(12.9716, 77.5946, 350.0);
        assertThat(excessive.status()).isEqualTo(LocationValidationStatus.POOR_ACCURACY);
        assertThat(excessive.validationErrors()).anyMatch(msg -> msg.contains("exceeds tactical safety limit"));

        // Missing accuracy (null) flagged as UNVERIFIED
        LocationValidationResult unverified = validator.validate(12.9716, 77.5946, null);
        assertThat(unverified.status()).isEqualTo(LocationValidationStatus.UNVERIFIED);
        assertThat(unverified.accuracyLevel()).isEqualTo(LocationAccuracyLevel.UNKNOWN);
    }

    // =========================================================================
    // 6. Map Display Using Mock Data
    // =========================================================================

    @Test
    @DisplayName("Map Display 6: Successful map display generation in mock mode")
    void testMapDisplayGenerationSuccess() {
        MapDisplayRequest request = new MapDisplayRequest(
                "PROP-BLDG-101",
                LocationReferenceType.PROPERTY,
                12.9716,
                77.5946,
                "MG Road Commercial Arcade",
                8.5
        );

        MapDisplayDTO display = foundationService.getMapDisplay(request);

        assertThat(display).isNotNull();
        assertThat(display.centerLatitude()).isEqualTo(12.9716);
        assertThat(display.centerLongitude()).isEqualTo(77.5946);
        assertThat(display.zoomLevel()).isEqualTo(16);
        assertThat(display.providerStatus()).isEqualTo(MapProviderStatus.MOCK_MODE);
        assertThat(display.offlineCapable()).isFalse(); // Strictly per requirement: no false offline claims
        assertThat(display.markers()).hasSize(1);
        assertThat(display.markers().get(0).referenceId()).isEqualTo("PROP-BLDG-101");
        assertThat(display.markers().get(0).statusColor()).isEqualTo("#38A169"); // Green for verified property
        assertThat(display.boundingBox()).isNotNull();
        assertThat(display.boundingBox().minLatitude()).isLessThan(12.9716);
        assertThat(display.boundingBox().maxLatitude()).isGreaterThan(12.9716);
    }

    // =========================================================================
    // 7. Nearby Fire Infrastructure & Empty Results
    // =========================================================================

    @Test
    @DisplayName("Infrastructure 7: Query hydrants returns nearest hydrants sorted by proximity")
    void testNearbyInfrastructureQuerySuccess() {
        Hydrant h1 = new Hydrant();
        h1.setId(UUID.randomUUID());
        h1.setHydrantCode("HYD-01");
        h1.setLatitude(12.9720);
        h1.setLongitude(77.5950);
        h1.setOperable(true);
        h1.setPressurePsi(65.0);
        h1.setFlowRateLpm(1800.0);
        h1.setAddress("Gate 1 Front");

        Hydrant h2 = new Hydrant();
        h2.setId(UUID.randomUUID());
        h2.setHydrantCode("HYD-02");
        h2.setLatitude(12.9740);
        h2.setLongitude(77.5970);
        h2.setOperable(true);
        h2.setPressurePsi(58.0);
        h2.setFlowRateLpm(1500.0);

        when(mockHydrantRepository.findAllByTenantIdAndOperable(tenantId, true))
                .thenReturn(List.of(h1, h2));

        NearbyInfrastructureRequest request = new NearbyInfrastructureRequest(
                12.9716, 77.5946, 1000.0, true
        );

        NearbyInfrastructureDTO result = foundationService.getNearbyInfrastructure(request);

        assertThat(result.totalFound()).isEqualTo(2);
        assertThat(result.nearestDistanceMeters()).isNotNull();
        assertThat(result.items().get(0).code()).isEqualTo("HYD-01");
        assertThat(result.items().get(0).distanceMeters()).isLessThan(result.items().get(1).distanceMeters());
    }

    @Test
    @DisplayName("Infrastructure 7b: Empty results handled gracefully without throwing exceptions")
    void testNearbyInfrastructureEmptyResults() {
        when(mockHydrantRepository.findAllByTenantIdAndOperable(tenantId, true))
                .thenReturn(List.of());

        NearbyInfrastructureRequest request = new NearbyInfrastructureRequest(
                12.9716, 77.5946, 500.0, true
        );

        NearbyInfrastructureDTO result = foundationService.getNearbyInfrastructure(request);

        assertThat(result).isNotNull();
        assertThat(result.totalFound()).isEqualTo(0);
        assertThat(result.nearestDistanceMeters()).isNull();
        assertThat(result.items()).isEmpty();
    }

    // =========================================================================
    // 8. Officer Navigation Handoff
    // =========================================================================

    @Test
    @DisplayName("Navigation 8: Prepares provider-neutral navigation handoff (RFC-5870, Google, Apple, Waze)")
    void testOfficerNavigationHandoffSuccess() {
        NavigationHandoffRequest request = new NavigationHandoffRequest(
                12.9716,
                77.5946,
                "City Mall Fire Standpipe",
                "104 Brigade Road"
        );

        OfficerNavigationHandoffDTO handoff = foundationService.prepareNavigationHandoff(request);

        assertThat(handoff.status()).isEqualTo(NavigationHandoffStatus.READY);
        assertThat(handoff.destinationLatitude()).isEqualTo(12.9716);
        assertThat(handoff.destinationLongitude()).isEqualTo(77.5946);
        assertThat(handoff.universalGeoUri()).startsWith("geo:12.971600,77.594600?q=12.971600,77.594600(");
        assertThat(handoff.googleMapsUrl()).contains("destination=12.971600,77.594600");
        assertThat(handoff.appleMapsUrl()).contains("daddr=12.971600,77.594600");
        assertThat(handoff.wazeUrl()).contains("ll=12.971600,77.594600");
    }

    @Test
    @DisplayName("Navigation 8b: Invalid destination coordinates return descriptive error")
    void testOfficerNavigationHandoffInvalidDestination() {
        NavigationHandoffRequest requestBadLat = new NavigationHandoffRequest(
                125.0, 77.5946, "Site", "Address"
        );
        OfficerNavigationHandoffDTO handoff = foundationService.prepareNavigationHandoff(requestBadLat);

        assertThat(handoff.status()).isEqualTo(NavigationHandoffStatus.INVALID_DESTINATION);
        assertThat(handoff.statusMessage()).contains("Latitude 125.000000 is out of bounds");
        assertThat(handoff.universalGeoUri()).isNull();
    }

    @Test
    @DisplayName("Navigation 8c: Missing destination coordinates return MISSING_COORDINATES error")
    void testOfficerNavigationHandoffMissingDestination() {
        NavigationHandoffRequest requestNull = new NavigationHandoffRequest(
                null, 77.5946, "Site", "Address"
        );
        OfficerNavigationHandoffDTO handoff = foundationService.prepareNavigationHandoff(requestNull);

        assertThat(handoff.status()).isEqualTo(NavigationHandoffStatus.MISSING_COORDINATES);
        assertThat(handoff.statusMessage()).contains("Destination latitude and longitude must both be provided");
    }

    // =========================================================================
    // 9. Map Provider Unavailable
    // =========================================================================

    @Test
    @DisplayName("Provider 9: Map provider unavailable fails gracefully with fallback message")
    void testMapProviderUnavailableGracefulFallback() {
        mapDisplayService.setSimulatedProviderUnavailable(true);

        MapDisplayRequest request = new MapDisplayRequest(
                "PROP-OFFLINE-01",
                LocationReferenceType.PROPERTY,
                12.9716,
                77.5946,
                "Substation 4",
                5.0
        );

        MapDisplayDTO display = foundationService.getMapDisplay(request);

        assertThat(display.providerStatus()).isEqualTo(MapProviderStatus.UNAVAILABLE);
        assertThat(display.statusMessage()).contains("Map visualization provider is currently unreachable");
        assertThat(display.markers()).isEmpty();
    }

    // =========================================================================
    // 10. Failure and Offline Behavior
    // =========================================================================

    @Test
    @DisplayName("Failure 10: Invalid coordinates in map display request throws clear IllegalArgumentException")
    void testMapDisplayInvalidCoordinatesRejection() {
        MapDisplayRequest request = new MapDisplayRequest(
                "PROP-BAD-01",
                LocationReferenceType.PROPERTY,
                999.0, // invalid
                77.5946,
                "Invalid Coordinate Building",
                null
        );

        assertThatThrownBy(() -> foundationService.getMapDisplay(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Latitude 999.000000 is out of bounds");
    }

    @Test
    @DisplayName("Failure 10b: Nearby infrastructure with invalid radius throws clear IllegalArgumentException")
    void testNearbyInfrastructureInvalidRadiusRejection() {
        NearbyInfrastructureRequest badRadius = new NearbyInfrastructureRequest(
                12.9716, 77.5946, -100.0, true
        );

        // When negative radius is provided, constructor defaults to 500.0 or throws if explicitly passed <= 0 to service
        assertThatThrownBy(() -> infrastructureService.findNearby(12.9716, 77.5946, -50.0, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("radius must be a positive non-zero number");
    }
}
