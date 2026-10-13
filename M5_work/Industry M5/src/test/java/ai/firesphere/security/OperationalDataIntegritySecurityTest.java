package ai.firesphere.security;

import ai.firesphere.common.exception.BusinessRuleViolationException;
import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.integration.core.model.IntegrationResponse;
import ai.firesphere.integration.core.exception.IntegrationProviderException;
import ai.firesphere.integration.core.retry.IntegrationRetryExecutor;
import ai.firesphere.integration.core.retry.RetryPolicy;
import ai.firesphere.inventory.dto.InventoryItemCreateRequest;
import ai.firesphere.inventory.dto.InventoryItemResponseDTO;
import ai.firesphere.inventory.dto.StockIssueRequest;
import ai.firesphere.inventory.service.InventoryService;
import ai.firesphere.notification.channel.NotificationChannel;
import ai.firesphere.notification.event.NotificationEvent;
import ai.firesphere.notification.event.NotificationEventType;
import ai.firesphere.notification.model.NotificationPriority;
import ai.firesphere.notification.model.NotificationRecord;
import ai.firesphere.notification.model.NotificationStatus;
import ai.firesphere.notification.provider.mock.MockSmsProvider;
import ai.firesphere.notification.service.NotificationService;
import ai.firesphere.operations.officer.dto.OfficerTaskCreateRequest;
import ai.firesphere.operations.officer.dto.OfficerTaskResponseDTO;
import ai.firesphere.operations.officer.dto.OfficerTaskStatusUpdateRequest;
import ai.firesphere.operations.officer.dto.OperationalAlertDTO;
import ai.firesphere.operations.officer.model.OfficerTaskStatus;
import ai.firesphere.operations.officer.model.OfficerTaskType;
import ai.firesphere.operations.officer.repository.OfficerTaskRepository;
import ai.firesphere.operations.officer.service.OfficerOperationsService;
import ai.firesphere.testing.MockSecurityHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security QA Suite: Operational Data Integrity and State Transition Protections.
 * Validates atomic transaction boundaries, stock integrity against negative depletion,
 * terminal state escalation invariants, alert/escalation deduplication,
 * and retry exhaustion ceilings across notification and integration engines.
 */
