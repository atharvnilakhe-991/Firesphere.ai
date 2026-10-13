package ai.firesphere.dispatch;

import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.dispatch.dto.DispatchCreateRequest;
import ai.firesphere.dispatch.dto.DispatchStatusUpdateRequest;
import ai.firesphere.dispatch.dto.DispatchUpdateRequest;
import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.dispatch.model.DispatchStatus;
import ai.firesphere.dispatch.repository.DispatchOrderRepository;
import ai.firesphere.dispatch.repository.DispatchStatusHistoryRepository;
import ai.firesphere.incident.dto.IncidentCloseRequest;
import ai.firesphere.incident.dto.IncidentCreateRequest;
import ai.firesphere.incident.dto.IncidentResponseDTO;
import ai.firesphere.incident.dto.IncidentStatusUpdateRequest;
import ai.firesphere.incident.model.IncidentSeverity;
import ai.firesphere.incident.model.IncidentStatus;
import ai.firesphere.incident.model.IncidentType;
import ai.firesphere.incident.service.IncidentService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DispatchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private DispatchOrderRepository dispatchOrderRepository;

    @Autowired
    private DispatchStatusHistoryRepository historyRepository;

    private final UUID tenantA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID tenantB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private UUID activeIncidentId;
    private final UUID stationId = UUID.randomUUID();
    private final UUID vehicleId1 = UUID.randomUUID();
    private final UUID vehicleId2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantA);

        // Create an active incident for testing
        IncidentCreateRequest incidentRequest = new IncidentCreateRequest(
                "Refinery Tank Farm Fire",
                "High pressure storage tank fire",
                IncidentType.INDUSTRIAL_HAZARD,
                IncidentSeverity.CRITICAL,
                19.0760,
                72.8777,
                "Dock 4, Industrial Port",
                "Harbor Master",
                "+919876543210",
                stationId,
                List.of("TANK-FOAM-1")
        );
        IncidentResponseDTO incident = incidentService.createIncident(incidentRequest);
        activeIncidentId = incident.id();

        // Advance to VERIFIED
        incidentService.updateStatus(activeIncidentId, new IncidentStatusUpdateRequest(
                IncidentStatus.VERIFIED, "Verified by Harbor Master"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("End-to-End: Create, Retrieve, Transition, Audit & Verify Incident Invariance")
    void testFullDispatchLifecycleAndIncidentBoundary() throws Exception {
        // 1. Create Dispatch Order
        DispatchCreateRequest createRequest = new DispatchCreateRequest(
                activeIncidentId,
                stationId,
                DispatchPriority.CRITICAL,
                List.of(vehicleId1),
                List.of(UUID.randomUUID()),
                "First alarm industrial foam attack"
        );

        String createJson = mockMvc.perform(post("/api/v1/dispatch/orders")
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.dispatchCode").isNotEmpty())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.priority").value("CRITICAL"))
                .andReturn().getResponse().getContentAsString();

        UUID dispatchId = UUID.fromString(objectMapper.readTree(createJson).get("id").asText());

        // 2. Incident Boundary: Incident status MUST NOT be automatically changed
        mockMvc.perform(get("/api/v1/incidents/{id}", activeIncidentId)
                        .header("X-Tenant-ID", tenantA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));

        // 3. Get by ID
        mockMvc.perform(get("/api/v1/dispatch/orders/{id}", dispatchId)
                        .header("X-Tenant-ID", tenantA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dispatchId.toString()))
                .andExpect(jsonPath("$.incidentId").value(activeIncidentId.toString()));

        // 4. Update mutable metadata (PUT)
        DispatchUpdateRequest updateRequest = new DispatchUpdateRequest(
                DispatchPriority.HIGH,
                null,
                null,
                null,
                "Updated operational briefing notes"
        );
        mockMvc.perform(put("/api/v1/dispatch/orders/{id}", dispatchId)
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.operationalNotes").value("Updated operational briefing notes"));

        // 5. Sequential lifecycle progression: CREATED -> DISPATCHED -> ACKNOWLEDGED -> EN_ROUTE -> ARRIVED -> COMPLETED
        DispatchStatus[] steps = {
                DispatchStatus.DISPATCHED,
                DispatchStatus.ACKNOWLEDGED,
                DispatchStatus.EN_ROUTE,
                DispatchStatus.ARRIVED,
                DispatchStatus.COMPLETED
        };

        for (DispatchStatus targetStatus : steps) {
            DispatchStatusUpdateRequest statusRequest = new DispatchStatusUpdateRequest(
                    targetStatus, "Stepping to " + targetStatus
            );
            mockMvc.perform(post("/api/v1/dispatch/orders/{id}/status", dispatchId)
                            .header("X-Tenant-ID", tenantA.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(statusRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(targetStatus.name()));
        }

        // 6. Verify Terminal state: COMPLETED cannot transition or be updated
        mockMvc.perform(post("/api/v1/dispatch/orders/{id}/status", dispatchId)
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DispatchStatusUpdateRequest(DispatchStatus.CREATED, "Reopen"))))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(put("/api/v1/dispatch/orders/{id}", dispatchId)
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isUnprocessableEntity());

        // 7. Verify History contains all transition entries
        mockMvc.perform(get("/api/v1/dispatch/orders/{id}/history", dispatchId)
                        .header("X-Tenant-ID", tenantA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6)); // initial CREATED + 5 transitions
    }

    @Test
    @DisplayName("Cancellation: CREATED -> CANCELLED is permitted, then immutable")
    void testDispatchCancellation() throws Exception {
        DispatchCreateRequest createRequest = new DispatchCreateRequest(
                activeIncidentId,
                stationId,
                DispatchPriority.MEDIUM,
                List.of(vehicleId2),
                List.of(),
                "Support tanker"
        );

        String createJson = mockMvc.perform(post("/api/v1/dispatch/orders")
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID dispatchId = UUID.fromString(objectMapper.readTree(createJson).get("id").asText());

        // Cancel order
        mockMvc.perform(post("/api/v1/dispatch/orders/{id}/status", dispatchId)
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DispatchStatusUpdateRequest(
                                DispatchStatus.CANCELLED, "False alarm stand down"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("False alarm stand down"));

        // Verify CANCELLED is terminal
        mockMvc.perform(post("/api/v1/dispatch/orders/{id}/status", dispatchId)
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DispatchStatusUpdateRequest(
                                DispatchStatus.DISPATCHED, "Try revive"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Duplicate active dispatch protection: assigning committed vehicle fails with 422")
    void testDuplicateActiveDispatchProtection() throws Exception {
        UUID sharedVehicle = UUID.randomUUID();

        // First order with vehicle
        DispatchCreateRequest order1 = new DispatchCreateRequest(
                activeIncidentId,
                stationId,
                DispatchPriority.HIGH,
                List.of(sharedVehicle),
                List.of(),
                "Initial pumper"
        );
        mockMvc.perform(post("/api/v1/dispatch/orders")
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(order1)))
                .andExpect(status().isCreated());

        // Second order with same active vehicle
        DispatchCreateRequest order2 = new DispatchCreateRequest(
                activeIncidentId,
                stationId,
                DispatchPriority.HIGH,
                List.of(sharedVehicle),
                List.of(),
                "Duplicate vehicle dispatch"
        );
        mockMvc.perform(post("/api/v1/dispatch/orders")
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(order2)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("Tenant Isolation: Tenant B cannot access Tenant A dispatch order or history")
    void testTenantIsolation() throws Exception {
        // Create order under Tenant A
        DispatchCreateRequest createRequest = new DispatchCreateRequest(
                activeIncidentId,
                stationId,
                DispatchPriority.LOW,
                List.of(UUID.randomUUID()),
                List.of(),
                "Tenant A order"
        );

        String createJson = mockMvc.perform(post("/api/v1/dispatch/orders")
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID dispatchId = UUID.fromString(objectMapper.readTree(createJson).get("id").asText());

        // Tenant B attempts to read order -> 404
        mockMvc.perform(get("/api/v1/dispatch/orders/{id}", dispatchId)
                        .header("X-Tenant-ID", tenantB.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // Tenant B attempts to mutate order -> 404
        mockMvc.perform(put("/api/v1/dispatch/orders/{id}", dispatchId)
                        .header("X-Tenant-ID", tenantB.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DispatchUpdateRequest(DispatchPriority.CRITICAL, null, null, null, "Hijack"))))
                .andExpect(status().isNotFound());

        // Tenant B attempts to transition status -> 404
        mockMvc.perform(post("/api/v1/dispatch/orders/{id}/status", dispatchId)
                        .header("X-Tenant-ID", tenantB.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DispatchStatusUpdateRequest(DispatchStatus.DISPATCHED, "Hijack"))))
                .andExpect(status().isNotFound());

        // Tenant B attempts to read history -> 404
        mockMvc.perform(get("/api/v1/dispatch/orders/{id}/history", dispatchId)
                        .header("X-Tenant-ID", tenantB.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Closed Incident Guard: Dispatch cannot be created against a CLOSED incident")
    void testCannotDispatchClosedIncident() throws Exception {
        // Advance incident to RESOLVED and then CLOSED
        incidentService.updateStatus(activeIncidentId, new IncidentStatusUpdateRequest(IncidentStatus.DISPATCHED, "Dispatched"));
        incidentService.updateStatus(activeIncidentId, new IncidentStatusUpdateRequest(IncidentStatus.EN_ROUTE, "En route"));
        incidentService.updateStatus(activeIncidentId, new IncidentStatusUpdateRequest(IncidentStatus.ON_SCENE, "On scene"));
        incidentService.updateStatus(activeIncidentId, new IncidentStatusUpdateRequest(IncidentStatus.UNDER_CONTROL, "Under control"));
        incidentService.updateStatus(activeIncidentId, new IncidentStatusUpdateRequest(IncidentStatus.RESOLVED, "Resolved"));
        incidentService.closeIncident(activeIncidentId, new IncidentCloseRequest("Incident formally closed"));

        // Attempt to create dispatch order against closed incident -> 422
        DispatchCreateRequest createRequest = new DispatchCreateRequest(
                activeIncidentId,
                stationId,
                DispatchPriority.HIGH,
                List.of(UUID.randomUUID()),
                List.of(),
                "Post-closure dispatch attempt"
        );

        mockMvc.perform(post("/api/v1/dispatch/orders")
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }
}
