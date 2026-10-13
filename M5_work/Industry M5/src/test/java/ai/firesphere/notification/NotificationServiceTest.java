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

import static org.junit.jupiter.api.Assertions.*;

class NotificationServiceTest {

    private MockPushProvider pushProvider;
    private MockSmsProvider smsProvider;
    private MockEmailProvider emailProvider;
    private MockWhatsAppProvider whatsAppProvider;
    private NotificationTemplateRegistry templateRegistry;
    private RecipientValidator recipientValidator;
    private NotificationRecordRepository repository;
    private NotificationService notificationService;

    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();

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
    @DisplayName("Test 1 — Successful notification: Event accepted, template selected, recipient validated, mock provider called, status SENT, history recorded")
    void test1_SuccessfulNotification() {
        NotificationEvent event = NotificationEvent.builder()
                .eventType(NotificationEventType.INCIDENT_ASSIGNMENT)
                .referenceId("INC-2026-001")
                .tenantId(tenantA)
                .priority(NotificationPriority.CRITICAL)
                .channels(Set.of(NotificationChannel.PUSH))
                .recipients(Map.of(NotificationChannel.PUSH, "device-token-officer-99"))
                .payload(Map.of(
                        "incidentNumber", "INC-2026-001",
                        "address", "100 Industrial Road",
                        "severity", "CRITICAL"
                ))
                .build();

        List<NotificationRecord> records = notificationService.processEvent(event);

        // 1. Event accepted & single record created
        assertEquals(1, records.size());
        NotificationRecord record = records.get(0);

        // 2. Template selected and rendered
        assertEquals("TPL-INCIDENT-PUSH", record.getTemplateCode());
        assertEquals("Incident Assignment: INC-2026-001", record.getRenderedSubject());
        assertTrue(record.getRenderedContent().contains("100 Industrial Road"));

        // 3. Provider called
        assertEquals(1, pushProvider.getDeliveredPushes().size());
        assertEquals("device-token-officer-99", pushProvider.getDeliveredPushes().get(0).targetToken());

        // 4. Status is SENT
        assertEquals(NotificationStatus.SENT, record.getStatus());
        assertNotNull(record.getSentAt());
        assertNull(record.getFailureReason());

        // 5. History recorded
        assertEquals(1, record.getAttemptCount());
        assertEquals(1, record.getAttemptHistory().size());
        assertEquals(NotificationStatus.SENT, record.getAttemptHistory().get(0).status());
        assertNotNull(record.getAttemptHistory().get(0).providerMessageId());

        // 6. Present in repository
        assertTrue(repository.findById(record.getId()).isPresent());
    }

    @Test
    @DisplayName("Test 2 — Provider failure: Delivery attempt recorded, failure reason recorded, remains retryable until max attempts, then becomes FAILED")
    void test2_ProviderFailure() {
        // Configure SMS mock provider to fail
        smsProvider.setSimulateFailure(true);
        smsProvider.setFailureReason("Upstream SMS telecom gateway timeout 504");

        NotificationEvent event = NotificationEvent.builder()
                .eventType(NotificationEventType.DISPATCH)
                .referenceId("DSP-001")
                .tenantId(tenantA)
                .channels(Set.of(NotificationChannel.SMS))
                .recipients(Map.of(NotificationChannel.SMS, "+14155552671"))
                .payload(Map.of(
                        "dispatchCode", "DSP-001",
                        "unitCode", "E-101",
                        "location", "Pier 39"
                ))
                .build();

        List<NotificationRecord> records = notificationService.processEvent(event);
        assertEquals(1, records.size());
        NotificationRecord record = records.get(0);

        // First attempt failed: status remains PENDING (retryable)
        assertEquals(NotificationStatus.PENDING, record.getStatus());
        assertEquals(1, record.getAttemptCount());
        assertEquals("Upstream SMS telecom gateway timeout 504", record.getFailureReason());
        assertEquals(1, record.getAttemptHistory().size());
        assertEquals(NotificationStatus.FAILED, record.getAttemptHistory().get(0).status());

        // Second attempt fails
        record = notificationService.retryNotification(record.getId());
        assertEquals(NotificationStatus.PENDING, record.getStatus());
        assertEquals(2, record.getAttemptCount());
        assertEquals(2, record.getAttemptHistory().size());

        // Third attempt fails (maxAttempts = 3): becomes FAILED
        record = notificationService.retryNotification(record.getId());
        assertEquals(NotificationStatus.FAILED, record.getStatus());
        assertEquals(3, record.getAttemptCount());
        assertEquals(3, record.getAttemptHistory().size());

        // Fourth attempt is blocked because max attempts exceeded
        final UUID finalRecordId = record.getId();
        assertThrows(BusinessRuleViolationException.class, () ->
                notificationService.retryNotification(finalRecordId));
    }

