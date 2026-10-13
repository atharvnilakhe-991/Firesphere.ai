package ai.firesphere.incident;

import ai.firesphere.common.dto.PageResponse;
import ai.firesphere.common.exception.BusinessRuleViolationException;
import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.incident.controller.IncidentController;
import ai.firesphere.incident.dto.*;
import ai.firesphere.incident.model.IncidentSeverity;
import ai.firesphere.incident.model.IncidentStatus;
import ai.firesphere.incident.model.IncidentType;
import ai.firesphere.incident.service.IncidentService;
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

@WebMvcTest(IncidentController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class IncidentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IncidentService incidentService;

    private final UUID incidentId = UUID.randomUUID();

    @Test
    @DisplayName("POST /api/v1/incidents returns 201 Created on valid request")
    void createIncident_Valid_Returns201() throws Exception {
        IncidentCreateRequest request = new IncidentCreateRequest(
                "Commercial Plaza Fire",
                "Fire on second floor",
                IncidentType.STRUCTURAL_FIRE,
                IncidentSeverity.HIGH,
                19.0760,
                72.8777,
                "Tower 4, Mumbai",
                "Security",
                "+919876543210",
                null,
                null
        );

        IncidentResponseDTO responseDTO = new IncidentResponseDTO(
                incidentId, "INC-20261005-11111", "Commercial Plaza Fire", "Fire on second floor",
                IncidentType.STRUCTURAL_FIRE, IncidentSeverity.HIGH, IncidentStatus.REPORTED,
                19.0760, 72.8777, "Tower 4, Mumbai", "Security", "+919876543210", null, List.of(),
                Instant.now(), null, null, null, null, null, null, null, null,
                Instant.now(), Instant.now()
        );

        when(incidentService.createIncident(any(IncidentCreateRequest.class))).thenReturn(responseDTO);

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(incidentId.toString()))
                .andExpect(jsonPath("$.incidentNumber").value("INC-20261005-11111"))
                .andExpect(jsonPath("$.status").value("REPORTED"));
    }

    @Test
    @DisplayName("POST /api/v1/incidents returns 400 Bad Request on invalid coordinates")
    void createIncident_InvalidCoordinates_Returns400() throws Exception {
        IncidentCreateRequest badRequest = new IncidentCreateRequest(
                "Commercial Plaza Fire",
                "Fire on second floor",
                IncidentType.STRUCTURAL_FIRE,
                IncidentSeverity.HIGH,
                195.0, // Invalid latitude > 90
                72.8777,
                "Tower 4, Mumbai",
                null, null, null, null
        );

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.latitude").exists());
    }

    @Test
    @DisplayName("GET /api/v1/incidents/{id} returns 200 OK for existing incident")
    void getIncident_Existing_Returns200() throws Exception {
        IncidentResponseDTO responseDTO = new IncidentResponseDTO(
                incidentId, "INC-20261005-11111", "Commercial Plaza Fire", "Fire on second floor",
                IncidentType.STRUCTURAL_FIRE, IncidentSeverity.HIGH, IncidentStatus.REPORTED,
                19.0760, 72.8777, "Tower 4, Mumbai", "Security", "+919876543210", null, List.of(),
                Instant.now(), null, null, null, null, null, null, null, null,
                Instant.now(), Instant.now()
        );

        when(incidentService.getIncident(incidentId)).thenReturn(responseDTO);

        mockMvc.perform(get("/api/v1/incidents/{id}", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(incidentId.toString()))
                .andExpect(jsonPath("$.title").value("Commercial Plaza Fire"));
    }

    @Test
    @DisplayName("GET /api/v1/incidents/{id} returns 404 Not Found for missing incident")
    void getIncident_NotFound_Returns404() throws Exception {
        when(incidentService.getIncident(incidentId))
                .thenThrow(new ResourceNotFoundException("Incident", incidentId));

        mockMvc.perform(get("/api/v1/incidents/{id}", incidentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/incidents returns 200 OK with paginated list")
    void listIncidents_Returns200() throws Exception {
        PageResponse<IncidentResponseDTO> pageResponse = new PageResponse<>(
                List.of(), 0, 20, 0, 0, true
        );

        when(incidentService.getIncidents(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @DisplayName("POST /api/v1/incidents/{id}/status returns 200 OK on valid transition")
    void updateStatus_Valid_Returns200() throws Exception {
        IncidentStatusUpdateRequest statusReq = new IncidentStatusUpdateRequest(IncidentStatus.VERIFIED, "Confirmed");
        IncidentResponseDTO responseDTO = new IncidentResponseDTO(
                incidentId, "INC-20261005-11111", "Commercial Plaza Fire", null,
                IncidentType.STRUCTURAL_FIRE, IncidentSeverity.HIGH, IncidentStatus.VERIFIED,
                19.0760, 72.8777, "Tower 4, Mumbai", null, null, null, List.of(),
                Instant.now(), Instant.now(), null, null, null, null, null, null, null,
                Instant.now(), Instant.now()
        );

        when(incidentService.updateStatus(eq(incidentId), any(IncidentStatusUpdateRequest.class)))
                .thenReturn(responseDTO);

        mockMvc.perform(post("/api/v1/incidents/{id}/status", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));
    }

    @Test
    @DisplayName("POST /api/v1/incidents/{id}/status returns 422 on invalid state transition")
    void updateStatus_InvalidTransition_Returns422() throws Exception {
        IncidentStatusUpdateRequest statusReq = new IncidentStatusUpdateRequest(IncidentStatus.CLOSED, "Illegal skip");

        when(incidentService.updateStatus(eq(incidentId), any(IncidentStatusUpdateRequest.class)))
                .thenThrow(new BusinessRuleViolationException("Invalid status transition from REPORTED to CLOSED"));

        mockMvc.perform(post("/api/v1/incidents/{id}/status", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Invalid status transition from REPORTED to CLOSED"));
    }

    @Test
    @DisplayName("POST /api/v1/incidents/{id}/close returns 200 OK on valid closure")
    void closeIncident_Valid_Returns200() throws Exception {
        IncidentCloseRequest closeReq = new IncidentCloseRequest("Incident fully controlled and extinguished");
        IncidentResponseDTO responseDTO = new IncidentResponseDTO(
                incidentId, "INC-20261005-11111", "Commercial Plaza Fire", null,
                IncidentType.STRUCTURAL_FIRE, IncidentSeverity.HIGH, IncidentStatus.CLOSED,
                19.0760, 72.8777, "Tower 4, Mumbai", null, null, null, List.of(),
                Instant.now(), Instant.now(), null, null, null, null, Instant.now(), Instant.now(),
                "Incident fully controlled and extinguished", Instant.now(), Instant.now()
        );

        when(incidentService.closeIncident(eq(incidentId), any(IncidentCloseRequest.class)))
                .thenReturn(responseDTO);

        mockMvc.perform(post("/api/v1/incidents/{id}/close", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(closeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closureRemarks").value("Incident fully controlled and extinguished"));
    }

    @Test
    @DisplayName("POST /api/v1/incidents/{id}/close returns 400 Bad Request when remarks are blank")
    void closeIncident_BlankRemarks_Returns400() throws Exception {
        IncidentCloseRequest badReq = new IncidentCloseRequest("   ");

        mockMvc.perform(post("/api/v1/incidents/{id}/close", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.remarks").exists());
    }

    @Test
    @DisplayName("GET /api/v1/incidents/{id}/history returns 200 OK with transition timeline")
    void getHistory_Returns200() throws Exception {
        IncidentStatusHistoryDTO h1 = new IncidentStatusHistoryDTO(
                UUID.randomUUID(), incidentId, null, IncidentStatus.REPORTED, Instant.now(), "SYSTEM", "Intake"
        );

        when(incidentService.getIncidentHistory(incidentId)).thenReturn(List.of(h1));

        mockMvc.perform(get("/api/v1/incidents/{id}/history", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].newStatus").value("REPORTED"))
                .andExpect(jsonPath("$[0].transitionedBy").value("SYSTEM"));
    }
}
