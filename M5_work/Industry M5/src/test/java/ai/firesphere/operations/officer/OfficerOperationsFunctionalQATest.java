package ai.firesphere.operations.officer;

import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.operations.officer.alert.OfficerEscalationService;
import ai.firesphere.operations.officer.alert.OperationalAlertService;
import ai.firesphere.operations.officer.config.OfficerOperationsProperties;
import ai.firesphere.operations.officer.dto.*;
import ai.firesphere.operations.officer.model.*;
import ai.firesphere.operations.officer.repository.InMemoryOfficerTaskRepository;
import ai.firesphere.operations.officer.security.OfficerSecurityValidator;
import ai.firesphere.operations.officer.service.OfficerOperationsService;
import ai.firesphere.operations.officer.service.OfficerOperationsServiceImpl;
import ai.firesphere.testing.MockSecurityHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Functional QA Suite for Officer Operations Support.
 * Validates workload capacity thresholds (OPTIMAL, HEAVY, OVERLOADED),
 * terminal state escalation protections, deep multi-tenant isolation,
 * deadline approaching threshold edge cases, and RBAC role matrix access.
 */
class OfficerOperationsFunctionalQATest {

    private InMemoryOfficerTaskRepository taskRepository;
    private OfficerOperationsProperties properties;
    private OperationalAlertService alertService;
    private OfficerEscalationService escalationService;
    private OfficerSecurityValidator securityValidator;
    private OfficerOperationsService operationsService;

    private final Instant baseTime = Instant.parse("2026-10-09T12:00:00Z");
    private Clock fixedClock;

    private final UUID tenant1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID tenant2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(baseTime, ZoneOffset.UTC);
        taskRepository = new InMemoryOfficerTaskRepository();
        properties = new OfficerOperationsProperties();
        properties.setDeadlineApproachingThresholdMinutes(120);
        properties.setOverdueEscalationThresholdMinutes(60);
        properties.setHighPriorityEscalationMinutes(30);
        properties.setMaxActiveTasksPerOfficer(10);
        properties.setDefaultEscalatedRole("STATION_CAPTAIN");

        alertService = new OperationalAlertService(properties, fixedClock, null);
        escalationService = new OfficerEscalationService(taskRepository, properties, fixedClock, null);
        securityValidator = new OfficerSecurityValidator();

        operationsService = new OfficerOperationsServiceImpl(
                taskRepository,
                alertService,
                escalationService,
                securityValidator,
                properties,
                fixedClock
        );