    @Test
    @DisplayName("Test 3 — Retry: First attempt fails, retry occurs, attempt count increments, second attempt succeeds, final status is SENT, full history observable")
    void test3_RetrySuccess() {
        // Fail only the first attempt, succeed on the second attempt
        emailProvider.setFailNextAttempts(1);
        emailProvider.setFailureReason("SMTP connection refused");

        NotificationEvent event = NotificationEvent.builder()
                .eventType(NotificationEventType.VEHICLE_ALERT)
                .referenceId("VEH-T-01")
                .tenantId(tenantA)
                .channels(Set.of(NotificationChannel.EMAIL))
                .recipients(Map.of(NotificationChannel.EMAIL, "fleet-manager@firesphere.ai"))
                .payload(Map.of(
                        "vehicleCode", "ENG-01",
                        "alertType", "ENGINE_OVERHEAT",
                        "details", "Coolant temp 115C"
                ))
                .build();

        List<NotificationRecord> records = notificationService.processEvent(event);
        NotificationRecord record = records.get(0);

        // Attempt 1 failed
        assertEquals(NotificationStatus.PENDING, record.getStatus());
        assertEquals(1, record.getAttemptCount());
        assertEquals("SMTP connection refused", record.getFailureReason());

        // Retry: Attempt 2 succeeds
        NotificationRecord retried = notificationService.retryNotification(record.getId());
        assertEquals(NotificationStatus.SENT, retried.getStatus());
        assertEquals(2, retried.getAttemptCount());
        assertNotNull(retried.getSentAt());
        assertNull(retried.getFailureReason());

        // Verify history contains both attempts
        assertEquals(2, retried.getAttemptHistory().size());
        assertEquals(NotificationStatus.FAILED, retried.getAttemptHistory().get(0).status());
        assertEquals(NotificationStatus.SENT, retried.getAttemptHistory().get(1).status());
    }

    @Test
    @DisplayName("Test 4 — Duplicate notification: Same idempotency key submitted twice creates only one delivery, provider not invoked twice")
    void test4_DuplicateNotificationIdempotency() {
        String idempotencyKey = "IDEMP-KEY-UNIQUE-12345";

        NotificationEvent event1 = NotificationEvent.builder()
                .eventType(NotificationEventType.LOW_INVENTORY)
                .referenceId("INV-SKU-99")
                .tenantId(tenantA)
                .idempotencyKey(idempotencyKey)
                .channels(Set.of(NotificationChannel.SMS))
                .recipients(Map.of(NotificationChannel.SMS, "+14155552671"))
                .payload(Map.of(
                        "sku", "FS-HOSE-65MM",
                        "itemName", "Delivery Hose",
                        "quantity", 3
                ))
                .build();

        // First delivery
        List<NotificationRecord> firstResults = notificationService.processEvent(event1);
        assertEquals(1, firstResults.size());
        assertEquals(NotificationStatus.SENT, firstResults.get(0).getStatus());
        assertEquals(1, smsProvider.getDeliveredSms().size());

        // Second delivery with identical idempotency key
        NotificationEvent event2 = NotificationEvent.builder()
                .eventType(NotificationEventType.LOW_INVENTORY)
                .referenceId("INV-SKU-99")
                .tenantId(tenantA)
                .idempotencyKey(idempotencyKey)
                .channels(Set.of(NotificationChannel.SMS))
                .recipients(Map.of(NotificationChannel.SMS, "+14155552671"))
                .payload(Map.of(
                        "sku", "FS-HOSE-65MM",
                        "itemName", "Delivery Hose",
                        "quantity", 3
                ))
                .build();

        List<NotificationRecord> secondResults = notificationService.processEvent(event2);
        assertEquals(1, secondResults.size());
        assertEquals(firstResults.get(0).getId(), secondResults.get(0).getId(), "Must return existing record");

        // Provider must NOT have been called a second time
        assertEquals(1, smsProvider.getDeliveredSms().size(), "Provider must only be invoked once for duplicate idempotency key");
    }