@SpringBootTest
@ActiveProfiles("test")
class OperationalDataIntegritySecurityTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private OfficerOperationsService officerOperationsService;

    @Autowired
    private OfficerTaskRepository officerTaskRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private MockSmsProvider mockSmsProvider;

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID stationId = UUID.randomUUID();
    private final Instant baseTime = Instant.parse("2026-10-09T10:00:00Z");

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        MockSecurityHelper.setupSecurityContext(TENANT_ID, "ops_commander", List.of("ROLE_ADMIN", "ROLE_DISPATCHER", "ROLE_COMMANDER"));
        officerTaskRepository.clear();
        mockSmsProvider.clear();
    }

    @AfterEach
    void tearDown() {
        MockSecurityHelper.clearSecurityContext();
        TenantContext.clear();
        officerTaskRepository.clear();
        mockSmsProvider.clear();
    }

    @Test
    @DisplayName("Data Integrity: Issuing stock exceeding quantity on hand is rejected; stock level remains uncorrupted")
    void testStockOverIssuePrevention() {
        String sku = "SKU-FOAM-" + UUID.randomUUID().toString().substring(0, 4);
        InventoryItemResponseDTO item = inventoryService.registerItem(new InventoryItemCreateRequest(
                sku, "AFFF Foam 20L", "FOAM", stationId, 10, 2, "DRUMS", "Class B suppression"
        ));
        assertThat(item.quantityOnHand()).isEqualTo(10);

        // Attempting to issue 15 units when only 10 are available
        StockIssueRequest overIssueReq = new StockIssueRequest(
                item.id(), 15, UUID.randomUUID(), "Fire Ground", "Attempted over-issue"
        );

        assertThatThrownBy(() -> inventoryService.issueStock(item.id(), overIssueReq))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Insufficient stock");

        // Verify that quantity on hand was NOT partially deducted or corrupted to negative
        InventoryItemResponseDTO postCheck = inventoryService.getItem(item.id());
        assertThat(postCheck.quantityOnHand()).isEqualTo(10);
    }

    @Test
    @DisplayName("State Transition Integrity: Terminal tasks (COMPLETED) cannot be escalated")
    void testTerminalStateEscalationGuardrail() {
        OfficerTaskResponseDTO task = officerOperationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-TERM-INTEG", OfficerTaskType.INSPECTION, "Survey", "Desc", "OF-101", null,
                DispatchPriority.HIGH, baseTime, baseTime.plus(Duration.ofHours(2))
        ));

        // Complete the task
        officerOperationsService.updateTaskStatus(
                task.taskId(), new OfficerTaskStatusUpdateRequest(OfficerTaskStatus.COMPLETED, "Inspection done")
        );

        // Escalation on completed task must be rejected
        assertThatThrownBy(() -> officerOperationsService.manualEscalateTask(task.taskId(), "Attempted escalation on completed task"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal state");
    }

    @Test
    @DisplayName("State Transition Integrity: Escalating an already escalated task is rejected")
    void testDuplicateEscalationGuardrail() {
        OfficerTaskResponseDTO task = officerOperationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-ESC-INTEG", OfficerTaskType.INSPECTION, "Survey", "Desc", "OF-101", null,
                DispatchPriority.HIGH, baseTime, baseTime.plus(Duration.ofHours(2))
        ));

        // First manual escalation succeeds
        officerOperationsService.manualEscalateTask(task.taskId(), "First valid escalation");

        // Second manual escalation must be rejected
        assertThatThrownBy(() -> officerOperationsService.manualEscalateTask(task.taskId(), "Duplicate escalation"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already escalated");
    }

    @Test
    @DisplayName("Operational Integrity: Repeated alert evaluations do not generate duplicate alert records")
    void testAlertDeduplicationIntegrity() {
        Instant now = Instant.now();
        // Create an overdue task to generate an alert
        officerOperationsService.createTask(new OfficerTaskCreateRequest(
                "TSK-ALERT-DUP", OfficerTaskType.INSPECTION, "OD Task", "Desc", "OF-101", null,
                DispatchPriority.MEDIUM, now.minus(Duration.ofHours(3)), now.minus(Duration.ofHours(1))
        ));

        // First run generates 1 alert
        List<OperationalAlertDTO> firstRun = officerOperationsService.evaluateOperationalAlerts();
        assertThat(firstRun).hasSize(1);
        assertThat(firstRun.get(0).taskReference()).isEqualTo("TSK-ALERT-DUP");

        // Second run within same operational window suppresses duplicate alerts
        List<OperationalAlertDTO> secondRun = officerOperationsService.evaluateOperationalAlerts();
        assertThat(secondRun).isEmpty();
    }

    @Test
    @DisplayName("Idempotency Integrity: Re-submitting identical notification request returns cached response")
    void testNotificationIdempotencyIntegrity() {
        String idempKey = "IDEMP-SEC-" + UUID.randomUUID();

        NotificationEvent event = NotificationEvent.builder()
                .eventType(NotificationEventType.SYSTEM_ALERT)
                .referenceId("SYS-SEC-IDEMP")
                .tenantId(TENANT_ID)
                .channels(Set.of(NotificationChannel.SMS))
                .recipients(Map.of(NotificationChannel.SMS, "+14155552671"))
                .payload(Map.of("alertTitle", "Evacuation Alert", "severity", "CRITICAL", "message", "Leave immediately"))
                .idempotencyKey(idempKey)
                .priority(NotificationPriority.CRITICAL)
                .build();

        List<NotificationRecord> first = notificationService.processEvent(event);
        assertThat(first).hasSize(1);
        UUID notificationId = first.get(0).getId();
        assertThat(first.get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
        int initialDeliveries = mockSmsProvider.getDeliveredSms().size();

        // Re-submit identical request with same idempotency key
        List<NotificationRecord> second = notificationService.processEvent(event);
        assertThat(second).hasSize(1);
        assertThat(second.get(0).getId()).isEqualTo(notificationId);

        // Zero additional network/mock invocations
        assertThat(mockSmsProvider.getDeliveredSms().size()).isEqualTo(initialDeliveries);
    }

    @Test
    @DisplayName("Resilience Ceiling: Notification retry stops after exactly 3 attempts and marks status FAILED")
    void testNotificationRetryCeilingEnforced() {
        mockSmsProvider.setSimulateFailure(true);

        NotificationEvent event = NotificationEvent.builder()
                .eventType(NotificationEventType.DISPATCH)
                .referenceId("DSP-CEIL-01")
                .tenantId(TENANT_ID)
                .channels(Set.of(NotificationChannel.SMS))
                .recipients(Map.of(NotificationChannel.SMS, "+14155552671"))
                .payload(Map.of("dispatchCode", "DSP-CEIL-01", "unitCode", "PUMPER-1", "location", "Pier 12"))
                .priority(NotificationPriority.HIGH)
                .build();

        List<NotificationRecord> records = notificationService.processEvent(event);
        UUID notificationId = records.get(0).getId();

        // Attempt 1: Initial delivery failed -> Status PENDING (attemptCount 1)
        NotificationRecord r1 = notificationService.getNotificationById(notificationId).orElseThrow();
        assertThat(r1.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(r1.getAttemptCount()).isEqualTo(1);

        // Attempt 2: Retry 1 failed -> Status PENDING (attemptCount 2)
        NotificationRecord r2 = notificationService.retryNotification(notificationId);
        assertThat(r2.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(r2.getAttemptCount()).isEqualTo(2);

        // Attempt 3: Retry 2 failed -> Max attempts (3) exhausted -> Status becomes FAILED
        NotificationRecord r3 = notificationService.retryNotification(notificationId);
        assertThat(r3.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(r3.getAttemptCount()).isEqualTo(3);

        // Attempt 4: Reject further retry when terminal FAILED
        assertThatThrownBy(() -> notificationService.retryNotification(notificationId))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("maximum retry attempts");
    }

    @Test
    @DisplayName("Resilience Ceiling: Integration retry halts after 3 attempts with ERR_RETRIES_EXHAUSTED; non-retryable aborts immediately")
    void testIntegrationRetryCeilingEnforced() {
        RetryPolicy policy = new RetryPolicy(3, 1, 1.0);
        IntegrationRetryExecutor executor = new IntegrationRetryExecutor();
        AtomicInteger attemptCounter = new AtomicInteger(0);

        // 1. Transient error reaches maximum 3 attempts then exhausts
        IntegrationResponse<String> exhausted = executor.executeWithRetry(
                "charge",
                "TEST_GATEWAY",
                policy,
                () -> {
                    attemptCounter.incrementAndGet();
                    throw new IntegrationProviderException("TEST_GATEWAY", "NETWORK_TIMEOUT", "Gateway connection timed out", true);
                }
        );

        assertThat(exhausted.isSuccess()).isFalse();
        assertThat(exhausted.getError()).isPresent();
        assertThat(exhausted.getError().get().code()).isEqualTo("ERR_RETRIES_EXHAUSTED");
        assertThat(attemptCounter.get()).isEqualTo(3);

        // 2. Non-retryable error aborts on first attempt
        AtomicInteger nonRetryAttempts = new AtomicInteger(0);
        IntegrationResponse<String> nonRetry = executor.executeWithRetry(
                "charge",
                "TEST_GATEWAY",
                policy,
                () -> {
                    nonRetryAttempts.incrementAndGet();
                    throw new IntegrationProviderException("TEST_GATEWAY", "INVALID_REQUEST_PAYLOAD", "Malformed payload", false);
                }
        );

        assertThat(nonRetry.isSuccess()).isFalse();
        assertThat(nonRetry.getError()).isPresent();
        assertThat(nonRetry.getError().get().code()).isEqualTo("INVALID_REQUEST_PAYLOAD");
        assertThat(nonRetryAttempts.get()).isEqualTo(1); // Aborted immediately without retry
    }
}
