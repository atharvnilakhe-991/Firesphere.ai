package ai.firesphere.fleet;

import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.dispatch.model.DispatchOrder;
import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.dispatch.model.DispatchStatus;
import ai.firesphere.dispatch.repository.DispatchOrderRepository;
import ai.firesphere.fleet.dto.VehicleRegisterRequest;
import ai.firesphere.fleet.dto.VehicleStatusUpdateRequest;
import ai.firesphere.fleet.model.Vehicle;
import ai.firesphere.fleet.model.VehicleStatus;
import ai.firesphere.fleet.model.VehicleType;
import ai.firesphere.fleet.repository.VehicleRepository;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression suite proving strict decoupling and phase boundaries between
 * Fleet Management (Phase 4) and Emergency Dispatch (Phase 3 locked).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FleetDispatchBoundaryRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private DispatchOrderRepository dispatchOrderRepository;

    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID stationId = UUID.randomUUID();
    private final UUID incidentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Boundary: Fleet status transitions do NOT alter Dispatch order records")
    void testFleetStatusChange_DoesNotModifyDispatchOrder() throws Exception {
        // 1. Register vehicle in Fleet
        VehicleRegisterRequest regRequest = new VehicleRegisterRequest(
                "MH-01-FS-BND1", "ENGINE-BND1", VehicleType.PUMPER_ENGINE, stationId,
                null, null, null, null, null, null, null
        );

        String createJson = mockMvc.perform(post("/api/v1/fleet/vehicles")
                        .header("X-Tenant-ID", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID vehicleId = UUID.fromString(objectMapper.readTree(createJson).get("id").asText());

        // 2. Persist an active Dispatch order referencing this vehicle UUID
        DispatchOrder order = new DispatchOrder();
        order.setTenantId(tenantId);
        order.setIncidentId(incidentId);
        order.setStationId(stationId);
        order.setDispatchCode("DSP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setStatus(DispatchStatus.DISPATCHED);
        order.setPriority(DispatchPriority.HIGH);
        order.setAssignedVehicleIds(new ArrayList<>(List.of(vehicleId)));
        order.setDispatchedAt(Instant.now());
        order.setOperationalNotes("Active dispatch unit");
        DispatchOrder savedOrder = dispatchOrderRepository.save(order);
        Instant originalUpdatedAt = savedOrder.getUpdatedAt();

        // 3. Mutate Fleet vehicle status to MAINTENANCE
        mockMvc.perform(post("/api/v1/fleet/vehicles/{id}/status", vehicleId)
                        .header("X-Tenant-ID", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VehicleStatusUpdateRequest(
                                VehicleStatus.MAINTENANCE, "Emergency mechanical fault"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));

        // 4. Verify Dispatch order remains completely unaltered
        DispatchOrder freshOrder = dispatchOrderRepository.findByIdAndTenantId(savedOrder.getId(), tenantId).orElseThrow();
        assertEquals(DispatchStatus.DISPATCHED, freshOrder.getStatus(), "Dispatch status must remain unaffected");
        assertEquals("Active dispatch unit", freshOrder.getOperationalNotes(), "Dispatch notes must remain unaffected");
        assertEquals(1, freshOrder.getAssignedVehicleIds().size());
        assertEquals(vehicleId, freshOrder.getAssignedVehicleIds().getFirst());
        assertEquals(originalUpdatedAt.toEpochMilli(), freshOrder.getUpdatedAt().toEpochMilli(), "Dispatch entity must not be updated by Fleet changes");
    }

    @Test
    @DisplayName("Boundary: Fleet vehicle intake does NOT automatically assign vehicle to Dispatch")
    void testFleetIntake_DoesNotAutoAssignToDispatch() throws Exception {
        // 1. Create Dispatch order without vehicles
        DispatchOrder order = new DispatchOrder();
        order.setTenantId(tenantId);
        order.setIncidentId(incidentId);
        order.setStationId(stationId);
        order.setDispatchCode("DSP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setStatus(DispatchStatus.CREATED);
        order.setPriority(DispatchPriority.MEDIUM);
        order.setAssignedVehicleIds(new ArrayList<>());
        order.setDispatchedAt(Instant.now());
        order.setOperationalNotes("Awaiting manual unit allocation");
        DispatchOrder savedOrder = dispatchOrderRepository.save(order);
        assertTrue(savedOrder.getAssignedVehicleIds().isEmpty());

        // 2. Register brand new vehicle at the exact same fire station
        VehicleRegisterRequest regRequest = new VehicleRegisterRequest(
                "MH-01-FS-BND2", "RESCUE-BND2", VehicleType.HEAVY_RESCUE, stationId,
                null, null, null, null, null, null, null
        );
        mockMvc.perform(post("/api/v1/fleet/vehicles")
                        .header("X-Tenant-ID", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        // 3. Verify Dispatch order still has 0 assigned vehicles
        DispatchOrder freshOrder = dispatchOrderRepository.findByIdAndTenantId(savedOrder.getId(), tenantId).orElseThrow();
        assertTrue(freshOrder.getAssignedVehicleIds().isEmpty(),
                "Dispatch orders must not automatically acquire newly registered Fleet vehicles");
    }

    @Test
    @DisplayName("Boundary: Dispatch lifecycle progression does NOT mutate Fleet vehicle state")
    void testDispatchProgression_DoesNotMutateFleetState() throws Exception {
        // 1. Register vehicle in Fleet with AVAILABLE status
        VehicleRegisterRequest regRequest = new VehicleRegisterRequest(
                "MH-01-FS-BND3", "LADDER-BND3", VehicleType.AERIAL_LADDER, stationId,
                null, null, null, null, null, null, null
        );
        String createJson = mockMvc.perform(post("/api/v1/fleet/vehicles")
                        .header("X-Tenant-ID", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID vehicleId = UUID.fromString(objectMapper.readTree(createJson).get("id").asText());

        // 2. Simulate Dispatch order moving to ARRIVED and COMPLETED
        DispatchOrder order = new DispatchOrder();
        order.setTenantId(tenantId);
        order.setIncidentId(incidentId);
        order.setStationId(stationId);
        order.setDispatchCode("DSP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setStatus(DispatchStatus.ARRIVED);
        order.setPriority(DispatchPriority.HIGH);
        order.setAssignedVehicleIds(new ArrayList<>(List.of(vehicleId)));
        order.setDispatchedAt(Instant.now());
        order.setArrivedAt(Instant.now());
        order.setOperationalNotes("Apparatus at scene");
        dispatchOrderRepository.save(order);

        // Progress order to COMPLETED
        order.setStatus(DispatchStatus.COMPLETED);
        order.setCompletedAt(Instant.now());
        dispatchOrderRepository.save(order);

        // 3. Verify Fleet vehicle state remains AVAILABLE and unmodified
        Vehicle vehicle = vehicleRepository.findByIdAndTenantId(vehicleId, tenantId).orElseThrow();
        assertEquals(VehicleStatus.AVAILABLE, vehicle.getStatus(),
                "Fleet vehicle status must not be automatically overwritten by Dispatch state progression");
        assertTrue(vehicle.isAvailable());
    }
}