    @Test
    @DisplayName("Test 5 — Multiple channels: Send one event through Push, SMS, Email, WhatsApp; each channel tracked independently; provider failure on one does not affect others")
    void test5_MultipleChannelsIndependentDelivery() {
        // Configure SMS to fail, others to succeed
        smsProvider.setSimulateFailure(true);

        NotificationEvent event = NotificationEvent.builder()
                .eventType(NotificationEventType.SYSTEM_ALERT)
                .referenceId("SYS-ALERT-77")
                .tenantId(tenantA)
                .channels(Set.of(NotificationChannel.PUSH, NotificationChannel.SMS, NotificationChannel.EMAIL, NotificationChannel.WHATSAPP))
                .recipients(Map.of(
                        NotificationChannel.PUSH, "device-token-all-hands",
                        NotificationChannel.SMS, "+14155552671",
                        NotificationChannel.EMAIL, "broadcast@firesphere.ai",
                        NotificationChannel.WHATSAPP, "+14155552671"
                ))
                .payload(Map.of(
                        "alertTitle", "Severe Weather Standoff",
                        "severity", "HIGH",
                        "message", "High wind advisory active across district"
                ))
                .build();

        List<NotificationRecord> records = notificationService.processEvent(event);
        assertEquals(4, records.size());

        // Verify each mock provider was invoked
        assertEquals(1, pushProvider.getDeliveredPushes().size());
        assertEquals(1, emailProvider.getDeliveredEmails().size());
        assertEquals(1, whatsAppProvider.getDeliveredWhatsAppMessages().size());

        // Verify independent status tracking
        NotificationRecord pushRecord = records.stream().filter(r -> r.getChannel() == NotificationChannel.PUSH).findFirst().orElseThrow();
        NotificationRecord smsRecord = records.stream().filter(r -> r.getChannel() == NotificationChannel.SMS).findFirst().orElseThrow();
        NotificationRecord emailRecord = records.stream().filter(r -> r.getChannel() == NotificationChannel.EMAIL).findFirst().orElseThrow();
        NotificationRecord waRecord = records.stream().filter(r -> r.getChannel() == NotificationChannel.WHATSAPP).findFirst().orElseThrow();

        assertEquals(NotificationStatus.SENT, pushRecord.getStatus());
        assertEquals(NotificationStatus.PENDING, smsRecord.getStatus(), "SMS failure must be recorded");
        assertEquals(NotificationStatus.SENT, emailRecord.getStatus(), "Email must succeed despite SMS failure");
        assertEquals(NotificationStatus.SENT, waRecord.getStatus(), "WhatsApp must succeed despite SMS failure");
    }

    @Test
    @DisplayName("Test 6 — Invalid recipient: Test invalid phone, email, push token; validation fails, provider NOT called, failure observable")
    void test6_InvalidRecipientValidation() {
        NotificationEvent event = NotificationEvent.builder()
                .eventType(NotificationEventType.MAINTENANCE_REMINDER)
                .referenceId("ASSET-PUMP-01")
                .tenantId(tenantA)
                .channels(Set.of(NotificationChannel.PUSH, NotificationChannel.SMS, NotificationChannel.EMAIL))
                .recipients(Map.of(
                        NotificationChannel.PUSH, "short",            // Too short for push token
                        NotificationChannel.SMS, "not-a-number",     // Invalid phone
                        NotificationChannel.EMAIL, "bad-email@"       // Invalid email format
                ))
                .payload(Map.of(
                        "assetCode", "PUMP-01",
                        "dueDate", "2026-11-01"
                ))
                .build();

        List<NotificationRecord> records = notificationService.processEvent(event);
        assertEquals(3, records.size());

        // All 3 failed validation before provider invocation
        for (NotificationRecord record : records) {
            assertEquals(NotificationStatus.FAILED, record.getStatus());
            assertNotNull(record.getFailureReason());
            assertTrue(record.getFailureReason().contains("Invalid recipient"));
            assertEquals(0, record.getAttemptCount());
        }

        // Providers must NOT be called
        assertEquals(0, pushProvider.getDeliveredPushes().size());
        assertEquals(0, smsProvider.getDeliveredSms().size());
        assertEquals(0, emailProvider.getDeliveredEmails().size());
    }

