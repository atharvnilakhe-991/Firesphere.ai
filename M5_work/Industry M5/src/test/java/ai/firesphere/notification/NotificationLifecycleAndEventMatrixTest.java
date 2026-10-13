package ai.firesphere.notification;

import ai.firesphere.common.tenant.TenantContext;
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
import ai.firesphere.notification.template.NotificationTemplate;
import ai.firesphere.notification.template.NotificationTemplateRegistry;
import ai.firesphere.notification.validation.RecipientValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationLifecycleAndEventMatrixTest {

    private MockPushProvider pushProvider;
    private MockSmsProvider smsProvider;
    private MockEmailProvider emailProvider;
    private MockWhatsAppProvider whatsAppProvider;
    private NotificationTemplateRegistry templateRegistry;
    private RecipientValidator recipientValidator;
    private NotificationRecordRepository repository;
    private NotificationService notificationService;
    private UUID tenantId;

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

        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Registry has exactly 56 templates (14 event types * 4 channels)")
    void testTemplateRegistryMatrixCompleteness() {
        Map<String, NotificationTemplate> allTemplates = templateRegistry.getAllTemplates();
        // 14 event types x 4 channels = 56 templates
        assertThat(NotificationEventType.values()).hasSize(14);
        assertThat(NotificationChannel.values()).hasSize(4);
        assertThat(allTemplates).hasSize(56);

        for (NotificationEventType eventType : NotificationEventType.values()) {
            for (NotificationChannel channel : NotificationChannel.values()) {
                Optional<NotificationTemplate> found = templateRegistry.findTemplate(eventType, channel);
                assertThat(found)
                        .withFailMessage("Template missing for %s on channel %s", eventType, channel)
                        .isPresent();
                assertThat(found.get().getBodyTemplate()).isNotBlank();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(NotificationEventType.class)
    @DisplayName("Process event dispatches across all 4 channels for every event type")
    void testDispatchMatrixAllEventTypesAllChannels(NotificationEventType eventType) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("incidentNumber", "INC-8891");
        payload.put("address", "101 Marine Drive");
        payload.put("severity", "HIGH");
        payload.put("stationName", "Station 5");
        payload.put("vehicleId", "TRUCK-01");
        payload.put("vehiclePlate", "MH-01-AB-1234");
        payload.put("issue", "Brake fluid pressure low");
        payload.put("itemName", "Foam Extinguisher 9L");
        payload.put("currentQuantity", 2);
        payload.put("threshold", 5);
        payload.put("unit", "bottles");
        payload.put("equipmentName", "Hydraulic Cutter");
        payload.put("maintenanceType", "Annual Overhaul");
        payload.put("dueDate", "2026-11-01");
        payload.put("alertType", "WATER_PRESSURE_DROP");
        payload.put("message", "Hydrant loop pressure drop detected");
        payload.put("inspectionCode", "INSP-5501");
        payload.put("inspectionId", "INSP-5501");
        payload.put("facilityName", "Apex Shopping Mall");
        payload.put("date", "2026-10-15");
        payload.put("certNumber", "NOC-2026-7788");
        payload.put("certificateNumber", "NOC-2026-7788");
        payload.put("expiryDate", "2027-10-15");
        payload.put("applicationId", "APP-NOC-9900");
        payload.put("status", "UNDER_REVIEW");
        payload.put("applicantName", "John Doe");
        payload.put("scheduledDate", "2026-10-20");
        payload.put("officerBadge", "CHIEF-101");
        payload.put("taskId", "TASK-INSP-01");
        payload.put("jobId", "AI-JOB-882");
        payload.put("hazardCount", 2);
        payload.put("reportId", "RPT-2026-001");
        payload.put("outcome", "COMPLIANT");
        payload.put("vehicleCode", "TRUCK-01");
        payload.put("details", "Brake fluid pressure low");
        payload.put("sku", "EXT-FOAM-9L");
        payload.put("quantity", 2);
        payload.put("stationId", "STN-5");
        payload.put("assetCode", "HYD-CUT-01");
        payload.put("alertTitle", "Water Loop Warning");
        payload.put("dispatchCode", "DSP-9901");
        payload.put("unitCode", "ENGINE-1");
        payload.put("location", "101 Marine Drive");

        NotificationEvent event = NotificationEvent.builder()
                .eventType(eventType)
                .referenceId("REF-" + eventType.name())
                .tenantId(tenantId)
                .channels(Set.of(NotificationChannel.PUSH, NotificationChannel.SMS, NotificationChannel.EMAIL, NotificationChannel.WHATSAPP))
                .recipients(Map.of(
                        NotificationChannel.PUSH, "fcm_token_device_abc",
                        NotificationChannel.SMS, "+919876543210",
                        NotificationChannel.EMAIL, "officer@firesafety.gov",
                        NotificationChannel.WHATSAPP, "+919876543210"
                ))
                .payload(payload)
                .idempotencyKey("IDEMP-" + eventType.name() + "-" + UUID.randomUUID())
                .priority(NotificationPriority.HIGH)
                .build();

        List<NotificationRecord> records = notificationService.processEvent(event);

        assertThat(records).hasSize(4);
        for (NotificationRecord record : records) {
            assertThat(record.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(record.getRenderedContent()).isNotBlank();
            assertThat(record.getRenderedContent()).doesNotContain("{{"); // No unresolved placeholders
        }
    }

    @Test
    @DisplayName("Duplicate event with same idempotency key is suppressed")
    void testDuplicateIdempotencySuppression() {
        String key = "IDEMP-RENEWAL-ONCE";

        NotificationEvent event1 = NotificationEvent.builder()
                .eventType(NotificationEventType.RENEWAL_REMINDER)
                .referenceId("CERT-991")
                .tenantId(tenantId)
                .channels(Set.of(NotificationChannel.SMS))
                .recipients(Map.of(NotificationChannel.SMS, "+919876543210"))
                .payload(Map.of(
                        "certificateNumber", "NOC-991",
                        "facilityName", "Global Hotel",
                        "expiryDate", "2026-12-31"
                ))
                .idempotencyKey(key)
                .build();

        List<NotificationRecord> first = notificationService.processEvent(event1);
        assertThat(first).hasSize(1);
        assertThat(first.get(0).getStatus()).isEqualTo(NotificationStatus.SENT);

        // Same idempotency key dispatched again
        NotificationEvent event2 = NotificationEvent.builder()
                .eventType(NotificationEventType.RENEWAL_REMINDER)
                .referenceId("CERT-991")
                .tenantId(tenantId)
                .channels(Set.of(NotificationChannel.SMS))
                .recipients(Map.of(NotificationChannel.SMS, "+919876543210"))
                .payload(Map.of(
                        "certificateNumber", "NOC-991",
                        "facilityName", "Global Hotel",
                        "expiryDate", "2026-12-31"
                ))
                .idempotencyKey(key)
                .build();

        List<NotificationRecord> second = notificationService.processEvent(event2);
        assertThat(second).hasSize(1);
        assertThat(second.get(0).getId()).isEqualTo(first.get(0).getId()); // Returns existing record
    }
}
