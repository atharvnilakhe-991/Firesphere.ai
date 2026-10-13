package ai.firesphere.fleet;

import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.fleet.dto.VehicleRegisterRequest;
import ai.firesphere.fleet.dto.VehicleStatusUpdateRequest;
import ai.firesphere.fleet.dto.VehicleUpdateRequest;
import ai.firesphere.fleet.model.VehicleStatus;
import ai.firesphere.fleet.model.VehicleType;
import ai.firesphere.fleet.repository.VehicleRepository;
import ai.firesphere.fleet.repository.VehicleStatusHistoryRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FleetIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private VehicleStatusHistoryRepository historyRepository;

    private final UUID tenantA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID tenantB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID stationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantA);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("End-to-End: Register, Retrieve, Update, Full Lifecycle, Audit & Readiness")
    void testFullVehicleLifecycleAndReadiness() throws Exception {
        String regNum = "MH-01-FS-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        // 1. Register vehicle
        VehicleRegisterRequest registerRequest = new VehicleRegisterRequest(
                regNum,
                "ENGINE-11",
                VehicleType.PUMPER_ENGINE,
                stationId,
                "Tata Motors",
                "Ultra Fire 4000",
                2023,
                4000,
                500,
                3000,
                "First due structural attack engine"
        );

        String createJson = mockMvc.perform(post("/api/v1/fleet/vehicles")
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.registrationNumber").value(regNum))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.available").value(true))
                .andReturn().getResponse().getContentAsString();

        UUID vehicleId = UUID.fromString(objectMapper.readTree(createJson).get("id").asText());

        // 2. Retrieve by ID
        mockMvc.perform(get("/api/v1/fleet/vehicles/{id}", vehicleId)
                        .header("X-Tenant-ID", tenantA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callSign").value("ENGINE-11"))
                .andExpect(jsonPath("$.make").value("Tata Motors"));

        // 3. Verify Initial Readiness
        mockMvc.perform(get("/api/v1/fleet/vehicles/{id}/readiness", vehicleId)
                        .header("X-Tenant-ID", tenantA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.readinessState").value("OPERATIONAL_READY"));

        // 4. Update mutable metadata (PUT)
        VehicleUpdateRequest updateRequest = new VehicleUpdateRequest(
                "HEAVY-ENGINE-11",
                null,
                null,
                null,
                null,
                4500,
                600,
                3500,
                "Upgraded high-pressure monitor"
        );
        mockMvc.perform(put("/api/v1/fleet/vehicles/{id}", vehicleId)
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callSign").value("HEAVY-ENGINE-11"))
                .andExpect(jsonPath("$.waterCapacityLiters").value(4500))
                .andExpect(jsonPath("$.registrationNumber").value(regNum)); // Registration remains unaltered

        // 5. Emergency Lifecycle: AVAILABLE -> DISPATCHED -> EN_ROUTE -> ON_SCENE -> AVAILABLE
        VehicleStatus[] steps = {
                VehicleStatus.DISPATCHED,
                VehicleStatus.EN_ROUTE,
                VehicleStatus.ON_SCENE,
                VehicleStatus.AVAILABLE
        };

        for (VehicleStatus step : steps) {
            mockMvc.perform(post("/api/v1/fleet/vehicles/{id}/status", vehicleId)
                            .header("X-Tenant-ID", tenantA.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new VehicleStatusUpdateRequest(step, "Transitioning to " + step))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(step.name()));
        }

        // 6. Verify History entries (1 initial + 4 transitions = 5 records)
        mockMvc.perform(get("/api/v1/fleet/vehicles/{id}/history", vehicleId)
                        .header("X-Tenant-ID", tenantA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    @DisplayName("Maintenance and Out-of-Service transitions & invalid jump rejection")
    void testMaintenanceAndOutOfServiceTransitions() throws Exception {
        String regNum = "MH-01-FS-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        VehicleRegisterRequest registerRequest = new VehicleRegisterRequest(
                regNum, "LADDER-2", VehicleType.AERIAL_LADDER, stationId,
                null, null, null, null, null, null, null
        );

        String createJson = mockMvc.perform(post("/api/v1/fleet/vehicles")
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID vehicleId = UUID.fromString(objectMapper.readTree(createJson).get("id").asText());

        // AVAILABLE -> OUT_OF_SERVICE
        mockMvc.perform(post("/api/v1/fleet/vehicles/{id}/status", vehicleId)
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VehicleStatusUpdateRequest(
                                VehicleStatus.OUT_OF_SERVICE, "Hydraulic arm leak"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"));

        // Verify Readiness indicates OUT_OF_SERVICE and available = false
        mockMvc.perform(get("/api/v1/fleet/vehicles/{id}/readiness", vehicleId)
                        .header("X-Tenant-ID", tenantA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.readinessState").value("OUT_OF_SERVICE"));

        // OUT_OF_SERVICE -> EN_ROUTE is forbidden (rejected with 422)
        mockMvc.perform(post("/api/v1/fleet/vehicles/{id}/status", vehicleId)
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VehicleStatusUpdateRequest(
                                VehicleStatus.EN_ROUTE, "Invalid emergency roll"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));

        // OUT_OF_SERVICE -> MAINTENANCE
        mockMvc.perform(post("/api/v1/fleet/vehicles/{id}/status", vehicleId)
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VehicleStatusUpdateRequest(
                                VehicleStatus.MAINTENANCE, "Moved to workshop"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));

        // MAINTENANCE -> AVAILABLE
        mockMvc.perform(post("/api/v1/fleet/vehicles/{id}/status", vehicleId)
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VehicleStatusUpdateRequest(
                                VehicleStatus.AVAILABLE, "Hydraulics repaired and recertified"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.available").value(true));

        // Same-status transition (AVAILABLE -> AVAILABLE) must be rejected with 422
        mockMvc.perform(post("/api/v1/fleet/vehicles/{id}/status", vehicleId)
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VehicleStatusUpdateRequest(
                                VehicleStatus.AVAILABLE, "Redundant transition"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));

        // Verify history entries: 1 initial registration + 3 valid transitions (OUT_OF_SERVICE, MAINTENANCE, AVAILABLE) = 4 records.
        // The rejected invalid jump (OUT_OF_SERVICE -> EN_ROUTE) and rejected same-status transition created 0 history records.
        mockMvc.perform(get("/api/v1/fleet/vehicles/{id}/history", vehicleId)
                        .header("X-Tenant-ID", tenantA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    @DisplayName("Duplicate registration number rejection (409)")
    void testDuplicateRegistrationRejection() throws Exception {
        String regNum = "MH-01-FS-DUPLICATE";

        VehicleRegisterRequest v1 = new VehicleRegisterRequest(
                regNum, "TANKER-1", VehicleType.WATER_TANKER, stationId,
                null, null, null, 10000, null, 2000, null
        );
        mockMvc.perform(post("/api/v1/fleet/vehicles")
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(v1)))
                .andExpect(status().isCreated());

        // Attempt duplicate
        VehicleRegisterRequest v2 = new VehicleRegisterRequest(
                regNum, "TANKER-2", VehicleType.WATER_TANKER, stationId,
                null, null, null, 10000, null, 2000, null
        );
        mockMvc.perform(post("/api/v1/fleet/vehicles")
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(v2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STATE_CONFLICT"));
    }

    @Test
    @DisplayName("Tenant Isolation: Tenant B cannot access Tenant A vehicle records")
    void testTenantIsolation() throws Exception {
        String regNum = "MH-01-FS-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        VehicleRegisterRequest v1 = new VehicleRegisterRequest(
                regNum, "RESCUE-1", VehicleType.HEAVY_RESCUE, stationId,
                null, null, null, null, null, null, null
        );
        String createJson = mockMvc.perform(post("/api/v1/fleet/vehicles")
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(v1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID vehicleId = UUID.fromString(objectMapper.readTree(createJson).get("id").asText());

        // Tenant B cannot get vehicle -> 404
        mockMvc.perform(get("/api/v1/fleet/vehicles/{id}", vehicleId)
                        .header("X-Tenant-ID", tenantB.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // Tenant B cannot mutate vehicle -> 404
        mockMvc.perform(put("/api/v1/fleet/vehicles/{id}", vehicleId)
                        .header("X-Tenant-ID", tenantB.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VehicleUpdateRequest("HIJACK", null, null, null, null, null, null, null, null))))
                .andExpect(status().isNotFound());

        // Tenant B cannot transition status -> 404
        mockMvc.perform(post("/api/v1/fleet/vehicles/{id}/status", vehicleId)
                        .header("X-Tenant-ID", tenantB.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VehicleStatusUpdateRequest(VehicleStatus.OUT_OF_SERVICE, "Sabotage"))))
                .andExpect(status().isNotFound());

        // Tenant B cannot view history -> 404
        mockMvc.perform(get("/api/v1/fleet/vehicles/{id}/history", vehicleId)
                        .header("X-Tenant-ID", tenantB.toString()))
                .andExpect(status().isNotFound());

        // Tenant B cannot view readiness -> 404
        mockMvc.perform(get("/api/v1/fleet/vehicles/{id}/readiness", vehicleId)
                        .header("X-Tenant-ID", tenantB.toString()))
                .andExpect(status().isNotFound());
    }
}