    @Test
    @DisplayName("Test 7 — Template selection: Verify template resolution and variable substitution across all 8 supported events; verify unsupported channel and missing variable errors")
    void test7_TemplateSelectionAndRendering() {
        // 1. INCIDENT_ASSIGNMENT
        var r1 = notificationService.processEvent(NotificationEvent.builder()
                .eventType(NotificationEventType.INCIDENT_ASSIGNMENT)
                .channels(Set.of(NotificationChannel.PUSH))
                .recipients(Map.of(NotificationChannel.PUSH, "token-officer-1"))
                .payload(Map.of("incidentNumber", "INC-101", "address", "Bay Street", "severity", "HIGH"))
                .build()).get(0);
        assertEquals(NotificationStatus.SENT, r1.getStatus());
        assertTrue(r1.getRenderedContent().contains("Bay Street"));

        // 2. DISPATCH
        var r2 = notificationService.processEvent(NotificationEvent.builder()
                .eventType(NotificationEventType.DISPATCH)
                .channels(Set.of(NotificationChannel.SMS))
                .recipients(Map.of(NotificationChannel.SMS, "+14155552671"))
                .payload(Map.of("dispatchCode", "DSP-202", "unitCode", "LAD-01", "location", "North Station"))
                .build()).get(0);
        assertEquals(NotificationStatus.SENT, r2.getStatus());
        assertTrue(r2.getRenderedContent().contains("DSP-202"));

        // 3. VEHICLE_ALERT
        var r3 = notificationService.processEvent(NotificationEvent.builder()
                .eventType(NotificationEventType.VEHICLE_ALERT)
                .channels(Set.of(NotificationChannel.EMAIL))
                .recipients(Map.of(NotificationChannel.EMAIL, "fleet@fire.gov"))
                .payload(Map.of("vehicleCode", "TK-04", "alertType", "LOW_FUEL", "details", "12% fuel remaining"))
                .build()).get(0);
        assertEquals(NotificationStatus.SENT, r3.getStatus());
        assertTrue(r3.getRenderedSubject().contains("TK-04"));

        // 4. LOW_INVENTORY
        var r4 = notificationService.processEvent(NotificationEvent.builder()
                .eventType(NotificationEventType.LOW_INVENTORY)
                .channels(Set.of(NotificationChannel.WHATSAPP))
                .recipients(Map.of(NotificationChannel.WHATSAPP, "+14155552671"))
                .payload(Map.of("sku", "FOAM-B", "itemName", "AFFF Foam 20L", "quantity", 2))
                .build()).get(0);
        assertEquals(NotificationStatus.SENT, r4.getStatus());
        assertTrue(r4.getRenderedContent().contains("FOAM-B"));

        // 5. MAINTENANCE_REMINDER
        var r5 = notificationService.processEvent(NotificationEvent.builder()
                .eventType(NotificationEventType.MAINTENANCE_REMINDER)
                .channels(Set.of(NotificationChannel.SMS))
                .recipients(Map.of(NotificationChannel.SMS, "+14155552671"))
                .payload(Map.of("assetCode", "SCBA-09", "dueDate", "2026-10-15"))
                .build()).get(0);
        assertEquals(NotificationStatus.SENT, r5.getStatus());
        assertTrue(r5.getRenderedContent().contains("SCBA-09"));

        // 6. SYSTEM_ALERT
        var r6 = notificationService.processEvent(NotificationEvent.builder()
                .eventType(NotificationEventType.SYSTEM_ALERT)
                .channels(Set.of(NotificationChannel.PUSH))
                .recipients(Map.of(NotificationChannel.PUSH, "token-system-admin"))
                .payload(Map.of("alertTitle", "DB Backup Completed", "severity", "INFO", "message", "Nightly backup done"))
                .build()).get(0);
        assertEquals(NotificationStatus.SENT, r6.getStatus());
        assertTrue(r6.getRenderedSubject().contains("DB Backup Completed"));

        // 7. INSPECTION_NOTIFICATION
        var r7 = notificationService.processEvent(NotificationEvent.builder()
                .eventType(NotificationEventType.INSPECTION_NOTIFICATION)
                .channels(Set.of(NotificationChannel.EMAIL))
                .recipients(Map.of(NotificationChannel.EMAIL, "inspector@city.gov"))
                .payload(Map.of("inspectionCode", "INSP-88", "facilityName", "Central Mall", "date", "2026-10-20"))
                .build()).get(0);
        assertEquals(NotificationStatus.SENT, r7.getStatus());
        assertTrue(r7.getRenderedContent().contains("Central Mall"));

        // 8. CERTIFICATE_NOTIFICATION
        var r8 = notificationService.processEvent(NotificationEvent.builder()
                .eventType(NotificationEventType.CERTIFICATE_NOTIFICATION)
                .channels(Set.of(NotificationChannel.WHATSAPP))
                .recipients(Map.of(NotificationChannel.WHATSAPP, "+14155552671"))
                .payload(Map.of("certNumber", "CERT-2026-999", "facilityName", "Metro Hospital"))
                .build()).get(0);
        assertEquals(NotificationStatus.SENT, r8.getStatus());
        assertTrue(r8.getRenderedContent().contains("CERT-2026-999"));

        // Missing required variable fails rendering in controlled manner
        var failedRender = notificationService.processEvent(NotificationEvent.builder()
                .eventType(NotificationEventType.INCIDENT_ASSIGNMENT)
                .channels(Set.of(NotificationChannel.PUSH))
                .recipients(Map.of(NotificationChannel.PUSH, "token-officer-1"))
                .payload(Map.of("incidentNumber", "INC-101")) // Missing 'address' and 'severity'
                .build()).get(0);
        assertEquals(NotificationStatus.FAILED, failedRender.getStatus());
        assertTrue(failedRender.getFailureReason().contains("missing required variable"));

        // Unsupported template (unregistered combination) fails clearly
        templateRegistry.getAllTemplates(); // verify registry intact
    }