        TenantContext.setTenantId(tenant1);
        MockSecurityHelper.setupSecurityContext(tenant1, "admin", List.of("ROLE_ADMIN"));
    }

    @AfterEach
    void tearDown() {
        MockSecurityHelper.clearSecurityContext();
        taskRepository.clear();
        alertService.clearDeduplicationCache();
        escalationService.clearHistory();
    }

    @Test
    @DisplayName("Workload Capacity Boundaries: Accurately classifies OPTIMAL, HEAVY (>=70%), and OVERLOADED (>=100%)")
    void testWorkloadCapacityBoundaries() {
        String badge = "OF-CAPACITY-TEST";

        // 1. Create 6 active tasks (6/10 = 60% capacity -> OPTIMAL)
        for (int i = 1; i <= 6; i++) {
            operationsService.createTask(new OfficerTaskCreateRequest(
                    "TSK-CAP-" + i, OfficerTaskType.INSPECTION, "T" + i, "D", badge, null,
                    DispatchPriority.MEDIUM, baseTime, baseTime.plus(Duration.ofHours(3))
            ));
        }
        OfficerWorkloadSummaryDTO summaryOptimal = operationsService.getOfficerWorkload(badge);
        assertThat(summaryOptimal.workloadCapacityStatus()).isEqualTo("OPTIMAL");

        // 2. Add 1 active task (7/10 = 70% capacity -> HEAVY)
        operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-CAP-7", OfficerTaskType.INSPECTION, "T7", "D", badge, null,
                DispatchPriority.MEDIUM, baseTime, baseTime.plus(Duration.ofHours(3))
        ));
        OfficerWorkloadSummaryDTO summaryHeavy = operationsService.getOfficerWorkload(badge);
        assertThat(summaryHeavy.workloadCapacityStatus()).isEqualTo("HEAVY");

        // 3. Add 3 more active tasks (10/10 = 100% capacity -> OVERLOADED)
        for (int i = 8; i <= 10; i++) {
            operationsService.createTask(new OfficerTaskCreateRequest(
                    "TSK-CAP-" + i, OfficerTaskType.INSPECTION, "T" + i, "D", badge, null,
                    DispatchPriority.MEDIUM, baseTime, baseTime.plus(Duration.ofHours(3))
            ));
        }
        OfficerWorkloadSummaryDTO summaryOverloaded = operationsService.getOfficerWorkload(badge);
        assertThat(summaryOverloaded.workloadCapacityStatus()).isEqualTo("OVERLOADED");
    }

    @Test
    @DisplayName("Terminal State Escalation Protection: Disallows escalating completed or already-escalated tasks")
    void testStatusTransitionsTerminalStateHandling() {
        OfficerTaskResponseDTO task = operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-TERM-01", OfficerTaskType.INSPECTION, "Title", "Desc", "OF-101", null,
                DispatchPriority.HIGH, baseTime, baseTime.plus(Duration.ofHours(2))
        ));

        // Mark task as COMPLETED
        operationsService.updateTaskStatus(task.taskId(), new OfficerTaskStatusUpdateRequest(OfficerTaskStatus.COMPLETED, "Done"));

        // Attempting to escalate a COMPLETED task must be rejected
        assertThatThrownBy(() -> operationsService.manualEscalateTask(task.taskId(), "Escalate completed task"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal state");

        // Create another task and escalate it
        OfficerTaskResponseDTO task2 = operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-TERM-02", OfficerTaskType.INSPECTION, "Title 2", "Desc", "OF-101", null,
                DispatchPriority.HIGH, baseTime, baseTime.plus(Duration.ofHours(2))
        ));
        operationsService.manualEscalateTask(task2.taskId(), "First escalation");

        // Attempting to escalate again must throw IllegalArgumentException (duplicate escalation prevented)
        assertThatThrownBy(() -> operationsService.manualEscalateTask(task2.taskId(), "Duplicate escalation"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already escalated");
    }

    @Test
    @DisplayName("Tenant Isolation Across All Operations: Tenant 2 cannot observe, update, alert, or escalate Tenant 1 tasks")
    void testTenantIsolationAcrossAllOfficerOperations() {
        // Create an overdue task in Tenant 1
        OfficerTaskResponseDTO t1Task = operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-T1-OVERDUE", OfficerTaskType.INSPECTION, "T1 Overdue", "D", "OF-T1", null,
                DispatchPriority.HIGH, baseTime.minus(Duration.ofHours(2)), baseTime.minus(Duration.ofHours(1))
        ));

        // Evaluate alerts in Tenant 1 -> 1 alert generated
        List<OperationalAlertDTO> t1Alerts = operationsService.evaluateOperationalAlerts();
        assertThat(t1Alerts).hasSize(1);
        assertThat(t1Alerts.get(0).taskReference()).isEqualTo("TSK-T1-OVERDUE");

        // Now switch context to Tenant 2
        TenantContext.setTenantId(tenant2);
        MockSecurityHelper.setupSecurityContext(tenant2, "admin2", List.of("ROLE_ADMIN"));

        // 1. Task query for Tenant 2 -> empty
        assertThat(operationsService.getTasks(null, null, null, null, null)).isEmpty();

        // 2. Overdue task query for Tenant 2 -> empty
        assertThat(operationsService.getOverdueTasks()).isEmpty();

        // 3. Evaluate alerts for Tenant 2 -> 0 alerts
        assertThat(operationsService.evaluateOperationalAlerts()).isEmpty();

        // 4. Evaluate escalations for Tenant 2 -> 0 escalations
        assertThat(operationsService.evaluateEscalations()).isEmpty();

        // 5. Escalation history for Tenant 2 -> empty
        assertThat(operationsService.getEscalationHistory()).isEmpty();

        // 6. Attempting to update Tenant 1's task from Tenant 2 -> ResourceNotFoundException
        assertThatThrownBy(() -> operationsService.updateTaskStatus(
                t1Task.taskId(), new OfficerTaskStatusUpdateRequest(OfficerTaskStatus.IN_PROGRESS, "Hacked")
        )).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Approaching Deadline Threshold: Identifies tasks within threshold, ignores future tasks and past tasks")
    void testApproachingDeadlineThresholdBoundaries() {
        // Task A: Deadline in 60 mins (within 120 min threshold -> triggers DEADLINE_APPROACHING)
        operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-DEAD-IN", OfficerTaskType.INSPECTION, "In Window", "D", "OF-101", null,
                DispatchPriority.MEDIUM, baseTime, baseTime.plus(Duration.ofMinutes(60))
        ));

        // Task B: Deadline in 180 mins (outside 120 min threshold -> no alert)
        operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-DEAD-OUT", OfficerTaskType.INSPECTION, "Out Window", "D", "OF-101", null,
                DispatchPriority.MEDIUM, baseTime, baseTime.plus(Duration.ofMinutes(180))
        ));

        // Task C: Deadline 15 mins ago (already overdue -> OVERDUE_INSPECTION, not DEADLINE_APPROACHING)
        operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-DEAD-PAST", OfficerTaskType.INSPECTION, "Past Deadline", "D", "OF-101", null,
                DispatchPriority.MEDIUM, baseTime.minus(Duration.ofHours(1)), baseTime.minus(Duration.ofMinutes(15))
        ));

        List<OperationalAlertDTO> alerts = operationsService.evaluateOperationalAlerts();
        assertThat(alerts).hasSize(2);

        List<String> types = alerts.stream().map(OperationalAlertDTO::alertType).toList();
        assertThat(types).containsExactlyInAnyOrder("DEADLINE_APPROACHING", "OVERDUE_INSPECTION");

        OperationalAlertDTO approachingAlert = alerts.stream()
                .filter(a -> "DEADLINE_APPROACHING".equals(a.alertType()))
                .findFirst().orElseThrow();
        assertThat(approachingAlert.taskReference()).isEqualTo("TSK-DEAD-IN");
    }

    @Test
    @DisplayName("Security Role Matrix: Officer cannot access other badges; supervisory roles have universal access")
    void testSecurityValidationRoleMatrix() {
        operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-SEC-02", OfficerTaskType.INSPECTION, "Task", "D", "OF-101", null,
                DispatchPriority.MEDIUM, baseTime, baseTime.plus(Duration.ofHours(1))
        ));

        // 1. Regular Officer OF-202 tries to view OF-101 workload -> AccessDeniedException
        MockSecurityHelper.setupSecurityContext(tenant1, "OF-202", List.of("ROLE_OFFICER"));
        assertThatThrownBy(() -> operationsService.getOfficerWorkload("OF-101"))
                .isInstanceOf(AccessDeniedException.class);

        // 2. Station Captain access -> Allowed
        MockSecurityHelper.setupSecurityContext(tenant1, "captain", List.of("ROLE_STATION_CAPTAIN"));
        assertThat(operationsService.getOfficerWorkload("OF-101").totalAssignedTasks()).isEqualTo(1);

        // 3. Commander access -> Allowed
        MockSecurityHelper.setupSecurityContext(tenant1, "commander", List.of("ROLE_COMMANDER"));
        assertThat(operationsService.getOfficerWorkload("OF-101").totalAssignedTasks()).isEqualTo(1);

        // 4. Dispatcher access -> Allowed
        MockSecurityHelper.setupSecurityContext(tenant1, "dispatcher", List.of("ROLE_DISPATCHER"));
        assertThat(operationsService.getOfficerWorkload("OF-101").totalAssignedTasks()).isEqualTo(1);
    }
}
