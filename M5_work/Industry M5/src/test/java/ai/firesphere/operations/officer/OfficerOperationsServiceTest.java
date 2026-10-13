package ai.firesphere.operations.officer;

import ai.firesphere.common.exception.ConflictException;
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

class OfficerOperationsServiceTest {

    private InMemoryOfficerTaskRepository taskRepository;
    private OfficerOperationsProperties properties;
    private OperationalAlertService alertService;
    private OfficerEscalationService escalationService;
    private OfficerSecurityValidator securityValidator;
    private OfficerOperationsService operationsService;

    private final Instant baseTime = Instant.parse("2026-10-09T10:00:00Z");
    private Clock fixedClock;
    private final UUID tenant1 = UUID.randomUUID();
    private final UUID tenant2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(baseTime, ZoneOffset.UTC);
        taskRepository = new InMemoryOfficerTaskRepository();
        properties = new OfficerOperationsProperties();
        properties.setDeadlineApproachingThresholdMinutes(120);
        properties.setOverdueEscalationThresholdMinutes(60);
        properties.setHighPriorityEscalationMinutes(30);
        properties.setMaxActiveTasksPerOfficer(5);
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
    @DisplayName("Create task creates assignment with correct status and priority")
    void testCreateTask_Success() {
        OfficerTaskCreateRequest request = new OfficerTaskCreateRequest(
                "TSK-2026-001",
                OfficerTaskType.INSPECTION,
                "Commercial Fire Inspection",
                "Annual safety inspection of Tower 1",
                "OF-101",
                UUID.randomUUID(),
                DispatchPriority.HIGH,
                baseTime.plus(Duration.ofHours(1)),
                baseTime.plus(Duration.ofHours(3))
        );

        OfficerTaskResponseDTO created = operationsService.createTask(request);

        assertThat(created).isNotNull();
        assertThat(created.taskReference()).isEqualTo("TSK-2026-001");
        assertThat(created.assignedOfficerBadge()).isEqualTo("OF-101");
        assertThat(created.status()).isEqualTo(OfficerTaskStatus.ASSIGNED);
        assertThat(created.priority()).isEqualTo(DispatchPriority.HIGH);
        assertThat(created.isOverdue()).isFalse();
        assertThat(created.isEscalated()).isFalse();
    }

    @Test
    @DisplayName("Create task without assigned badge initializes as UNASSIGNED")
    void testCreateTask_UnassignedWhenNoBadge() {
        OfficerTaskCreateRequest request = new OfficerTaskCreateRequest(
                "TSK-UNASSIGNED-1",
                OfficerTaskType.HAZARD_VERIFICATION,
                "Hazard Inspection",
                "Unassigned check",
                null,
                null,
                DispatchPriority.MEDIUM,
                baseTime,
                baseTime.plus(Duration.ofHours(2))
        );

        OfficerTaskResponseDTO created = operationsService.createTask(request);
        assertThat(created.status()).isEqualTo(OfficerTaskStatus.UNASSIGNED);
    }