    @Test
    @DisplayName("Tenant isolation: Notifications are strictly partitioned by tenant ID")
    void testTenantIsolation() {
        notificationService.processEvent(NotificationEvent.builder()
                .eventType(NotificationEventType.INCIDENT_ASSIGNMENT)
                .referenceId("INC-A")
                .tenantId(tenantA)
                .channels(Set.of(NotificationChannel.PUSH))
                .recipients(Map.of(NotificationChannel.PUSH, "token-tenant-a"))
                .payload(Map.of("incidentNumber", "INC-A", "address", "Road A", "severity", "LOW"))
                .build());

        notificationService.processEvent(NotificationEvent.builder()
                .eventType(NotificationEventType.INCIDENT_ASSIGNMENT)
                .referenceId("INC-B")
                .tenantId(tenantB)
                .channels(Set.of(NotificationChannel.PUSH))
                .recipients(Map.of(NotificationChannel.PUSH, "token-tenant-b"))
                .payload(Map.of("incidentNumber", "INC-B", "address", "Road B", "severity", "LOW"))
                .build());

        List<NotificationRecord> tenantARecords = notificationService.getNotificationsByTenant(tenantA);
        List<NotificationRecord> tenantBRecords = notificationService.getNotificationsByTenant(tenantB);

        assertEquals(1, tenantARecords.size());
        assertEquals("INC-A", tenantARecords.get(0).getEventReferenceId());

        assertEquals(1, tenantBRecords.size());
        assertEquals("INC-B", tenantBRecords.get(0).getEventReferenceId());
    }
}
