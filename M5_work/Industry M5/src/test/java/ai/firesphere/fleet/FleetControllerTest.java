package ai.firesphere.fleet;

import ai.firesphere.common.dto.PageResponse;
import ai.firesphere.common.exception.BusinessRuleViolationException;
import ai.firesphere.common.exception.ConflictException;
import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.fleet.controller.FleetController;
import ai.firesphere.fleet.dto.*;
import ai.firesphere.fleet.model.VehicleStatus;
import ai.firesphere.fleet.model.VehicleType;
import ai.firesphere.fleet.service.FleetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FleetController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class FleetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FleetService fleetService;

    private final UUID vehicleId = UUID.randomUUID();
    private final UUID stationId = UUID.randomUUID();

    private VehicleResponseDTO sampleVehicle() {
        return new VehicleResponseDTO(
                vehicleId,
                "MH-01-FS-101",
                "ENGINE-1",
                VehicleType.PUMPER_ENGINE,
                VehicleStatus.AVAILABLE,
                stationId,
                "Rosenbauer",
                "Commander",
                2023,
                4000,
                500,
                3000,
                19.0760,
                72.8777,
                100.0,
                100.0,
                "Ready for deployment",
                true,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @DisplayName("POST /api/v1/fleet/vehicles returns 201 Created on valid registration")
    void registerVehicle_Valid_Returns201() throws Exception {
        VehicleRegisterRequest request = new VehicleRegisterRequest(
                "MH-01-FS-101",
                "ENGINE-1",
                VehicleType.PUMPER_ENGINE,
                stationId,
                "Rosenbauer",
                "Commander",
                2023,
                4000,
                500,
                3000,
                "Ready for deployment"
        );

        when(fleetService.registerVehicle(any(VehicleRegisterRequest.class))).thenReturn(sampleVehicle());

        mockMvc.perform(post("/api/v1/fleet/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(vehicleId.toString()))
                .andExpect(jsonPath("$.registrationNumber").value("MH-01-FS-101"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/fleet/vehicles returns 400 when missing required fields")
    void registerVehicle_MissingFields_Returns400() throws Exception {
        String invalidJson = """
                {
                    "callSign": "ENGINE-1"
                }
                """;

        mockMvc.perform(post("/api/v1/fleet/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /api/v1/fleet/vehicles returns 409 when registration number exists")
    void registerVehicle_Duplicate_Returns409() throws Exception {
        VehicleRegisterRequest request = new VehicleRegisterRequest(
                "MH-01-FS-101", "ENGINE-1", VehicleType.PUMPER_ENGINE, stationId,
                null, null, null, null, null, null, null
        );

        when(fleetService.registerVehicle(any()))
                .thenThrow(new ConflictException("Vehicle with registration number already exists: MH-01-FS-101"));

        mockMvc.perform(post("/api/v1/fleet/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STATE_CONFLICT"));
    }

    @Test
    @DisplayName("GET /api/v1/fleet/vehicles/{id} returns 200 OK")
    void getVehicle_Exists_Returns200() throws Exception {
        when(fleetService.getVehicle(vehicleId)).thenReturn(sampleVehicle());

        mockMvc.perform(get("/api/v1/fleet/vehicles/{id}", vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vehicleId.toString()))
                .andExpect(jsonPath("$.registrationNumber").value("MH-01-FS-101"));
    }

    @Test
    @DisplayName("GET /api/v1/fleet/vehicles/{id} returns 404 when not found")
    void getVehicle_NotFound_Returns404() throws Exception {
        when(fleetService.getVehicle(vehicleId))
                .thenThrow(new ResourceNotFoundException("Vehicle not found: " + vehicleId));

        mockMvc.perform(get("/api/v1/fleet/vehicles/{id}", vehicleId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/fleet/vehicles returns 200 with PageResponse")
    void listVehicles_Returns200() throws Exception {
        PageResponse<VehicleResponseDTO> pageResponse = new PageResponse<>(
                List.of(sampleVehicle()), 0, 20, 1, 1, true
        );
        when(fleetService.getVehicles(any(), any(), any(), any(), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/fleet/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("PATCH /api/v1/fleet/vehicles/{id}/status is rejected with 405 Method Not Allowed")
    void updateVehicleStatus_Patch_Returns405() throws Exception {
        VehicleStatusUpdateRequest request = new VehicleStatusUpdateRequest(VehicleStatus.DISPATCHED, "Turnout");

        mockMvc.perform(patch("/api/v1/fleet/vehicles/{id}/status", vehicleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("PUT /api/v1/fleet/vehicles/{id} returns 200 on metadata update")
    void updateVehicle_Returns200() throws Exception {
        VehicleUpdateRequest updateRequest = new VehicleUpdateRequest(
                "RESCUE-1", null, "Volvo", "FMX", 2024, 5000, 600, 3500, "Notes"
        );
        when(fleetService.updateVehicle(eq(vehicleId), any())).thenReturn(sampleVehicle());

        mockMvc.perform(put("/api/v1/fleet/vehicles/{id}", vehicleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/fleet/vehicles/{id}/status returns 200 on valid status transition")
    void updateVehicleStatus_Valid_Returns200() throws Exception {
        VehicleStatusUpdateRequest request = new VehicleStatusUpdateRequest(VehicleStatus.DISPATCHED, "Turnout");
        when(fleetService.updateStatus(eq(vehicleId), any())).thenReturn(sampleVehicle());

        mockMvc.perform(post("/api/v1/fleet/vehicles/{id}/status", vehicleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/fleet/vehicles/{id}/status returns 422 on invalid status transition")
    void updateVehicleStatus_Invalid_Returns422() throws Exception {
        VehicleStatusUpdateRequest request = new VehicleStatusUpdateRequest(VehicleStatus.EN_ROUTE, "Invalid jump");
        when(fleetService.updateStatus(eq(vehicleId), any()))
                .thenThrow(new BusinessRuleViolationException("Invalid vehicle status transition from OUT_OF_SERVICE to EN_ROUTE"));

        mockMvc.perform(post("/api/v1/fleet/vehicles/{id}/status", vehicleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("GET /api/v1/fleet/vehicles/{id}/history returns 200 OK")
    void getVehicleHistory_Returns200() throws Exception {
        VehicleStatusHistoryDTO historyEntry = new VehicleStatusHistoryDTO(
                UUID.randomUUID(), vehicleId, null, VehicleStatus.AVAILABLE, Instant.now(), "FLEET_OFFICER", "Initial"
        );
        when(fleetService.getVehicleHistory(vehicleId)).thenReturn(List.of(historyEntry));

        mockMvc.perform(get("/api/v1/fleet/vehicles/{id}/history", vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].newStatus").value("AVAILABLE"));
    }

    @Test
    @DisplayName("GET /api/v1/fleet/vehicles/{id}/readiness returns 200 OK")
    void getVehicleReadiness_Returns200() throws Exception {
        VehicleReadinessDTO readiness = new VehicleReadinessDTO(
                vehicleId, "MH-01-FS-101", "ENGINE-1", VehicleType.PUMPER_ENGINE,
                VehicleStatus.AVAILABLE, true, "OPERATIONAL_READY", stationId
        );
        when(fleetService.getVehicleReadiness(vehicleId)).thenReturn(readiness);

        mockMvc.perform(get("/api/v1/fleet/vehicles/{id}/readiness", vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value(vehicleId.toString()))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.readinessState").value("OPERATIONAL_READY"));
    }
}
