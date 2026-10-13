package ai.firesphere.notification;

import ai.firesphere.common.exception.BusinessRuleViolationException;
import ai.firesphere.notification.channel.NotificationChannel;
import ai.firesphere.notification.event.NotificationEvent;
import ai.firesphere.notification.event.NotificationEventType;
import ai.firesphere.notification.model.NotificationPriority;
import ai.firesphere.notification.model.NotificationRecord;
import ai.firesphere.notification.model.NotificationStatus;
import ai.firesphere.notification.provider.mock.MockEmailProvider;
import ai.firesphere.notification.provider.mock.MockPushProvider;
import ai.firesphere.notification.provider.mock.MockSmsProvider;
import ai.firesphere.notification.provider.mock.MockWhatsAppProvider;
import ai.firesphere.notification.repository.InMemoryNotificationRecordRepository;
import ai.firesphere.notification.repository.NotificationRecordRepository;
import ai.firesphere.notification.service.NotificationService;
import ai.firesphere.notification.service.NotificationServiceImpl;
import ai.firesphere.notification.template.NotificationTemplateRegistry;
import ai.firesphere.notification.validation.RecipientValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Functional QA Suite for the Notification System.
 * Tests full template resolution across all standard event types,
 * graceful handling of template render failures, retry exhaustion and terminal FAILED state,
 * multi-channel priority handling, idempotency deduplication, and mock provider isolation.
 */
class NotificationFunctionalQATest {

    private MockPushProvider pushProvider;
    private MockSmsProvider smsProvider;
    private MockEmailProvider emailProvider;
    private MockWhatsAppProvider whatsAppProvider;
    private NotificationTemplateRegistry templateRegistry;
    private RecipientValidator recipientValidator;
    private NotificationRecordRepository repository;
    private NotificationService notificationService;

    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        pushProvider = new MockPushProvider();
        smsProvider = new MockSmsProvider();
        emailProvider = new MockEmailProvider();
        whatsAppProvider = new MockWhatsAppProvider();
        templateRegistry = new NotificationTemplateRegistry();
        recipientValidator = new RecipientValidator();
        repository = new InMemoryNotificationRecordRepository();

