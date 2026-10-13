package ai.firesphere.mobile;

import ai.firesphere.gis.foundation.dto.LocationValidationRequest;
import ai.firesphere.gis.foundation.dto.MapDisplayRequest;
import ai.firesphere.gis.foundation.dto.NavigationHandoffRequest;
import ai.firesphere.gis.foundation.model.LocationReferenceType;
import ai.firesphere.testing.MockSecurityHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mobile QA Suite: GIS Location Validation, Navigation Handoff & Mobile Map Display.
 * Validates mobile field GIS workflows: device GPS accuracy classification (HIGH, MODERATE, POOR),
 * boundary rejection without coordinate fabrication, provider-neutral navigation handoff
 * (Android RFC-5870 geo intent, iOS Apple Maps, Google Maps, Waze), and mobile map display packaging.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MobileGisNavigationQATest {

    private static final UUID MOBILE_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockSecurityHelper.setupSecurityContext(MOBILE_TENANT_ID, "OF-101", List.of("ROLE_OFFICER"));
    }

    @AfterEach
    void tearDown() {
        MockSecurityHelper.clearSecurityContext();
    }

    @Test
    @DisplayName("Mobile GPS: High accuracy fix (<= 15m) classified as HIGH accuracy")
    void testMobileGpsValidation_HighAccuracyGpsFix() throws Exception {
        LocationValidationRequest request = new LocationValidationRequest(18.9220, 72.8347, 8.5);

        mockMvc.perform(post("/api/v1/gis/foundation/validate")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.accuracyLevel").value("HIGH"))
                .andExpect(jsonPath("$.status").value("VALID"));
    }

    @Test
    @DisplayName("Mobile GPS: Medium accuracy cellular fix (15m-50m) classified as MODERATE accuracy")
    void testMobileGpsValidation_CellularMediumAccuracy() throws Exception {
        LocationValidationRequest request = new LocationValidationRequest(18.9220, 72.8347, 32.0);

        mockMvc.perform(post("/api/v1/gis/foundation/validate")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.accuracyLevel").value("MODERATE"))
                .andExpect(jsonPath("$.status").value("VALID"));
    }

    @Test
    @DisplayName("Mobile GPS: Degraded indoor/multipath fix (> 50m) classified as POOR accuracy and APPROXIMATE status")
    void testMobileGpsValidation_DegradedLowAccuracy() throws Exception {
        LocationValidationRequest request = new LocationValidationRequest(18.9220, 72.8347, 85.0);

        mockMvc.perform(post("/api/v1/gis/foundation/validate")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.accuracyLevel").value("POOR"))
                .andExpect(jsonPath("$.status").value("APPROXIMATE"));
    }

    @Test
    @DisplayName("Mobile GPS: Out-of-bounds latitude (> 90.0) cleanly rejected without fabrication")
    void testMobileGpsValidation_OutOfBoundsLatitudeRejected() throws Exception {
        LocationValidationRequest request = new LocationValidationRequest(95.5, 72.8347, 10.0);

        mockMvc.perform(post("/api/v1/gis/foundation/validate")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.status").value("INVALID"))
                .andExpect(jsonPath("$.validationErrors[0]").value(containsString("out of bounds [-90.0, 90.0]")));
    }

    @Test
    @DisplayName("Mobile GPS: Out-of-bounds longitude (> 180.0) cleanly rejected without fabrication")
    void testMobileGpsValidation_OutOfBoundsLongitudeRejected() throws Exception {
        LocationValidationRequest request = new LocationValidationRequest(18.9220, 195.0, 10.0);

        mockMvc.perform(post("/api/v1/gis/foundation/validate")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.status").value("INVALID"))
                .andExpect(jsonPath("$.validationErrors[0]").value(containsString("out of bounds [-180.0, 180.0]")));
    }

    @Test
    @DisplayName("Mobile Navigation: Generates provider-neutral intent URIs for Android and iOS")
    void testMobileNavigationHandoff_ProviderNeutralIntents() throws Exception {
        NavigationHandoffRequest request = new NavigationHandoffRequest(
                18.9220,
                72.8347,
                "Commercial High-Rise Fire",
                "Apollo Bandar, Colaba, Mumbai"
        );

        mockMvc.perform(post("/api/v1/gis/foundation/navigation-handoff")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.destinationLatitude").value(18.9220))
                .andExpect(jsonPath("$.destinationLongitude").value(72.8347))
                .andExpect(jsonPath("$.destinationLabel").value("Commercial High-Rise Fire"))
                .andExpect(jsonPath("$.destinationAddress").value("Apollo Bandar, Colaba, Mumbai"))
                // Standard RFC-5870 URI for Android native Intent.ACTION_VIEW
                .andExpect(jsonPath("$.universalGeoUri").value(containsString("geo:18.922000,72.834700?q=18.922000,72.834700(Commercial+High-Rise+Fire)")))
                // Apple Maps deep link for iOS native app
                .andExpect(jsonPath("$.appleMapsUrl").value(containsString("https://maps.apple.com/?daddr=18.922000,72.834700&dirflg=d")))
                // Google Maps universal web/app link
                .andExpect(jsonPath("$.googleMapsUrl").value(containsString("https://www.google.com/maps/dir/?api=1&destination=18.922000,72.834700")))
                // Waze universal navigation link
                .andExpect(jsonPath("$.wazeUrl").value(containsString("https://waze.com/ul?ll=18.922000,72.834700&navigate=yes")));
    }

    @Test
    @DisplayName("Mobile Navigation: Invalid destination coordinates returns graceful error payload without crashing")
    void testMobileNavigationHandoff_GracefulErrorOnInvalidCoordinates() throws Exception {
        NavigationHandoffRequest request = new NavigationHandoffRequest(
                95.0,
                72.8347,
                "Scene with invalid latitude",
                "Invalid Coordinate St."
        );

        mockMvc.perform(post("/api/v1/gis/foundation/navigation-handoff")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVALID_DESTINATION"))
                .andExpect(jsonPath("$.statusMessage").value(containsString("Destination coordinates are invalid")));
    }

    @Test
    @DisplayName("Mobile Tactical Map: Generates bounding viewport and display metadata for mobile map component")
    void testMobileTacticalMapDisplayMetadata() throws Exception {
        MapDisplayRequest request = new MapDisplayRequest(
                "INC-MOB-01",
                LocationReferenceType.INCIDENT,
                18.9220,
                72.8347,
                "Gateway Fire Incident Scene",
                15.0
        );

        mockMvc.perform(post("/api/v1/gis/foundation/map-display")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.centerLatitude").value(18.9220))
                .andExpect(jsonPath("$.centerLongitude").value(72.8347))
                .andExpect(jsonPath("$.boundingBox").isMap())
                .andExpect(jsonPath("$.zoomLevel").isNumber());
    }

    @Test
    @DisplayName("Mobile Nearby Hydrants: Returns operable hydrants in proximity to mobile location")
    void testMobileNearbyInfrastructure_OperableHydrantsSearch() throws Exception {
        mockMvc.perform(get("/api/v1/gis/foundation/nearby-infrastructure")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .param("latitude", "18.9220")
                        .param("longitude", "72.8347")
                        .param("radiusMeters", "1000.0")
                        .param("operableOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchLatitude").value(18.9220))
                .andExpect(jsonPath("$.searchLongitude").value(72.8347))
                .andExpect(jsonPath("$.radiusMeters").value(1000.0))
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    @DisplayName("Mobile Nearby Hydrants: Remote coordinates with no infrastructure returns HTTP 200 with 0 count")
    void testMobileNearbyInfrastructure_EmptyRemoteLocation() throws Exception {
        mockMvc.perform(get("/api/v1/gis/foundation/nearby-infrastructure")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .param("latitude", "0.0")
                        .param("longitude", "0.0")
                        .param("radiusMeters", "500.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFound").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }
}
