package ai.firesphere.dispatch;

import ai.firesphere.common.dto.PageResponse;
import ai.firesphere.common.exception.BusinessRuleViolationException;
import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.dispatch.controller.DispatchController;
import ai.firesphere.dispatch.dto.*;
import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.dispatch.model.DispatchStatus;
import ai.firesphere.dispatch.service.DispatchService;
import ai.firesphere.incident.model.IncidentSeverity;
import ai.firesphere.incident.model.IncidentType;
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

@WebMvcTest(DispatchController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class DispatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DispatchService dispatchService;

    private final UUID dispatchId = UUID.randomUUID();
    private final UUID incidentId = UUID.randomUUID();
    private final UUID stationId = UUID.randomUUID();

    private DispatchResponseDTO sampleResponse() {
        return new DispatchResponseDTO(
                dispatchId,
                incidentId,
                stationId,
                "DSP-20261005-12345",
                DispatchStatus.CREATED,
                DispatchPriority.HIGH,
                List.of(UUID.randomUUID()),
                List.of(UUID.randomUUID()),
                Instant.now(),
                null, null, null, null, null, null,
                "First turnout notes",
                Instant.now(), Instant.now()
        );
    }

    @Test
    @DisplayName("POST /api/v1/dispatch/orders returns 201 Created on valid payload")
    void createDispatchOrder_Valid_Returns201() throws Exception {
        DispatchCreateRequest request = new DispatchCreateRequest(
                incidentId,
                stationId,
                DispatchPriority.HIGH,
                List.of(UUID.randomUUID()),
                List.of(UUID.randomUUID()),
                "First alarm turnout"
        );

        when(dispatchService.createDispatch(any(DispatchCreateRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/dispatch/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(dispatchId.toString()))
                .andExpect(jsonPath("$.dispatchCode").value("DSP-20261005-12345"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    @DisplayName("POST /api/v1/dispatch/orders returns 400 when missing priority")
    void createDispatchOrder_MissingPriority_Returns400() throws Exception {
        String invalidJson = """
                {
                    "incidentId": "%s",
                    "stationId": "%s"
                }
                """.formatted(incidentId, stationId);

        mockMvc.perform(post("/api/v1/dispatch/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /api/v1/dispatch/orders returns 400 when missing incidentId")
    void createDispatchOrder_MissingIncidentId_Returns400() throws Exception {
        String invalidJson = """
                {
                    "stationId": "%s",
                    "priority": "HIGH"
                }
                """.formatted(stationId);

        mockMvc.perform(post("/api/v1/dispatch/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("GET /api/v1/dispatch/orders/{id} returns 200 OK")
    void getDispatchOrder_Exists_Returns200() throws Exception {
        when(dispatchService.getDispatch(dispatchId)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/dispatch/orders/{id}", dispatchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dispatchId.toString()))
                .andExpect(jsonPath("$.dispatchCode").value("DSP-20261005-12345"));
    }

    @Test
    @DisplayName("GET /api/v1/dispatch/orders/{id} returns 404 when not found")
    void getDispatchOrder_NotFound_Returns404() throws Exception {
        when(dispatchService.getDispatch(dispatchId))
                .thenThrow(new ResourceNotFoundException("Dispatch order not found: " + dispatchId));

        mockMvc.perform(get("/api/v1/dispatch/orders/{id}", dispatchId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/dispatch/orders returns 200 OK with PageResponse")
    void listDispatchOrders_Returns200() throws Exception {
        PageResponse<DispatchResponseDTO> pageResponse = new PageResponse<>(
                List.of(sampleResponse()), 0, 20, 1, 1, true
        );
        when(dispatchService.getDispatches(any(), any(), any(), any(), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/dispatch/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/dispatch/orders/{id} returns 200 OK on metadata update")
    void updateDispatchOrder_Returns200() throws Exception {
        DispatchUpdateRequest updateRequest = new DispatchUpdateRequest(
                DispatchPriority.CRITICAL, null, null, null, "Updated notes"
        );
        when(dispatchService.updateDispatch(eq(dispatchId), any())).thenReturn(sampleResponse());

        mockMvc.perform(put("/api/v1/dispatch/orders/{id}", dispatchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/dispatch/orders/{id}/status returns 200 OK on valid transition")
    void updateDispatchStatus_Valid_Returns200() throws Exception {
        DispatchStatusUpdateRequest statusRequest = new DispatchStatusUpdateRequest(
                DispatchStatus.DISPATCHED, "Transmitted to units"
        );
        when(dispatchService.updateDispatchStatus(eq(dispatchId), any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/dispatch/orders/{id}/status", dispatchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/dispatch/orders/{id}/status returns 422 on invalid transition")
    void updateDispatchStatus_Invalid_Returns422() throws Exception {
        DispatchStatusUpdateRequest statusRequest = new DispatchStatusUpdateRequest(
                DispatchStatus.EN_ROUTE, "Invalid jump"
        );
        when(dispatchService.updateDispatchStatus(eq(dispatchId), any()))
                .thenThrow(new BusinessRuleViolationException("Invalid dispatch status transition from CREATED to EN_ROUTE"));

        mockMvc.perform(post("/api/v1/dispatch/orders/{id}/status", dispatchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("GET /api/v1/dispatch/orders/{id}/history returns 200 OK")
    void getDispatchHistory_Returns200() throws Exception {
        DispatchStatusHistoryDTO historyEntry = new DispatchStatusHistoryDTO(
                UUID.randomUUID(), dispatchId, null, DispatchStatus.CREATED, Instant.now(), "DISPATCHER", "Initial"
        );
        when(dispatchService.getDispatchHistory(dispatchId)).thenReturn(List.of(historyEntry));

        mockMvc.perform(get("/api/v1/dispatch/orders/{id}/history", dispatchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].newStatus").value("CREATED"));
    }

    @Test
    @DisplayName("POST /api/v1/dispatch/recommendations returns 200 OK")
    void getRecommendations_Returns200() throws Exception {
        DispatchRecommendationRequest request = new DispatchRecommendationRequest(
                incidentId,
                IncidentType.STRUCTURAL_FIRE,
                IncidentSeverity.HIGH,
                19.0760,
                72.8777
        );
        DispatchRecommendationResponse response = new DispatchRecommendationResponse(
                incidentId,
                UUID.randomUUID(),
                "Station 1",
                2500.0,
                300,
                List.of(new DispatchRecommendationResponse.RecommendedUnit("PUMPER_ENGINE", 1, "Attack")),
                6,
                "RECOMMENDATION ONLY"
        );

        when(dispatchService.getRecommendations(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/dispatch/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(incidentId.toString()))
                .andExpect(jsonPath("$.recommendedUnits.length()").value(1));
    }
}
