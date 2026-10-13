package ai.firesphere.mobile;

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
import com.fasterxml.jackson.core.type.TypeReference;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mobile QA Suite: Field Officer Operations & Mobile Task Management.
 * Validates mobile officer workflows: assigned task retrieval with filtering,
 * sequential status progression, cross-officer workload privacy, delta queries
 * for offline sync, and duplicate submission protections.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MobileOfficerWorkflowQATest {

    private static final UUID MOBILE_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String OFFICER_PRIMARY = "OF-101";
    private static final String OFFICER_SECONDARY = "OF-202";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OfficerOperationsService operationsService;

    @Autowired
    private OfficerTaskRepository taskRepository;

    @BeforeEach
    void setUp() {
        taskRepository.clear();
        MockSecurityHelper.setupSecurityContext(MOBILE_TENANT_ID, OFFICER_PRIMARY, List.of("ROLE_OFFICER"));
    }

    @AfterEach
    void tearDown() {
        MockSecurityHelper.clearSecurityContext();
        taskRepository.clear();
    }

    @Test
    @DisplayName("Mobile Officer: Retrieve assigned tasks and filter by officer badge")
    void testMobileOfficerTaskListRetrievalAndBadgeFiltering() throws Exception {
        Instant now = Instant.now();
        operationsService.createTask(new OfficerTaskCreateRequest(
                "MOB-TSK-101",
                OfficerTaskType.INSPECTION,
                "Tower 1 Hydrant Inspection",
                "Inspect ground floor and basement booster pumps",
                OFFICER_PRIMARY,
                null,
                DispatchPriority.HIGH,
                now.plus(1, ChronoUnit.HOURS),
                now.plus(24, ChronoUnit.HOURS)
        ));

        // Create a second task as supervisor for OFFICER_SECONDARY
        MockSecurityHelper.setupSecurityContext(MOBILE_TENANT_ID, "supervisor", List.of("ROLE_ADMIN"));
        operationsService.createTask(new OfficerTaskCreateRequest(
                "MOB-TSK-202",
                OfficerTaskType.OPERATIONAL_PATROL,
                "Station 4 Ladder Check",
                "Routine pre-shift inspection",
                OFFICER_SECONDARY,
                null,
                DispatchPriority.MEDIUM,
                now.plus(2, ChronoUnit.HOURS),
                now.plus(48, ChronoUnit.HOURS)
        ));

        // Switch back to OFFICER_PRIMARY mobile session
        MockSecurityHelper.setupSecurityContext(MOBILE_TENANT_ID, OFFICER_PRIMARY, List.of("ROLE_OFFICER"));

        // Act: Query tasks specifically assigned to OFFICER_PRIMARY
        MvcResult result = mockMvc.perform(get("/api/v1/officer-operations/tasks")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .param("officerBadge", OFFICER_PRIMARY)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        List<OfficerTaskResponseDTO> tasks = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );

        // Assert: Mobile task list only contains tasks for OF-101 with all mobile display attributes
        assertThat(tasks).hasSize(1);
        OfficerTaskResponseDTO task = tasks.get(0);
        assertThat(task.taskReference()).isEqualTo("MOB-TSK-101");
        assertThat(task.assignedOfficerBadge()).isEqualTo(OFFICER_PRIMARY);
        assertThat(task.title()).isEqualTo("Tower 1 Hydrant Inspection");
        assertThat(task.status()).isEqualTo(OfficerTaskStatus.ASSIGNED);
        assertThat(task.priority()).isEqualTo(DispatchPriority.HIGH);
        assertThat(task.scheduledAt()).isNotNull();
        assertThat(task.deadlineAt()).isNotNull();
    }

    @Test
    @DisplayName("Mobile Officer: Sequential task progression from ASSIGNED -> IN_PROGRESS -> COMPLETED")
    void testMobileOfficerSequentialTaskStatusProgression() throws Exception {
        Instant now = Instant.now();
        OfficerTaskResponseDTO created = operationsService.createTask(new OfficerTaskCreateRequest(
                "MOB-PROG-01",
                OfficerTaskType.HAZARD_VERIFICATION,
                "Hospital Safety Audit",
                "Verify oxygen manifold alarms and fire escape routes",
                OFFICER_PRIMARY,
                null,
                DispatchPriority.HIGH,
                now.plus(1, ChronoUnit.HOURS),
                now.plus(12, ChronoUnit.HOURS)
        ));

        // Step 1: Officer starts inspection on mobile (ASSIGNED -> IN_PROGRESS)
        OfficerTaskStatusUpdateRequest inProgressReq = new OfficerTaskStatusUpdateRequest(
                OfficerTaskStatus.IN_PROGRESS,
                "Mobile officer checked in on-site at hospital entrance"
        );

        mockMvc.perform(put("/api/v1/officer-operations/tasks/{id}/status", created.taskId())
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inProgressReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        // Step 2: Officer completes audit on mobile (IN_PROGRESS -> COMPLETED)
        OfficerTaskStatusUpdateRequest completeReq = new OfficerTaskStatusUpdateRequest(
                OfficerTaskStatus.COMPLETED,
                "Audit complete: all manifold valves operating normally, exit corridors clear"
        );

        mockMvc.perform(put("/api/v1/officer-operations/tasks/{id}/status", created.taskId())
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
    }

    @Test
    @DisplayName("Mobile Officer: Cross-officer task access is denied (HTTP 403 ACCESS_DENIED)")
    void testMobileOfficerCrossBadgeAccessDenied() throws Exception {
        Instant now = Instant.now();
        OfficerTaskResponseDTO task = operationsService.createTask(new OfficerTaskCreateRequest(
                "MOB-ISOL-01",
                OfficerTaskType.INSPECTION,
                "Restricted Office Building Inspection",
                "Classified government research center",
                OFFICER_PRIMARY,
                null,
                DispatchPriority.CRITICAL,
                now.plus(1, ChronoUnit.HOURS),
                now.plus(24, ChronoUnit.HOURS)
        ));

        // Switch mobile session to OFFICER_SECONDARY attempting to access or mutate task
        MockSecurityHelper.setupSecurityContext(MOBILE_TENANT_ID, OFFICER_SECONDARY, List.of("ROLE_OFFICER"));

        // 1. Attempt reading task by ID
        mockMvc.perform(get("/api/v1/officer-operations/tasks/{id}", task.taskId())
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        // 2. Attempt updating status
        OfficerTaskStatusUpdateRequest updateReq = new OfficerTaskStatusUpdateRequest(
                OfficerTaskStatus.IN_PROGRESS,
                "Unauthorized tampering attempt"
        );
        mockMvc.perform(put("/api/v1/officer-operations/tasks/{id}/status", task.taskId())
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Mobile Officer: Workload and capacity summary query")
    void testMobileOfficerWorkloadSummaryQuery() throws Exception {
        Instant now = Instant.now();
        operationsService.createTask(new OfficerTaskCreateRequest(
                "MOB-WL-01",
                OfficerTaskType.HYDRANT_SURVEY,
                "SCBA Cylinder Pressure Testing",
                "Hydrostatic cylinder tests",
                OFFICER_PRIMARY,
                null,
                DispatchPriority.MEDIUM,
                now.plus(1, ChronoUnit.HOURS),
                now.plus(24, ChronoUnit.HOURS)
        ));
        operationsService.createTask(new OfficerTaskCreateRequest(
                "MOB-WL-02",
                OfficerTaskType.OPERATIONAL_PATROL,
                "Station Generator Test",
                "Weekly backup power test",
                OFFICER_PRIMARY,
                null,
                DispatchPriority.LOW,
                now.plus(2, ChronoUnit.HOURS),
                now.plus(48, ChronoUnit.HOURS)
        ));

        // Act: Mobile client requests officer workload summary
        mockMvc.perform(get("/api/v1/officer-operations/workload/{officerBadge}", OFFICER_PRIMARY)
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officerBadge").value(OFFICER_PRIMARY))
                .andExpect(jsonPath("$.totalAssignedTasks").value(2))
                .andExpect(jsonPath("$.workloadCapacityStatus").value("OPTIMAL"));
    }

    @Test
    @DisplayName("Mobile Offline Sync: Delta query for task synchronization by date range")
    void testMobileOfflineSyncDeltaQuery() throws Exception {
        Instant now = Instant.now();
        operationsService.createTask(new OfficerTaskCreateRequest(
                "MOB-SYNC-TODAY",
                OfficerTaskType.INSPECTION,
                "Mall Evacuation Route Check",
                "Verify emergency stairwells",
                OFFICER_PRIMARY,
                null,
                DispatchPriority.HIGH,
                now.plus(2, ChronoUnit.HOURS),
                now.plus(10, ChronoUnit.HOURS)
        ));

        Instant windowStart = now.minus(1, ChronoUnit.HOURS);
        Instant windowEnd = now.plus(12, ChronoUnit.HOURS);

        mockMvc.perform(get("/api/v1/officer-operations/tasks")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .param("officerBadge", OFFICER_PRIMARY)
                        .param("fromDate", windowStart.toString())
                        .param("toDate", windowEnd.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].taskReference").value("MOB-SYNC-TODAY"));

        Instant futureStart = now.plus(30, ChronoUnit.DAYS);
        Instant futureEnd = now.plus(31, ChronoUnit.DAYS);

        mockMvc.perform(get("/api/v1/officer-operations/tasks")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .param("officerBadge", OFFICER_PRIMARY)
                        .param("fromDate", futureStart.toString())
                        .param("toDate", futureEnd.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Mobile Officer: Retrieve task by unique reference identifier")
    void testMobileOfficerTaskDetailByReference() throws Exception {
        Instant now = Instant.now();
        operationsService.createTask(new OfficerTaskCreateRequest(
                "MOB-REF-99",
                OfficerTaskType.EVACUATION_CHECK,
                "Post-Incident Arson Investigation",
                "Examine origin patterns in warehouse bay 3",
                OFFICER_PRIMARY,
                null,
                DispatchPriority.CRITICAL,
                now.plus(1, ChronoUnit.HOURS),
                now.plus(18, ChronoUnit.HOURS)
        ));

        mockMvc.perform(get("/api/v1/officer-operations/tasks/reference/{reference}", "MOB-REF-99")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskReference").value("MOB-REF-99"))
                .andExpect(jsonPath("$.title").value("Post-Incident Arson Investigation"))
                .andExpect(jsonPath("$.priority").value("CRITICAL"));
    }

    @Test
    @DisplayName("Mobile Network Retry: Duplicate task creation request rejected with HTTP 409 Conflict")
    void testMobileDuplicateTaskSubmissionConflict() throws Exception {
        Instant now = Instant.now();
        OfficerTaskCreateRequest createReq = new OfficerTaskCreateRequest(
                "MOB-IDEMP-01",
                OfficerTaskType.OPERATIONAL_PATROL,
                "Foam Proportioner Test",
                "Pre-incident equipment calibration",
                OFFICER_PRIMARY,
                null,
                DispatchPriority.MEDIUM,
                now.plus(1, ChronoUnit.HOURS),
                now.plus(24, ChronoUnit.HOURS)
        );

        // First attempt succeeds
        mockMvc.perform(post("/api/v1/officer-operations/tasks")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated());

        // Retrying exact same request -> HTTP 409 Conflict with STATE_CONFLICT error code
        mockMvc.perform(post("/api/v1/officer-operations/tasks")
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STATE_CONFLICT"));
    }

    @Test
    @DisplayName("Mobile Client: Task not found returns HTTP 404 with standard ErrorResponse")
    void testMobileTaskNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/officer-operations/tasks/{id}", nonExistentId)
                        .header("X-Tenant-Id", MOBILE_TENANT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
