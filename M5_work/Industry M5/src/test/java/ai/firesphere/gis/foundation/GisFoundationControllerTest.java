package ai.firesphere.gis.foundation;

import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.gis.foundation.controller.GisFoundationController;
import ai.firesphere.gis.foundation.dto.LocationValidationRequest;
import ai.firesphere.gis.foundation.dto.MapDisplayRequest;
import ai.firesphere.gis.foundation.dto.NearbyInfrastructureRequest;
import ai.firesphere.gis.foundation.dto.NavigationHandoffRequest;
import ai.firesphere.gis.foundation.model.LocationReferenceType;
import ai.firesphere.gis.foundation.service.GisFoundationService;
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

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GisFoundationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GisFoundationService foundationService;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("POST /api/v1/gis/foundation/validate returns 200 OK with validation status")
    void testValidateEndpoint_Success() throws Exception {
        LocationValidationRequest request = new LocationValidationRequest(12.9716, 77.5946, 10.0);

        mockMvc.perform(post("/api/v1/gis/foundation/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.status", is("VALID")))
                .andExpect(jsonPath("$.accuracyLevel", is("HIGH")))
                .andExpect(jsonPath("$.latitude", is(12.9716)))
                .andExpect(jsonPath("$.longitude", is(77.5946)));
    }

    @Test
    @DisplayName("POST /api/v1/gis/foundation/validate returns 200 OK with INVALID status for out-of-bounds coordinates")
    void testValidateEndpoint_InvalidCoordinates() throws Exception {
        LocationValidationRequest request = new LocationValidationRequest(95.0, 77.5946, 10.0);

        mockMvc.perform(post("/api/v1/gis/foundation/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(false)))
                .andExpect(jsonPath("$.status", is("INVALID")))
                .andExpect(jsonPath("$.validationErrors", not(empty())));
    }

    @Test
    @DisplayName("POST /api/v1/gis/foundation/map-display returns 200 OK with bounding box and markers")
    void testMapDisplayEndpoint_Success() throws Exception {
        MapDisplayRequest request = new MapDisplayRequest(
                "PROP-ARCADE-01",
                LocationReferenceType.PROPERTY,
                12.9716,
                77.5946,
                "MG Road Arcade",
                12.0
        );

        mockMvc.perform(post("/api/v1/gis/foundation/map-display")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.centerLatitude", is(12.9716)))
                .andExpect(jsonPath("$.centerLongitude", is(77.5946)))
                .andExpect(jsonPath("$.providerStatus", is("MOCK_MODE")))
                .andExpect(jsonPath("$.markers", hasSize(1)))
                .andExpect(jsonPath("$.markers[0].referenceId", is("PROP-ARCADE-01")));
    }

    @Test
    @DisplayName("POST /api/v1/gis/foundation/nearby-infrastructure returns 200 OK with search results")
    void testNearbyInfrastructurePostEndpoint_Success() throws Exception {
        NearbyInfrastructureRequest request = new NearbyInfrastructureRequest(
                12.9716, 77.5946, 1000.0, true
        );

        mockMvc.perform(post("/api/v1/gis/foundation/nearby-infrastructure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchLatitude", is(12.9716)))
                .andExpect(jsonPath("$.searchLongitude", is(77.5946)))
                .andExpect(jsonPath("$.radiusMeters", is(1000.0)))
                .andExpect(jsonPath("$.items", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/gis/foundation/nearby-infrastructure via query params returns 200 OK")
    void testNearbyInfrastructureGetEndpoint_Success() throws Exception {
        mockMvc.perform(get("/api/v1/gis/foundation/nearby-infrastructure")
                        .param("latitude", "12.9716")
                        .param("longitude", "77.5946")
                        .param("radiusMeters", "500.0")
                        .param("operableOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchLatitude", is(12.9716)))
                .andExpect(jsonPath("$.searchLongitude", is(77.5946)));
    }

    @Test
    @DisplayName("POST /api/v1/gis/foundation/navigation-handoff returns 200 OK with universal links")
    void testNavigationHandoffEndpoint_Success() throws Exception {
        NavigationHandoffRequest request = new NavigationHandoffRequest(
                12.9716,
                77.5946,
                "City Hospital ER Bay",
                "Hospital Road"
        );

        mockMvc.perform(post("/api/v1/gis/foundation/navigation-handoff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("READY")))
                .andExpect(jsonPath("$.destinationLatitude", is(12.9716)))
                .andExpect(jsonPath("$.universalGeoUri", containsString("geo:12.971600,77.594600")))
                .andExpect(jsonPath("$.googleMapsUrl", containsString("google.com/maps")))
                .andExpect(jsonPath("$.appleMapsUrl", containsString("maps.apple.com")))
                .andExpect(jsonPath("$.wazeUrl", containsString("waze.com")));
    }
}
