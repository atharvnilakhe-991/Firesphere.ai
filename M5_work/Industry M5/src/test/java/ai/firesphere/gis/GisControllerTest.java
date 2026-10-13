package ai.firesphere.gis;

import ai.firesphere.common.exception.ConflictException;
import ai.firesphere.common.exception.GlobalExceptionHandler;
import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.gis.controller.GisRoutingController;
import ai.firesphere.gis.controller.HazardZoneController;
import ai.firesphere.gis.controller.HydrantController;
import ai.firesphere.gis.dto.*;
import ai.firesphere.gis.service.GisOperationalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {HydrantController.class, HazardZoneController.class, GisRoutingController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class GisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GisOperationalService gisService;

    private final UUID hydrantId = UUID.randomUUID();
    private final UUID zoneId = UUID.randomUUID();

    // ========================================================================
    // HYDRANT ENDPOINTS
    // ========================================================================

    @Test
    @DisplayName("POST /api/v1/gis/hydrants - Register hydrant success (201)")
    void testRegisterHydrant_Success() throws Exception {
        HydrantCreateRequest req = new HydrantCreateRequest(
                "HYD-101", 19.0760, 72.8777, 65.0, 1500.0, true, "South Terminal Road"
        );
        HydrantResponseDTO resp = new HydrantResponseDTO(
                hydrantId, "HYD-101", 19.0760, 72.8777, 65.0, 1500.0, true,
                "South Terminal Road", null, Instant.now(), Instant.now()
        );

        when(gisService.registerHydrant(any(HydrantCreateRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/gis/hydrants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(hydrantId.toString()))
                .andExpect(jsonPath("$.hydrantCode").value("HYD-101"))
                .andExpect(jsonPath("$.operable").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/gis/hydrants - Validation failure on invalid coordinates (400)")
    void testRegisterHydrant_ValidationFailure() throws Exception {
        HydrantCreateRequest req = new HydrantCreateRequest(
                "HYD-BAD", 99.0, 72.8777, 65.0, 1500.0, true, "Out of bounds"
        );

        mockMvc.perform(post("/api/v1/gis/hydrants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /api/v1/gis/hydrants - Duplicate hydrant code conflict (409)")
    void testRegisterHydrant_Conflict() throws Exception {
        HydrantCreateRequest req = new HydrantCreateRequest(
                "HYD-EXISTS", 19.0760, 72.8777, 65.0, 1500.0, true, "Station"
        );
        when(gisService.registerHydrant(any(HydrantCreateRequest.class)))
                .thenThrow(new ConflictException("Hydrant with code already exists: HYD-EXISTS"));

        mockMvc.perform(post("/api/v1/gis/hydrants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STATE_CONFLICT"));
    }

    @Test
    @DisplayName("GET /api/v1/gis/hydrants/{id} - Get hydrant by ID (200)")
    void testGetHydrantById_Success() throws Exception {
        HydrantResponseDTO resp = new HydrantResponseDTO(
                hydrantId, "HYD-101", 19.0760, 72.8777, 65.0, 1500.0, true,
                "South Terminal", null, Instant.now(), Instant.now()
        );
        when(gisService.getHydrantById(hydrantId)).thenReturn(resp);

        mockMvc.perform(get("/api/v1/gis/hydrants/{id}", hydrantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(hydrantId.toString()))
                .andExpect(jsonPath("$.hydrantCode").value("HYD-101"));
    }

    @Test
    @DisplayName("GET /api/v1/gis/hydrants/{id} - Not found (404)")
    void testGetHydrantById_NotFound() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(gisService.getHydrantById(unknown)).thenThrow(new ResourceNotFoundException("Hydrant not found: " + unknown));

        mockMvc.perform(get("/api/v1/gis/hydrants/{id}", unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/gis/hydrants - Paginated list (200)")
    void testGetHydrants_List() throws Exception {
        HydrantResponseDTO resp = new HydrantResponseDTO(
                hydrantId, "HYD-101", 19.0760, 72.8777, 65.0, 1500.0, true,
                "South Terminal", null, Instant.now(), Instant.now()
        );
        when(gisService.getHydrants(eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resp)));

        mockMvc.perform(get("/api/v1/gis/hydrants")
                        .param("operable", "true")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].hydrantCode").value("HYD-101"));
    }

    @Test
    @DisplayName("PUT /api/v1/gis/hydrants/{id} - Update hydrant (200)")
    void testUpdateHydrant_Success() throws Exception {
        HydrantUpdateRequest req = new HydrantUpdateRequest(
                19.0800, 72.8800, 70.0, 1600.0, false, "Updated Address"
        );
        HydrantResponseDTO resp = new HydrantResponseDTO(
                hydrantId, "HYD-101", 19.0800, 72.8800, 70.0, 1600.0, false,
                "Updated Address", null, Instant.now(), Instant.now()
        );
        when(gisService.updateHydrant(eq(hydrantId), any(HydrantUpdateRequest.class))).thenReturn(resp);

        mockMvc.perform(put("/api/v1/gis/hydrants/{id}", hydrantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operable").value(false))
                .andExpect(jsonPath("$.address").value("Updated Address"));
    }

    @Test
    @DisplayName("GET /api/v1/gis/hydrants/nearby - Spatial proximity query (200)")
    void testFindNearbyHydrants_Success() throws Exception {
        HydrantResponseDTO resp = new HydrantResponseDTO(
                hydrantId, "HYD-NEAR", 19.0765, 72.8780, 65.0, 1500.0, true,
                "Near Scene", 58.5, Instant.now(), Instant.now()
        );
        when(gisService.findNearbyHydrants(19.0760, 72.8777, 500.0, true))
                .thenReturn(List.of(resp));

        mockMvc.perform(get("/api/v1/gis/hydrants/nearby")
                        .param("latitude", "19.0760")
                        .param("longitude", "72.8777")
                        .param("radiusMeters", "500.0")
                        .param("operableOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hydrantCode").value("HYD-NEAR"))
                .andExpect(jsonPath("$[0].distanceMeters").value(58.5));
    }

    // ========================================================================
    // HAZARD ZONE ENDPOINTS
    // ========================================================================

    @Test
    @DisplayName("POST /api/v1/gis/hazard-zones - Create hazard zone (201)")
    void testCreateHazardZone_Success() throws Exception {
        HazardZoneCreateRequest req = new HazardZoneCreateRequest(
                "Refinery Fuel Depot", "CHEMICAL", "HZ-REF-01",
                19.0800, 72.8800, 500.0, "CRITICAL", "Full Evacuation", null
        );
        HazardZoneResponseDTO resp = new HazardZoneResponseDTO(
                zoneId, "HZ-REF-01", "Refinery Fuel Depot", "CHEMICAL",
                19.0800, 72.8800, 500.0, "CRITICAL", true, "Full Evacuation",
                null, "POLYGON ((...))", Instant.now(), Instant.now()
        );
        when(gisService.createHazardZone(any(HazardZoneCreateRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/gis/hazard-zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.zoneCode").value("HZ-REF-01"))
                .andExpect(jsonPath("$.severity").value("CRITICAL"));
    }

    @Test
    @DisplayName("GET /api/v1/gis/hazard-zones/{id} - Get hazard zone (200)")
    void testGetHazardZoneById_Success() throws Exception {
        HazardZoneResponseDTO resp = new HazardZoneResponseDTO(
                zoneId, "HZ-REF-01", "Refinery Fuel Depot", "CHEMICAL",
                19.0800, 72.8800, 500.0, "CRITICAL", true, "Full Evacuation",
                null, "POLYGON ((...))", Instant.now(), Instant.now()
        );
        when(gisService.getHazardZoneById(zoneId)).thenReturn(resp);

        mockMvc.perform(get("/api/v1/gis/hazard-zones/{id}", zoneId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zoneCode").value("HZ-REF-01"));
    }

    @Test
    @DisplayName("GET /api/v1/gis/hazard-zones/contains - Spatial point-in-zone query (200)")
    void testFindHazardZonesContainingPoint_Success() throws Exception {
        HazardZoneResponseDTO resp = new HazardZoneResponseDTO(
                zoneId, "HZ-REF-01", "Refinery Fuel Depot", "CHEMICAL",
                19.0800, 72.8800, 500.0, "CRITICAL", true, "Full Evacuation",
                12.0, "POLYGON ((...))", Instant.now(), Instant.now()
        );
        when(gisService.findHazardZonesContainingPoint(19.0800, 72.8800))
                .thenReturn(List.of(resp));

        mockMvc.perform(get("/api/v1/gis/hazard-zones/contains")
                        .param("latitude", "19.0800")
                        .param("longitude", "72.8800"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].zoneCode").value("HZ-REF-01"));
    }

    // ========================================================================
    // ROUTE CALCULATION ENDPOINT (CONTRACT BASELINE)
    // ========================================================================

    @Test
    @DisplayName("POST /api/v1/gis/routes/calculate - Route calculation contract baseline (200)")
    void testCalculateEmergencyRoute_Success() throws Exception {
        RouteCalculationRequest req = new RouteCalculationRequest(
                19.0760, 72.8777, 19.0820, 72.8840
        );
        RouteCalculationResponse resp = new RouteCalculationResponse(
                1250.0, 90L, List.of(), "mock_polyline_123"
        );
        when(gisService.calculateEmergencyRoute(any(RouteCalculationRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/gis/routes/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distanceMeters").value(1250.0))
                .andExpect(jsonPath("$.estimatedDurationSeconds").value(90))
                .andExpect(jsonPath("$.polyline").value("mock_polyline_123"));
    }
}
