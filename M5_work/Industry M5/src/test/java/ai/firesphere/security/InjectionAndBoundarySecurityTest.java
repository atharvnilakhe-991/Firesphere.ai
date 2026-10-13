package ai.firesphere.security;

import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.operations.officer.dto.OfficerTaskCreateRequest;
import ai.firesphere.operations.officer.dto.OfficerTaskResponseDTO;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security QA Suite: Injection and Request-Boundary Checks.
 * Evaluates application resilience against SQL injection payloads in query parameters and references,
 * XSS/HTML tag payloads in text fields, and oversized payload boundaries.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InjectionAndBoundarySecurityTest {

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
        MockSecurityHelper.setupSecurityContext(TENANT_ID, "security_officer", List.of("ROLE_ADMIN", "ROLE_DISPATCHER", "ROLE_COMMANDER"));
        taskRepository.clear();
    }

    @AfterEach
    void tearDown() {
        MockSecurityHelper.clearSecurityContext();
        TenantContext.clear();
        taskRepository.clear();
    }

    @Test
    @DisplayName("Injection Resistance: SQL injection payloads in search/filter and reference fields execute safely without data leaks")
    void testSqlInjectionInSearchAndFilterParameters() {
        String sqlInjectionRef = "TSK-' OR '1'='1' --";
        String sqlDropPayload = "'; DROP TABLE officer_tasks; --";

        // 1. Create task with injection payload in reference
        OfficerTaskResponseDTO created = operationsService.createTask(new OfficerTaskCreateRequest(
                sqlInjectionRef, OfficerTaskType.INSPECTION, "Sql Test", "Desc", "OF-101", null,
                DispatchPriority.HIGH, baseTime, baseTime.plus(Duration.ofHours(2))
        ));
        assertThat(created.taskReference()).isEqualTo(sqlInjectionRef);

        // 2. Fetch by exact reference succeeds without SQL syntax error or universal matching
        OfficerTaskResponseDTO fetched = operationsService.getTaskByReference(sqlInjectionRef);
        assertThat(fetched.taskId()).isEqualTo(created.taskId());

        // 3. Search with SQL DROP payload returns only matching records (none in this case) without executing SQL
        List<OfficerTaskResponseDTO> results = operationsService.getTasks(sqlDropPayload, null, null, null, null);
        assertThat(results).isEmpty();

        // 4. Verify table / repository was not dropped or compromised
        assertThat(operationsService.getTasks(null, null, null, null, null)).hasSize(1);
    }

    @Test
    @DisplayName("Payload Safety: XSS script tags and HTML markup in text fields are stored as literal text without execution")
    void testXssAndSpecialCharactersHandling() {
        String xssScript = "<script>alert('XSS_ATTACK')</script>";
        String xssImg = "<img src=x onerror=alert('document.cookie') />";
        String unicodeInjection = "🔥 Fire Hazard — \uD83D\uDCA3 Chemical Tank Pier-44 \u0000 Test";

        OfficerTaskResponseDTO task = operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-XSS-01", OfficerTaskType.HAZARD_VERIFICATION, xssScript, xssImg, "OF-101", null,
                DispatchPriority.HIGH, baseTime, baseTime.plus(Duration.ofHours(2))
        ));

        // Verify stored text is safely preserved verbatim as text data
        assertThat(task.title()).isEqualTo(xssScript);
        assertThat(task.description()).isEqualTo(xssImg);

        // Fetch back and assert verbatim integrity
        OfficerTaskResponseDTO retrieved = operationsService.getTask(task.taskId());
        assertThat(retrieved.title()).isEqualTo(xssScript);
        assertThat(retrieved.description()).isEqualTo(xssImg);
    }

    @Test
    @DisplayName("Boundary Validation: Excessively long strings exceeding field length are rejected with HTTP 400 VALIDATION_FAILED")
    void testExcessivelyLongStringInputsRejected() throws Exception {
        // title has @Size(max = 200)
        String oversizedTitle = "A".repeat(250);

        String oversizedPayload = """
                {
                    "taskReference": "TSK-OVERSIZED",
                    "taskType": "INSPECTION",
                    "title": "%s",
                    "assignedOfficerBadge": "OF-101",
                    "priority": "HIGH",
                    "scheduledAt": "2026-10-09T10:00:00Z",
                    "deadlineAt": "2026-10-09T12:00:00Z"
                }
                """.formatted(oversizedTitle);

        mockMvc.perform(post("/api/v1/officer-operations/tasks")
                        .header("X-Tenant-Id", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }
}
