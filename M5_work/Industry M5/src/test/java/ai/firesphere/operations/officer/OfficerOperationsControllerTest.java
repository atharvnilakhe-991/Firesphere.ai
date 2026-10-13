package ai.firesphere.operations.officer;

import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.operations.officer.dto.OfficerTaskCreateRequest;
import ai.firesphere.operations.officer.dto.OfficerTaskResponseDTO;
import ai.firesphere.operations.officer.dto.OfficerTaskStatusUpdateRequest;
import ai.firesphere.operations.officer.model.OfficerTaskStatus;
import ai.firesphere.operations.officer.model.OfficerTaskType;
import ai.firesphere.operations.officer.repository.OfficerTaskRepository;
import ai.firesphere.operations.officer.service.OfficerOperationsService;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OfficerOperationsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OfficerOperationsService operationsService;

    @Autowired
    private OfficerTaskRepository taskRepository;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        MockSecurityHelper.setupSecurityContext(TENANT_ID, "testAdmin", List.of("ROLE_ADMIN"));
        taskRepository.clear();
    }

    @AfterEach
    void tearDown() {
        MockSecurityHelper.clearSecurityContext();
        taskRepository.clear();
    }

    @Test
    @DisplayName("POST /api/v1/officer-operations/tasks returns 201 CREATED for valid task")
    void testCreateTask_Endpoint_Success() throws Exception {
        OfficerTaskCreateRequest request = new OfficerTaskCreateRequest(
                "TSK-CTRL-001",
                OfficerTaskType.INSPECTION,
                "Warehouse Safety Check",
                "Periodic review of facility fire barriers",
                "OF-501",
                UUID.randomUUID(),
                DispatchPriority.HIGH,
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200)
        );

        mockMvc.perform(post("/api/v1/officer-operations/tasks")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskReference", is("TSK-CTRL-001")))
                .andExpect(jsonPath("$.assignedOfficerBadge", is("OF-501")))
                .andExpect(jsonPath("$.status", is("ASSIGNED")))
                .andExpect(jsonPath("$.priority", is("HIGH")))
                .andExpect(jsonPath("$.taskId", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/officer-operations/tasks returns 400 BAD REQUEST on blank mandatory fields")
    void testCreateTask_Endpoint_ValidationFailure() throws Exception {
        OfficerTaskCreateRequest request = new OfficerTaskCreateRequest(
                "", // Blank reference
                OfficerTaskType.INSPECTION,
                "", // Blank title
                "Desc",
                null,
                null,
                DispatchPriority.LOW,
                null,
                null
        );

        mockMvc.perform(post("/api/v1/officer-operations/tasks")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/officer-operations/tasks returns 409 CONFLICT on duplicate task reference")
    void testCreateTask_Endpoint_Conflict() throws Exception {
        OfficerTaskCreateRequest request = new OfficerTaskCreateRequest(
                "TSK-CTRL-DUP",
                OfficerTaskType.INSPECTION,
                "Initial Inspection",
                "Desc",
                "OF-501",
                null,
                DispatchPriority.MEDIUM,
                null,
                null
        );

        TenantContext.setTenantId(TENANT_ID);
        operationsService.createTask(request);

        mockMvc.perform(post("/api/v1/officer-operations/tasks")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /api/v1/officer-operations/tasks/{id} returns 200 OK or 404 NOT FOUND")
    void testGetTaskById_Endpoint() throws Exception {
        TenantContext.setTenantId(TENANT_ID);
        OfficerTaskResponseDTO created = operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-CTRL-BYID", OfficerTaskType.INSPECTION, "Title", "Desc", "OF-501", null,
                DispatchPriority.MEDIUM, null, null
        ));

        mockMvc.perform(get("/api/v1/officer-operations/tasks/" + created.taskId())
                        .header("X-Tenant-Id", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskReference", is("TSK-CTRL-BYID")));

        mockMvc.perform(get("/api/v1/officer-operations/tasks/" + UUID.randomUUID())
                        .header("X-Tenant-Id", TENANT_ID.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/officer-operations/tasks/reference/{reference} returns 200 OK")
    void testGetTaskByReference_Endpoint() throws Exception {
        TenantContext.setTenantId(TENANT_ID);
        operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-CTRL-BYREF", OfficerTaskType.HYDRANT_SURVEY, "Survey", "Desc", "OF-501", null,
                DispatchPriority.LOW, null, null
        ));

        mockMvc.perform(get("/api/v1/officer-operations/tasks/reference/TSK-CTRL-BYREF")
                        .header("X-Tenant-Id", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskReference", is("TSK-CTRL-BYREF")));
    }

    @Test
    @DisplayName("GET /api/v1/officer-operations/tasks filters by query params")
    void testListTasks_Endpoint_WithFilters() throws Exception {
        TenantContext.setTenantId(TENANT_ID);
        operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-LIST-1", OfficerTaskType.INSPECTION, "T1", "D", "OF-501", null,
                DispatchPriority.HIGH, null, null
        ));
        operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-LIST-2", OfficerTaskType.INSPECTION, "T2", "D", "OF-602", null,
                DispatchPriority.LOW, null, null
        ));

        mockMvc.perform(get("/api/v1/officer-operations/tasks")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .param("officerBadge", "OF-501"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].taskReference", is("TSK-LIST-1")));
    }

    @Test
    @DisplayName("PUT /api/v1/officer-operations/tasks/{id}/status updates task state")
    void testUpdateStatus_Endpoint() throws Exception {
        TenantContext.setTenantId(TENANT_ID);
        OfficerTaskResponseDTO created = operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-STAT-EP", OfficerTaskType.INSPECTION, "T", "D", "OF-501", null,
                DispatchPriority.MEDIUM, null, null
        ));

        OfficerTaskStatusUpdateRequest update = new OfficerTaskStatusUpdateRequest(
                OfficerTaskStatus.IN_PROGRESS, "Officer en route"
        );

        mockMvc.perform(put("/api/v1/officer-operations/tasks/" + created.taskId() + "/status")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));
    }

    @Test
    @DisplayName("GET /api/v1/officer-operations/workload/{badge} returns workload summary")
    void testGetWorkload_Endpoint() throws Exception {
        TenantContext.setTenantId(TENANT_ID);
        operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-WL-EP", OfficerTaskType.INSPECTION, "T", "D", "OF-701", null,
                DispatchPriority.HIGH, null, null
        ));

        mockMvc.perform(get("/api/v1/officer-operations/workload/OF-701")
                        .header("X-Tenant-Id", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officerBadge", is("OF-701")))
                .andExpect(jsonPath("$.totalAssignedTasks", is(1)))
                .andExpect(jsonPath("$.workloadCapacityStatus", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/officer-operations/alerts/evaluate evaluates alerts")
    void testEvaluateAlerts_Endpoint() throws Exception {
        mockMvc.perform(post("/api/v1/officer-operations/alerts/evaluate")
                        .header("X-Tenant-Id", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/officer-operations/escalations/evaluate and history endpoints")
    void testEscalationEndpoints() throws Exception {
        TenantContext.setTenantId(TENANT_ID);
        OfficerTaskResponseDTO created = operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-ESC-CTRL", OfficerTaskType.INSPECTION, "T", "D", "OF-501", null,
                DispatchPriority.MEDIUM, null, null
        ));

        // Manual escalation
        mockMvc.perform(post("/api/v1/officer-operations/tasks/" + created.taskId() + "/escalate")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Chief request"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.escalatedToRole", is("STATION_CAPTAIN")));

        // History
        mockMvc.perform(get("/api/v1/officer-operations/escalations/history")
                        .header("X-Tenant-Id", TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].taskReference", is("TSK-ESC-CTRL")));
    }
}