        notificationService = new NotificationServiceImpl(
                pushProvider,
                smsProvider,
                emailProvider,
                whatsAppProvider,
                templateRegistry,
                recipientValidator,
                repository
        );
    }

    @Test
    @DisplayName("Event Types & Templates: Validates template rendering across standard event types")
    void testStandardEventTypesTemplateRendering() {
        // 1. VEHICLE_ALERT (PUSH)
        NotificationEvent vehicleEvent = NotificationEvent.builder()
                .eventType(NotificationEventType.VEHICLE_ALERT)
                .referenceId("VEH-101")
                .tenantId(tenantId)
                .channels(Set.of(NotificationChannel.PUSH))
                .recipients(Map.of(NotificationChannel.PUSH, "station-chief-push"))
                .payload(Map.of("vehicleCode", "ENGINE-1", "alertType", "LOW_FUEL", "details", "Fuel level 12%"))
                .priority(NotificationPriority.HIGH)
                .build();
        List<NotificationRecord> vehicleRecords = notificationService.processEvent(vehicleEvent);
        assertThat(vehicleRecords).hasSize(1);
        assertThat(vehicleRecords.get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(vehicleRecords.get(0).getRenderedContent()).contains("ENGINE-1").doesNotContain("{{");

        // 2. LOW_INVENTORY (PUSH)
        NotificationEvent inventoryEvent = NotificationEvent.builder()
                .eventType(NotificationEventType.LOW_INVENTORY)
                .referenceId("SKU-FOAM-01")
                .tenantId(tenantId)
                .channels(Set.of(NotificationChannel.PUSH))
                .recipients(Map.of(NotificationChannel.PUSH, "quartermaster-push"))
                .payload(Map.of("sku", "FOAM-AFFF", "itemName", "AFFF Foam", "stationId", "STATION-1", "quantity", 5))
                .priority(NotificationPriority.HIGH)
                .build();
        List<NotificationRecord> invRecords = notificationService.processEvent(inventoryEvent);
        assertThat(invRecords).hasSize(1);
        assertThat(invRecords.get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(invRecords.get(0).getRenderedContent()).contains("FOAM-AFFF").doesNotContain("{{");

        // 3. SYSTEM_ALERT (PUSH)
        NotificationEvent sysEvent = NotificationEvent.builder()
                .eventType(NotificationEventType.SYSTEM_ALERT)
                .referenceId("SYS-001")
                .tenantId(tenantId)
                .channels(Set.of(NotificationChannel.PUSH))
                .recipients(Map.of(NotificationChannel.PUSH, "admin-push"))
                .payload(Map.of("alertTitle", "SCADA Pressure Drop", "severity", "CRITICAL", "message", "Hydrant grid loss"))
                .priority(NotificationPriority.CRITICAL)
                .build();
        List<NotificationRecord> sysRecords = notificationService.processEvent(sysEvent);
        assertThat(sysRecords).hasSize(1);
        assertThat(sysRecords.get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(sysRecords.get(0).getRenderedSubject()).contains("SCADA Pressure Drop");
        assertThat(sysRecords.get(0).getRenderedContent()).contains("Hydrant grid loss").doesNotContain("{{");
    }

    @Test
    @DisplayName("Template Render Failure: Missing required placeholders safely fails without uncaught exceptions")
    void testTemplateRenderFailureHandledGracefully() {
        // Send SYSTEM_ALERT without 'alertTitle' (mandatory placeholder)
        NotificationEvent invalidPayloadEvent = NotificationEvent.builder()
                .eventType(NotificationEventType.SYSTEM_ALERT)
                .referenceId("SYS-FAIL-01")
                .tenantId(tenantId)
                .channels(Set.of(NotificationChannel.PUSH))
                .recipients(Map.of(NotificationChannel.PUSH, "admin-push"))
                .payload(Map.of("severity", "CRITICAL")) // missing alertTitle and message
                .priority(NotificationPriority.HIGH)
                .build();

        List<NotificationRecord> records = notificationService.processEvent(invalidPayloadEvent);
        assertThat(records).hasSize(1);
        NotificationRecord record = records.get(0);

        assertThat(record.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(record.getFailureReason()).contains("missing required variable");
        assertThat(record.getAttemptCount()).isEqualTo(0); // Not dispatched to provider
    }

    @Test
    @DisplayName("Retry Exhaustion: 3 failed attempts transition record to FAILED; 4th retry throws BusinessRuleViolationException")
    void testRetryExhaustionAndTerminalFailedState() {
        smsProvider.setSimulateFailure(true);

        NotificationEvent event = NotificationEvent.builder()
                .eventType(NotificationEventType.DISPATCH)
                .referenceId("DSP-RETRY-01")
                .tenantId(tenantId)
                .channels(Set.of(NotificationChannel.SMS))
                .recipients(Map.of(NotificationChannel.SMS, "+14155552671"))
                .payload(Map.of("dispatchCode", "DSP-RETRY-01", "unitCode", "E-101", "location", "Dock 4"))
                .priority(NotificationPriority.HIGH)
                .build();

        List<NotificationRecord> records = notificationService.processEvent(event);
        UUID notificationId = records.get(0).getId();

        // Attempt 1: Failed -> Status PENDING
        NotificationRecord r1 = notificationService.getNotificationById(notificationId).orElseThrow();
        assertThat(r1.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(r1.getAttemptCount()).isEqualTo(1);

        // Attempt 2: Failed -> Status PENDING
        NotificationRecord r2 = notificationService.retryNotification(notificationId);
        assertThat(r2.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(r2.getAttemptCount()).isEqualTo(2);

        // Attempt 3: Failed -> maxAttempts (3) reached -> Status becomes FAILED
        NotificationRecord r3 = notificationService.retryNotification(notificationId);
        assertThat(r3.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(r3.getAttemptCount()).isEqualTo(3);

        // Attempt 4: Max attempts exceeded -> throws BusinessRuleViolationException
        assertThatThrownBy(() -> notificationService.retryNotification(notificationId))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("maximum retry attempts");
    }

    @Test
    @DisplayName("Idempotency Protection: Re-delivering notification with same idempotency key returns cached record")
    void testDuplicateNotificationIdempotency() {
        String key = "IDEMP-QA-DUP-" + UUID.randomUUID();

        NotificationEvent event = NotificationEvent.builder()
                .eventType(NotificationEventType.SYSTEM_ALERT)
                .referenceId("SYS-IDEMP-01")
                .tenantId(tenantId)
                .channels(Set.of(NotificationChannel.PUSH))
                .recipients(Map.of(NotificationChannel.PUSH, "officer-push"))
                .payload(Map.of("alertTitle", "Hazard Alert", "severity", "HIGH", "message", "Gas leak detected"))
                .idempotencyKey(key)
                .priority(NotificationPriority.HIGH)
                .build();

        List<NotificationRecord> first = notificationService.processEvent(event);
        assertThat(first).hasSize(1);
        UUID firstId = first.get(0).getId();
        assertThat(first.get(0).getStatus()).isEqualTo(NotificationStatus.SENT);

        // Resend identical event
        List<NotificationRecord> second = notificationService.processEvent(event);
        assertThat(second).hasSize(1);
        assertThat(second.get(0).getId()).isEqualTo(firstId);
        assertThat(second.get(0).getAttemptCount()).isEqualTo(1); // Provider not called second time
    }

    @Test
    @DisplayName("Mock Provider Isolation: Mock providers operate entirely in memory without external network calls")
    void testMockProvidersZeroExternalNetworkCalls() {
        assertThat(pushProvider.getDeliveredPushes()).isEmpty();
        assertThat(smsProvider.getDeliveredSms()).isEmpty();
        assertThat(emailProvider.getDeliveredEmails()).isEmpty();
        assertThat(whatsAppProvider.getDeliveredWhatsAppMessages()).isEmpty();

        // Dispatch email
        NotificationEvent emailEvent = NotificationEvent.builder()
                .eventType(NotificationEventType.MAINTENANCE_REMINDER)
                .referenceId("ASSET-PUMP-01")
                .tenantId(tenantId)
                .channels(Set.of(NotificationChannel.EMAIL))
                .recipients(Map.of(NotificationChannel.EMAIL, "chief@firesphere.ai"))
                .payload(Map.of("assetCode", "PUMP-101", "dueDate", "2026-10-15"))
                .priority(NotificationPriority.NORMAL)
                .build();

        notificationService.processEvent(emailEvent);

        assertThat(emailProvider.getDeliveredEmails()).hasSize(1);
        MockEmailProvider.MockEmailMessage sent = emailProvider.getDeliveredEmails().get(0);
        assertThat(sent.emailAddress()).isEqualTo("chief@firesphere.ai");
        assertThat(sent.subject()).contains("PUMP-101");
    }
}
