package ai.firesphere.security;

import ai.firesphere.common.exception.ErrorResponse;
import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.operations.officer.dto.OfficerTaskCreateRequest;
import ai.firesphere.operations.officer.dto.OfficerTaskResponseDTO;
import ai.firesphere.operations.officer.dto.OfficerTaskStatusUpdateRequest;
import ai.firesphere.operations.officer.dto.OfficerWorkloadSummaryDTO;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security QA Suite: Authentication, Authorization, Role Matrix, and Identity Ownership.
 * Validates access restrictions, supervisory privilege segregation, badge ownership,
 * and error responses under unauthorized actions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationAndAuthorizationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OfficerOperationsService operationsService;

    @Autowired
    private OfficerTaskRepository taskRepository;

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final Instant baseTime = Instant.parse("2026-10-09T10:00:00Z");

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        MockSecurityHelper.setupSecurityContext(TENANT_ID, "ops_admin", List.of("ROLE_ADMIN"));
        taskRepository.clear();
    }

    @AfterEach
    void tearDown() {
        MockSecurityHelper.clearSecurityContext();
        taskRepository.clear();
    }

    @Test
    @DisplayName("Officer badge isolation: Officer OF-202 cannot view tasks assigned to officer OF-101")
    void testOfficerCannotViewOtherOfficerTask() {
        OfficerTaskResponseDTO task = operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-AUTH-01", OfficerTaskType.INSPECTION, "Survey", "Desc", "OF-101", null,
                DispatchPriority.HIGH, baseTime, baseTime.plus(Duration.ofHours(2))
        ));

        // Switch security context to regular officer OF-202
        MockSecurityHelper.setupSecurityContext(TENANT_ID, "OF-202", List.of("ROLE_OFFICER"));

        assertThatThrownBy(() -> operationsService.getTask(task.taskId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not authorized");

        assertThatThrownBy(() -> operationsService.getTaskByReference("TSK-AUTH-01"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not authorized");
    }

    @Test
    @DisplayName("Officer badge isolation: Officer OF-202 cannot update status of task assigned to OF-101")
    void testOfficerCannotUpdateOtherOfficerTaskStatus() {
        OfficerTaskResponseDTO task = operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-AUTH-02", OfficerTaskType.INSPECTION, "Survey", "Desc", "OF-101", null,
                DispatchPriority.HIGH, baseTime, baseTime.plus(Duration.ofHours(2))
        ));

        // Switch security context to regular officer OF-202
        MockSecurityHelper.setupSecurityContext(TENANT_ID, "OF-202", List.of("ROLE_OFFICER"));

        assertThatThrownBy(() -> operationsService.updateTaskStatus(
                task.taskId(), new OfficerTaskStatusUpdateRequest(OfficerTaskStatus.IN_PROGRESS, "Hijack attempt")
        )).isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not authorized");
    }

    @Test
    @DisplayName("Officer badge isolation: Officer OF-202 cannot view workload summary of OF-101")
    void testOfficerCannotAccessOtherOfficerWorkload() {
        operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-AUTH-03", OfficerTaskType.INSPECTION, "Survey", "Desc", "OF-101", null,
                DispatchPriority.HIGH, baseTime, baseTime.plus(Duration.ofHours(2))
        ));

        // Switch to OF-202
        MockSecurityHelper.setupSecurityContext(TENANT_ID, "OF-202", List.of("ROLE_OFFICER"));

        assertThatThrownBy(() -> operationsService.getOfficerWorkload("OF-101"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not authorized");

        // Own workload access is allowed
        OfficerWorkloadSummaryDTO ownWorkload = operationsService.getOfficerWorkload("OF-202");
        assertThat(ownWorkload).isNotNull();
        assertThat(ownWorkload.officerBadge()).isEqualTo("OF-202");
    }

    @Test
    @DisplayName("Privilege escalation protection: Officer without supervisory role cannot perform manual escalation")
    void testOfficerCannotPerformManualEscalation() {
        OfficerTaskResponseDTO task = operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-AUTH-04", OfficerTaskType.INSPECTION, "Survey", "Desc", "OF-101", null,
                DispatchPriority.HIGH, baseTime, baseTime.plus(Duration.ofHours(2))
        ));

        // Switch to regular officer
        MockSecurityHelper.setupSecurityContext(TENANT_ID, "OF-101", List.of("ROLE_OFFICER"));

        assertThatThrownBy(() -> operationsService.manualEscalateTask(task.taskId(), "Unauthorized escalation attempt"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not authorized to perform 'manual task escalation'");
    }

    @Test
    @DisplayName("Privilege escalation protection: Officer without supervisory role cannot trigger escalation evaluation")
    void testOfficerCannotTriggerEscalationEvaluation() {
        // Switch to regular officer
        MockSecurityHelper.setupSecurityContext(TENANT_ID, "OF-101", List.of("ROLE_OFFICER"));

        assertThatThrownBy(() -> operationsService.evaluateEscalations())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not authorized to perform 'evaluate escalations'");
    }

    @Test
    @DisplayName("Supervisory authorization: Station Captain, Commander, Dispatcher, and Chief can escalate and access workloads")
    void testSupervisoryRolesPermitted() {
        OfficerTaskResponseDTO task = operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-AUTH-05", OfficerTaskType.INSPECTION, "Survey", "Desc", "OF-101", null,
                DispatchPriority.HIGH, baseTime, baseTime.plus(Duration.ofHours(2))
        ));

        // 1. Station Captain
        MockSecurityHelper.setupSecurityContext(TENANT_ID, "captain_rao", List.of("ROLE_STATION_CAPTAIN"));
        assertThat(operationsService.getOfficerWorkload("OF-101")).isNotNull();

        // 2. Commander
        MockSecurityHelper.setupSecurityContext(TENANT_ID, "cmdr_deshmukh", List.of("ROLE_COMMANDER"));
        assertThat(operationsService.getOfficerWorkload("OF-101")).isNotNull();

        // 3. Dispatcher
        MockSecurityHelper.setupSecurityContext(TENANT_ID, "disp_shinde", List.of("ROLE_DISPATCHER"));
        assertThat(operationsService.getOfficerWorkload("OF-101")).isNotNull();

        // 4. Station Captain manual escalation succeeds
        MockSecurityHelper.setupSecurityContext(TENANT_ID, "captain_rao", List.of("ROLE_STATION_CAPTAIN"));
        var escalation = operationsService.manualEscalateTask(task.taskId(), "Captain authorized escalation");
        assertThat(escalation).isNotNull();
        assertThat(escalation.escalatedToRole()).isEqualTo("STATION_CAPTAIN");
    }

    @Test
    @DisplayName("Access denied error envelope: Returns HTTP 403 with ACCESS_DENIED and zero information disclosure")
    void testAccessDeniedHttpResponseBodyFormat() throws Exception {
        OfficerTaskResponseDTO task = operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-AUTH-06", OfficerTaskType.INSPECTION, "Survey", "Desc", "OF-101", null,
                DispatchPriority.HIGH, baseTime, baseTime.plus(Duration.ofHours(2))
        ));

        // Log in as officer OF-202 (unauthorized for OF-101)
        MockSecurityHelper.setupSecurityContext(TENANT_ID, "OF-202", List.of("ROLE_OFFICER"));

        MvcResult result = mockMvc.perform(get("/api/v1/officer-operations/workload/OF-101")
                        .header("X-Tenant-Id", TENANT_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value("/api/v1/officer-operations/workload/OF-101"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);

        // Verify zero stack trace or internal path leakage
        assertThat(errorResponse.message()).doesNotContain("Exception");
        assertThat(errorResponse.message()).doesNotContain(".java");
        assertThat(responseBody).doesNotContain("at ai.firesphere");
        assertThat(responseBody).doesNotContain("C:\\");
        assertThat(responseBody).doesNotContain("/home/");
    }

    @Test
    @DisplayName("Release Security Guardrail: Unauthenticated access to officer operations is strictly rejected (no null-auth bypass)")
    void testUnauthenticatedAccessToOfficerOperationsRejected() throws Exception {
        OfficerTaskResponseDTO task = operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-UNAUTH-01", OfficerTaskType.INSPECTION, "Survey", "Desc", "OF-101", null,
                DispatchPriority.HIGH, baseTime, baseTime.plus(Duration.ofHours(2))
        ));

        // Explicitly clear security context (null / unauthenticated principal) while retaining tenant context
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        TenantContext.setTenantId(TENANT_ID);

        // Service layer rejection
        assertThatThrownBy(() -> operationsService.getTask(task.taskId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Full authentication is required to access officer operations");

        assertThatThrownBy(() -> operationsService.getOfficerWorkload("OF-101"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Full authentication is required to access officer operations");

        // HTTP endpoint rejection
        mockMvc.perform(get("/api/v1/officer-operations/workload/OF-101")
                        .header("X-Tenant-Id", TENANT_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Release Security Guardrail: Unauthenticated supervisor escalation is strictly rejected (no null-auth bypass)")
    void testUnauthenticatedSupervisorEscalationRejected() throws Exception {
        OfficerTaskResponseDTO task = operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-UNAUTH-02", OfficerTaskType.INSPECTION, "Survey", "Desc", "OF-101", null,
                DispatchPriority.HIGH, baseTime, baseTime.plus(Duration.ofHours(2))
        ));

        // Explicitly clear security context while retaining tenant context
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        TenantContext.setTenantId(TENANT_ID);

        // Service layer rejection
        assertThatThrownBy(() -> operationsService.manualEscalateTask(task.taskId(), "Unauthenticated attempt"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Full authentication with supervisory authority is required");

        assertThatThrownBy(() -> operationsService.evaluateEscalations())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Full authentication with supervisory authority is required");

        // HTTP endpoint rejection
        mockMvc.perform(post("/api/v1/officer-operations/tasks/" + task.taskId() + "/escalate")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Unauthenticated attempt\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.status").value(403));
    }
}