    @Test
    @DisplayName("Create task with duplicate reference in same tenant throws ConflictException")
    void testCreateTask_DuplicateReferenceThrowsConflict() {
        OfficerTaskCreateRequest request1 = new OfficerTaskCreateRequest(
                "TSK-DUP-01", OfficerTaskType.INSPECTION, "Title", "Desc", "OF-101", null,
                DispatchPriority.MEDIUM, baseTime, baseTime.plus(Duration.ofHours(1))
        );
        operationsService.createTask(request1);

        OfficerTaskCreateRequest request2 = new OfficerTaskCreateRequest(
                "TSK-DUP-01", OfficerTaskType.INSPECTION, "Title 2", "Desc 2", "OF-102", null,
                DispatchPriority.HIGH, baseTime, baseTime.plus(Duration.ofHours(2))
        );

        assertThatThrownBy(() -> operationsService.createTask(request2))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Create task with scheduledAt after deadlineAt throws IllegalArgumentException")
    void testCreateTask_InvalidDatesThrowsIllegalArgument() {
        OfficerTaskCreateRequest request = new OfficerTaskCreateRequest(
                "TSK-INVALID-DATES", OfficerTaskType.INSPECTION, "Title", "Desc", "OF-101", null,
                DispatchPriority.MEDIUM,
                baseTime.plus(Duration.ofHours(5)),
                baseTime.plus(Duration.ofHours(1)) // deadline before scheduled
        );

        assertThatThrownBy(() -> operationsService.createTask(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be after deadlineAt");
    }

    @Test
    @DisplayName("Retrieve task by ID and by reference, throw ResourceNotFoundException for missing")
    void testGetTask_ByIdAndReference() {
        OfficerTaskCreateRequest request = new OfficerTaskCreateRequest(
                "TSK-GET-01", OfficerTaskType.HYDRANT_SURVEY, "Hydrant Survey", "Flow test",
                "OF-101", null, DispatchPriority.MEDIUM, baseTime, baseTime.plus(Duration.ofHours(2))
        );
        OfficerTaskResponseDTO created = operationsService.createTask(request);

        OfficerTaskResponseDTO byId = operationsService.getTask(created.taskId());
        assertThat(byId.taskReference()).isEqualTo("TSK-GET-01");

        OfficerTaskResponseDTO byRef = operationsService.getTaskByReference("TSK-GET-01");
        assertThat(byRef.taskId()).isEqualTo(created.taskId());

        UUID nonExistent = UUID.randomUUID();
        assertThatThrownBy(() -> operationsService.getTask(nonExistent))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThatThrownBy(() -> operationsService.getTaskByReference("NON-EXISTENT-REF"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Task status updates correctly and sets completedAt on completion")
    void testUpdateTaskStatus_Success() {
        OfficerTaskCreateRequest request = new OfficerTaskCreateRequest(
                "TSK-STATUS-01", OfficerTaskType.INSPECTION, "Title", "Desc", "OF-101", null,
                DispatchPriority.MEDIUM, baseTime, baseTime.plus(Duration.ofHours(2))
        );
        OfficerTaskResponseDTO created = operationsService.createTask(request);

        // Transition to IN_PROGRESS
        OfficerTaskResponseDTO inProgress = operationsService.updateTaskStatus(
                created.taskId(),
                new OfficerTaskStatusUpdateRequest(OfficerTaskStatus.IN_PROGRESS, "Officer on site")
        );
        assertThat(inProgress.status()).isEqualTo(OfficerTaskStatus.IN_PROGRESS);
        assertThat(inProgress.completedAt()).isNull();

        // Transition to COMPLETED
        OfficerTaskResponseDTO completed = operationsService.updateTaskStatus(
                created.taskId(),
                new OfficerTaskStatusUpdateRequest(OfficerTaskStatus.COMPLETED, "Inspection passed")
        );
        assertThat(completed.status()).isEqualTo(OfficerTaskStatus.COMPLETED);
        assertThat(completed.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("Filtering tasks by officer badge, status, priority, and date window")
    void testGetTasks_FilteringAndSorting() {
        operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-F-01", OfficerTaskType.INSPECTION, "T1", "D", "OF-101", null,
                DispatchPriority.LOW, baseTime.plus(Duration.ofHours(1)), baseTime.plus(Duration.ofHours(2))
        ));
        operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-F-02", OfficerTaskType.INSPECTION, "T2", "D", "OF-101", null,
                DispatchPriority.CRITICAL, baseTime.plus(Duration.ofHours(1)), baseTime.plus(Duration.ofHours(2))
        ));
        operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-F-03", OfficerTaskType.INSPECTION, "T3", "D", "OF-202", null,
                DispatchPriority.HIGH, baseTime.plus(Duration.ofHours(1)), baseTime.plus(Duration.ofHours(2))
        ));

        // Filter by badge OF-101: should return 2 tasks, sorted by priority (CRITICAL first, then LOW)
        List<OfficerTaskResponseDTO> badgeTasks = operationsService.getTasks(
                "OF-101", null, null, null, null
        );
        assertThat(badgeTasks).hasSize(2);
        assertThat(badgeTasks.get(0).priority()).isEqualTo(DispatchPriority.CRITICAL);
        assertThat(badgeTasks.get(1).priority()).isEqualTo(DispatchPriority.LOW);

        // Filter by priority CRITICAL
        List<OfficerTaskResponseDTO> criticalTasks = operationsService.getTasks(
                null, null, DispatchPriority.CRITICAL, null, null
        );
        assertThat(criticalTasks).hasSize(1);
        assertThat(criticalTasks.get(0).taskReference()).isEqualTo("TSK-F-02");

        // Filter by invalid date range: fromDate after toDate
        assertThatThrownBy(() -> operationsService.getTasks(
                null, null, null, baseTime.plus(Duration.ofHours(5)), baseTime
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Overdue task detection operates deterministically with injected Clock")
    void testOverdueTasks_WithDeterministicClock() {
        // Create a task due 30 minutes in the past relative to baseTime
        operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-OD-01", OfficerTaskType.INSPECTION, "Overdue Task", "Desc", "OF-101", null,
                DispatchPriority.HIGH, baseTime.minus(Duration.ofHours(2)), baseTime.minus(Duration.ofMinutes(30))
        ));

        // Create a future task due 2 hours from baseTime
        operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-FUT-01", OfficerTaskType.INSPECTION, "Future Task", "Desc", "OF-101", null,
                DispatchPriority.HIGH, baseTime, baseTime.plus(Duration.ofHours(2))
        ));

        List<OfficerTaskResponseDTO> overdue = operationsService.getOverdueTasks();
        assertThat(overdue).hasSize(1);
        assertThat(overdue.get(0).taskReference()).isEqualTo("TSK-OD-01");
        assertThat(overdue.get(0).isOverdue()).isTrue();
    }

    @Test
    @DisplayName("Officer workload calculation computes accurate metrics and capacity status")
    void testOfficerWorkload_CalculationAndCapacity() {
        String badge = "OF-303";

        // Create 2 ASSIGNED, 1 IN_PROGRESS, 1 COMPLETED, 1 OVERDUE
        OfficerTaskResponseDTO t1 = operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-WL-01", OfficerTaskType.INSPECTION, "T1", "D", badge, null,
                DispatchPriority.HIGH, baseTime, baseTime.plus(Duration.ofHours(1))
        ));
        OfficerTaskResponseDTO t2 = operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-WL-02", OfficerTaskType.INSPECTION, "T2", "D", badge, null,
                DispatchPriority.CRITICAL, baseTime, baseTime.plus(Duration.ofHours(2))
        ));
        OfficerTaskResponseDTO t3 = operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-WL-03", OfficerTaskType.INSPECTION, "T3", "D", badge, null,
                DispatchPriority.MEDIUM, baseTime.minus(Duration.ofHours(1)), baseTime.minus(Duration.ofMinutes(10)) // Overdue
        ));
        OfficerTaskResponseDTO t4 = operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-WL-04", OfficerTaskType.INSPECTION, "T4", "D", badge, null,
                DispatchPriority.LOW, baseTime, baseTime.plus(Duration.ofHours(3))
        ));

        // Update t3 to IN_PROGRESS, t4 to COMPLETED
        operationsService.updateTaskStatus(t3.taskId(), new OfficerTaskStatusUpdateRequest(OfficerTaskStatus.IN_PROGRESS, null));
        operationsService.updateTaskStatus(t4.taskId(), new OfficerTaskStatusUpdateRequest(OfficerTaskStatus.COMPLETED, null));

        OfficerWorkloadSummaryDTO workload = operationsService.getOfficerWorkload(badge);

        assertThat(workload.officerBadge()).isEqualTo(badge);
        assertThat(workload.totalAssignedTasks()).isEqualTo(4);
        assertThat(workload.pendingTasks()).isEqualTo(2); // t1, t2
        assertThat(workload.inProgressTasks()).isEqualTo(1); // t3
        assertThat(workload.completedTasks()).isEqualTo(1); // t4
        assertThat(workload.overdueTasks()).isEqualTo(1); // t3
        assertThat(workload.highPriorityTasks()).isEqualTo(2); // t1, t2
        // Active tasks = 2 + 1 = 3 out of max 5 -> 60% capacity -> OPTIMAL
        assertThat(workload.workloadCapacityStatus()).isEqualTo("OPTIMAL");
    }

    @Test
    @DisplayName("OperationalAlertService generates alerts and suppresses duplicates")
    void testOperationalAlerts_GenerationAndDeduplication() {
        // 1. Overdue task
        operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-ALT-OD", OfficerTaskType.INSPECTION, "OD", "D", "OF-101", null,
                DispatchPriority.MEDIUM, baseTime.minus(Duration.ofHours(1)), baseTime.minus(Duration.ofMinutes(15))
        ));

        // 2. High-priority unassigned task (deadline far away to not trigger approaching deadline)
        operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-ALT-UN", OfficerTaskType.INSPECTION, "UN", "D", null, null,
                DispatchPriority.CRITICAL, baseTime, baseTime.plus(Duration.ofHours(5))
        ));

        // 3. Deadline approaching task (due in 30 minutes, threshold is 120 mins)
        operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-ALT-AP", OfficerTaskType.INSPECTION, "AP", "D", "OF-101", null,
                DispatchPriority.MEDIUM, baseTime, baseTime.plus(Duration.ofMinutes(30))
        ));

        List<OperationalAlertDTO> firstEval = operationsService.evaluateOperationalAlerts();
        assertThat(firstEval).hasSize(3);

        // Second evaluation: all alerts should be deduplicated (no new alerts returned)
        List<OperationalAlertDTO> secondEval = operationsService.evaluateOperationalAlerts();
        assertThat(secondEval).isEmpty();
    }

    @Test
    @DisplayName("OfficerEscalationService auto-escalates overdue threshold breaches and prevents duplicate escalation")
    void testEscalationService_OverdueAndDuplicatePrevention() {
        // Create a task overdue by 90 minutes (overdue threshold is 60 min)
        OfficerTaskResponseDTO created = operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-ESC-01", OfficerTaskType.INSPECTION, "Task for Escalation", "D", "OF-101", null,
                DispatchPriority.MEDIUM, baseTime.minus(Duration.ofHours(3)), baseTime.minus(Duration.ofMinutes(90))
        ));

        List<EscalationResultDTO> escalations = operationsService.evaluateEscalations();
        assertThat(escalations).hasSize(1);
        assertThat(escalations.get(0).taskReference()).isEqualTo("TSK-ESC-01");
        assertThat(escalations.get(0).escalatedToRole()).isEqualTo("STATION_CAPTAIN");

        // Verify task state in repository is now ESCALATED
        OfficerTaskResponseDTO updated = operationsService.getTask(created.taskId());
        assertThat(updated.status()).isEqualTo(OfficerTaskStatus.ESCALATED);
        assertThat(updated.isEscalated()).isTrue();

        // Second evaluation should NOT escalate again
        List<EscalationResultDTO> secondEval = operationsService.evaluateEscalations();
        assertThat(secondEval).isEmpty();
    }

    @Test
    @DisplayName("Manual escalation promotes task and disallows duplicate escalation")
    void testManualEscalation_SuccessAndDuplicateDisallowed() {
        OfficerTaskResponseDTO created = operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-MAN-ESC", OfficerTaskType.INSPECTION, "Manual Task", "D", "OF-101", null,
                DispatchPriority.LOW, baseTime, baseTime.plus(Duration.ofHours(5))
        ));

        EscalationResultDTO esc = operationsService.manualEscalateTask(created.taskId(), "Supervisor intervention required");
        assertThat(esc.escalatedToRole()).isEqualTo("STATION_CAPTAIN");
        assertThat(esc.reason()).isEqualTo("Supervisor intervention required");

        // Trying manual escalation again throws IllegalArgumentException
        assertThatThrownBy(() -> operationsService.manualEscalateTask(created.taskId(), "Again"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already escalated");
    }

    @Test
    @DisplayName("Tenant isolation strictly enforces data boundary across tenants")
    void testTenantIsolation_BoundaryEnforced() {
        // Create task under tenant1
        operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-T1-ONLY", OfficerTaskType.INSPECTION, "T1 Task", "D", "OF-101", null,
                DispatchPriority.MEDIUM, baseTime, baseTime.plus(Duration.ofHours(1))
        ));

        // Switch to tenant2
        TenantContext.setTenantId(tenant2);
        MockSecurityHelper.setupSecurityContext(tenant2, "admin2", List.of("ROLE_ADMIN"));

        // Tenant2 should see empty list and cannot fetch by reference
        List<OfficerTaskResponseDTO> t2Tasks = operationsService.getTasks(null, null, null, null, null);
        assertThat(t2Tasks).isEmpty();

        assertThatThrownBy(() -> operationsService.getTaskByReference("TSK-T1-ONLY"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("OfficerSecurityValidator forbids officer from accessing another officer's records")
    void testSecurityValidation_EnforcesOfficerIdentity() {
        operationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-SEC-01", OfficerTaskType.INSPECTION, "Officer 101 Task", "D", "OF-101", null,
                DispatchPriority.MEDIUM, baseTime, baseTime.plus(Duration.ofHours(1))
        ));

        // Simulate login as officer OF-202 (not supervisor)
        MockSecurityHelper.setupSecurityContext(tenant1, "OF-202", List.of("ROLE_OFFICER"));

        // Attempting to access OF-101's workload throws AccessDeniedException
        assertThatThrownBy(() -> operationsService.getOfficerWorkload("OF-101"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not authorized");

        // Accessing own workload (OF-202) is permitted and returns 0 tasks
        OfficerWorkloadSummaryDTO ownWorkload = operationsService.getOfficerWorkload("OF-202");
        assertThat(ownWorkload.totalAssignedTasks()).isEqualTo(0);

        // Supervisor role (ROLE_DISPATCHER) can access any officer
        MockSecurityHelper.setupSecurityContext(tenant1, "dispatcher1", List.of("ROLE_DISPATCHER"));
        OfficerWorkloadSummaryDTO dispatcherCheck = operationsService.getOfficerWorkload("OF-101");
        assertThat(dispatcherCheck.totalAssignedTasks()).isEqualTo(1);
    }

    @Test
    @DisplayName("Workload evaluation returns empty counters for non-existent officer")
    void testWorkload_NonExistentOfficerReturnsZeroCounters() {
        OfficerWorkloadSummaryDTO workload = operationsService.getOfficerWorkload("OF-UNKNOWN");
        assertThat(workload.totalAssignedTasks()).isEqualTo(0);
        assertThat(workload.workloadCapacityStatus()).isEqualTo("OPTIMAL");
    }
}
