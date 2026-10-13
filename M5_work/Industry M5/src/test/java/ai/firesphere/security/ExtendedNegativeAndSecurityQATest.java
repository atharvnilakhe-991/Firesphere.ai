package ai.firesphere.security;

import ai.firesphere.common.exception.ConflictException;
import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.contracts.integration.AIInspectionResultProviderContract.*;
import ai.firesphere.contracts.mock.MockAIInspectionResultProvider;
import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.integration.adapter.payment.PaymentIntegrationAdapter;
import ai.firesphere.integration.adapter.storage.StorageIntegrationAdapter;
import ai.firesphere.integration.api.payment.PaymentWebhookRequest;
import ai.firesphere.integration.api.payment.PaymentWebhookResult;
import ai.firesphere.integration.api.storage.FileDownloadRequest;
import ai.firesphere.integration.api.storage.FileDownloadResult;
import ai.firesphere.integration.api.storage.FileUploadRequest;
import ai.firesphere.integration.api.storage.FileUploadResult;
import ai.firesphere.integration.core.configuration.IntegrationProperties;
import ai.firesphere.integration.core.idempotency.IdempotencyKey;
import ai.firesphere.integration.core.idempotency.InMemoryIdempotencyStore;
import ai.firesphere.integration.core.logging.IntegrationLogger;
import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;
import ai.firesphere.integration.core.model.IntegrationStatus;
import ai.firesphere.integration.core.retry.IntegrationRetryExecutor;
import ai.firesphere.integration.dto.AIFindingReviewRequest;
import ai.firesphere.integration.provider.payment.MockPaymentProvider;
import ai.firesphere.integration.provider.storage.MockStorageProvider;
import ai.firesphere.integration.service.OperationalIntegrationServiceImpl;
import ai.firesphere.notification.channel.NotificationChannel;
import ai.firesphere.notification.event.NotificationEvent;
import ai.firesphere.notification.event.NotificationEventType;
import ai.firesphere.notification.model.NotificationRecord;
import ai.firesphere.notification.model.NotificationStatus;
import ai.firesphere.notification.provider.mock.MockEmailProvider;
import ai.firesphere.notification.provider.mock.MockPushProvider;
import ai.firesphere.notification.provider.mock.MockSmsProvider;
import ai.firesphere.notification.provider.mock.MockWhatsAppProvider;
import ai.firesphere.notification.repository.InMemoryNotificationRecordRepository;
import ai.firesphere.notification.service.NotificationServiceImpl;
import ai.firesphere.notification.template.NotificationTemplateRegistry;
import ai.firesphere.notification.validation.RecipientValidator;
import ai.firesphere.operations.officer.alert.OfficerEscalationService;
import ai.firesphere.operations.officer.alert.OperationalAlertService;
import ai.firesphere.operations.officer.config.OfficerOperationsProperties;
import ai.firesphere.operations.officer.dto.*;
import ai.firesphere.operations.officer.model.OfficerTaskStatus;
import ai.firesphere.operations.officer.model.OfficerTaskType;
import ai.firesphere.operations.officer.repository.InMemoryOfficerTaskRepository;
import ai.firesphere.operations.officer.security.OfficerSecurityValidator;
import ai.firesphere.operations.officer.service.OfficerOperationsServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtendedNegativeAndSecurityQATest {

    private UUID tenantA;
    private UUID tenantB;
    private Clock fixedClock;

    private MockStorageProvider storageProvider;
    private StorageIntegrationAdapter storageAdapter;

    private MockPaymentProvider paymentProvider;
    private PaymentIntegrationAdapter paymentAdapter;

    private MockAIInspectionResultProvider aiProvider;
    private OperationalIntegrationServiceImpl operationalService;

    private NotificationServiceImpl notificationService;

    private InMemoryOfficerTaskRepository taskRepository;
    private OfficerOperationsServiceImpl officerService;

    @BeforeEach
    void setUp() {
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        TenantContext.setTenantId(tenantA);
        fixedClock = Clock.fixed(Instant.parse("2026-10-10T10:00:00Z"), ZoneOffset.UTC);

        TestingAuthenticationToken auth = new TestingAuthenticationToken("OFFICER-7712", "pass", "ROLE_OFFICER");
        SecurityContextHolder.getContext().setAuthentication(auth);

        storageProvider = new MockStorageProvider();
        IntegrationProperties intProps = new IntegrationProperties();
        IntegrationRetryExecutor retryExecutor = new IntegrationRetryExecutor();
        InMemoryIdempotencyStore idempStore = new InMemoryIdempotencyStore();
        IntegrationLogger intLogger = new IntegrationLogger();
        storageAdapter = new StorageIntegrationAdapter(storageProvider, intProps, retryExecutor, idempStore, intLogger);

        paymentProvider = new MockPaymentProvider();
        paymentAdapter = new PaymentIntegrationAdapter(paymentProvider, intProps, retryExecutor, idempStore, intLogger);

        aiProvider = new MockAIInspectionResultProvider();
        operationalService = new OperationalIntegrationServiceImpl(aiProvider, null);

        notificationService = new NotificationServiceImpl(
                new MockPushProvider(),
                new MockSmsProvider(),
                new MockEmailProvider(),
                new MockWhatsAppProvider(),
                new NotificationTemplateRegistry(),
                new RecipientValidator(),
                new InMemoryNotificationRecordRepository()
        );

        taskRepository = new InMemoryOfficerTaskRepository();
        OfficerOperationsProperties opsProps = new OfficerOperationsProperties();
        OperationalAlertService alertService = new OperationalAlertService(opsProps, fixedClock, notificationService);
        OfficerEscalationService escalationService = new OfficerEscalationService(taskRepository, opsProps, fixedClock, notificationService);
        OfficerSecurityValidator securityValidator = new OfficerSecurityValidator();

        officerService = new OfficerOperationsServiceImpl(
                taskRepository,
                alertService,
                escalationService,
                securityValidator,
                opsProps,
                fixedClock,
                notificationService
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        storageProvider.reset();
        paymentProvider.reset();
        aiProvider.clear();
    }

    // 1. Cross-Tenant Task Isolation
    @Test
    @DisplayName("Negative 1: Attempting to access Tenant A task from Tenant B context throws ResourceNotFoundException")
    void testCrossTenantTaskAccessBlocked() {
        OfficerTaskCreateRequest req = new OfficerTaskCreateRequest(
                "TASK-TENANT-A-01", OfficerTaskType.INSPECTION, "Inspect Mill", "Notes",
                "OFFICER-7712", UUID.randomUUID(), DispatchPriority.HIGH, null, null
        );
        OfficerTaskResponseDTO created = officerService.createTask(req);

        // Switch context to Tenant B
        TenantContext.setTenantId(tenantB);
        assertThatThrownBy(() -> officerService.getTask(created.taskId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // 2. Unauthenticated Access Rejection
    @Test
    @DisplayName("Negative 2: Unauthenticated caller attempting task retrieval is rejected with AccessDeniedException")
    void testUnauthenticatedAccessRejected() {
        OfficerTaskCreateRequest req = new OfficerTaskCreateRequest(
                "TASK-AUTH-01", OfficerTaskType.INSPECTION, "Inspect Mall", "Notes",
                "OFFICER-7712", UUID.randomUUID(), DispatchPriority.HIGH, null, null
        );
        OfficerTaskResponseDTO created = officerService.createTask(req);

        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> officerService.getTask(created.taskId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Full authentication is required");
    }

    // 3. Unauthorized Officer Access Rejection
    @Test
    @DisplayName("Negative 3: Officer attempting to access another officer's private task is rejected")
    void testUnauthorizedOfficerAccessRejected() {
        OfficerTaskCreateRequest req = new OfficerTaskCreateRequest(
                "TASK-OFFICER-PRIVATE", OfficerTaskType.INSPECTION, "Special Audit", "Confidential",
                "OFFICER-SPECIAL-99", UUID.randomUUID(), DispatchPriority.CRITICAL, null, null
        );
        OfficerTaskResponseDTO created = officerService.createTask(req);

        // Current authenticated officer is OFFICER-7712, attempting to access OFFICER-SPECIAL-99's task
        assertThatThrownBy(() -> officerService.getTask(created.taskId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not authorized to access records for badge");
    }

    // 4. Tampered Webhook Signature Rejection
    @Test
    @DisplayName("Negative 4: Inbound webhook with forged HMAC signature is rejected with ERR_PAYMENT_INVALID_SIGNATURE")
    void testForgedWebhookSignatureRejected() {
        PaymentWebhookRequest webhook = new PaymentWebhookRequest(
                "payment.captured", "EVT-FORGED-01", "ORD-1", "TX-1",
                new BigDecimal("1000.00"), "INR", "CAPTURED", Instant.now(), Map.of()
        );
        IntegrationResponse<PaymentWebhookResult> resp = paymentAdapter.processWebhook(
                IntegrationRequest.of(webhook), "invalid_tampered_signature_hex"
        );
        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.error().code()).isEqualTo("ERR_PAYMENT_INVALID_SIGNATURE");
    }

    // 5. Replay Attack / Webhook Idempotency Protection
    @Test
    @DisplayName("Negative 5: Replayed webhook request returns DUPLICATE status without re-executing business logic")
    void testReplayedWebhookIdempotency() {
        PaymentWebhookRequest webhook = new PaymentWebhookRequest(
                "payment.captured", "EVT-REPLAY-01", "ORD-REPLAY", "TX-REPLAY",
                new BigDecimal("1000.00"), "INR", "CAPTURED", Instant.now(), Map.of()
        );
        String raw = webhook.paymentGatewayOrderId() + "|" + webhook.paymentTransactionId() + "|" + webhook.amount() + "|" + webhook.status();
        String sig = paymentProvider.generateWebhookSignature(raw);
        IdempotencyKey key = IdempotencyKey.of("IDEMP-REPLAY-KEY");

        IntegrationResponse<PaymentWebhookResult> resp1 = paymentAdapter.processWebhook(
                IntegrationRequest.of("C1", key, webhook), sig
        );
        assertThat(resp1.isSuccess()).isTrue();

        IntegrationResponse<PaymentWebhookResult> resp2 = paymentAdapter.processWebhook(
                IntegrationRequest.of("C2", key, webhook), sig
        );
        assertThat(resp2.status()).isEqualTo(IntegrationStatus.DUPLICATE);
    }

    // 6. Oversized Evidence File Upload Rejection
    @Test
    @DisplayName("Negative 6: Object upload exceeding 10MB default threshold is rejected with ERR_STORAGE_PAYLOAD_TOO_LARGE")
    void testOversizedStoragePayloadRejected() {
        storageProvider.setMaxFileSizeBytes(1024); // Set to 1KB for test
        byte[] largeBytes = new byte[2048];

        FileUploadRequest req = new FileUploadRequest(
                "huge_video.mp4", "image/jpeg", largeBytes, "EVIDENCE", "INSP-1", Map.of()
        );
        IntegrationResponse<FileUploadResult> resp = storageAdapter.uploadFile(IntegrationRequest.of(req));
        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.error().code()).isEqualTo("ERR_STORAGE_PAYLOAD_TOO_LARGE");
    }

    // 7. Unsupported / Dangerous MIME Type Upload Rejection
    @Test
    @DisplayName("Negative 7: Object upload with dangerous executable/script MIME type is rejected")
    void testDangerousMimeTypeRejected() {
        byte[] bytes = "#!/bin/bash\nrm -rf /".getBytes(StandardCharsets.UTF_8);
        FileUploadRequest req = new FileUploadRequest(
                "malicious.sh", "application/x-sh", bytes, "EXPLOIT", "INSP-1", Map.of()
        );
        IntegrationResponse<FileUploadResult> resp = storageAdapter.uploadFile(IntegrationRequest.of(req));
        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.error().code()).isEqualTo("ERR_STORAGE_UNSUPPORTED_MEDIA_TYPE");
    }

    // 8. SQL Injection Strings Handled Safely in Notes/References
    @Test
    @DisplayName("Negative 8: SQL injection payloads in task reference and notes are safely treated as literal text")
    void testSqlInjectionHandledSafely() {
        String sqlPayload = "' OR '1'='1'; DROP TABLE officer_tasks; --";
        OfficerTaskCreateRequest req = new OfficerTaskCreateRequest(
                "SQLI-TEST-01", OfficerTaskType.INSPECTION, "SQLi Test", sqlPayload,
                "OFFICER-7712", UUID.randomUUID(), DispatchPriority.LOW, null, null
        );
        OfficerTaskResponseDTO created = officerService.createTask(req);
        assertThat(created.description()).isEqualTo(sqlPayload);

        OfficerTaskResponseDTO fetched = officerService.getTask(created.taskId());
        assertThat(fetched.description()).isEqualTo(sqlPayload);
    }

    // 9. XSS Payloads Handled Safely in Notes
    @Test
    @DisplayName("Negative 9: XSS script payloads in checklist notes are safely preserved as raw text without execution")
    void testXssPayloadHandledSafely() {
        String xssPayload = "<script>alert('xss-vulnerability');</script>";
        OfficerTaskCreateRequest req = new OfficerTaskCreateRequest(
                "XSS-TEST-01", OfficerTaskType.INSPECTION, "XSS Test", "Description",
                "OFFICER-7712", UUID.randomUUID(), DispatchPriority.LOW, null, null
        );
        OfficerTaskResponseDTO created = officerService.createTask(req);

        OfficerTaskChecklistUpdateRequest updateReq = new OfficerTaskChecklistUpdateRequest(
                List.of("Check 1"), xssPayload
        );
        OfficerTaskResponseDTO updated = officerService.updateTaskChecklist(created.taskId(), updateReq);
        assertThat(updated.inspectionNotes()).isEqualTo(xssPayload);
    }

    // 10. Malformed Boundary Violation: Duplicate Reference
    @Test
    @DisplayName("Negative 10: Duplicate task reference within the same tenant throws ConflictException")
    void testDuplicateTaskReferenceThrowsConflict() {
        OfficerTaskCreateRequest req = new OfficerTaskCreateRequest(
                "TASK-DUP-REF", OfficerTaskType.INSPECTION, "Initial", "Desc",
                "OFFICER-7712", UUID.randomUUID(), DispatchPriority.LOW, null, null
        );
        officerService.createTask(req);

        assertThatThrownBy(() -> officerService.createTask(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists for this tenant");
    }

    // 11. Invalid Date Range: Scheduled Date After Deadline
    @Test
    @DisplayName("Negative 11: Task scheduledAt after deadlineAt throws IllegalArgumentException")
    void testScheduledAfterDeadlineRejected() {
        OfficerTaskCreateRequest req = new OfficerTaskCreateRequest(
                "TASK-TIME-ERR", OfficerTaskType.INSPECTION, "Title", "Desc",
                "OFFICER-7712", UUID.randomUUID(), DispatchPriority.LOW,
                Instant.parse("2026-10-15T12:00:00Z"),
                Instant.parse("2026-10-10T12:00:00Z")
        );

        assertThatThrownBy(() -> officerService.createTask(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be after deadlineAt");
    }

    // 12. Invalid Status Transition: From Terminal to Non-Terminal
    @Test
    @DisplayName("Negative 12: Transitioning a CANCELLED task to IN_PROGRESS throws IllegalStateException")
    void testInvalidStatusTransitionOnTerminalTask() {
        OfficerTaskCreateRequest req = new OfficerTaskCreateRequest(
                "TASK-CANCEL-TEST", OfficerTaskType.INSPECTION, "Title", "Desc",
                "OFFICER-7712", UUID.randomUUID(), DispatchPriority.LOW, null, null
        );
        OfficerTaskResponseDTO created = officerService.createTask(req);
        officerService.updateTaskStatus(created.taskId(), new OfficerTaskStatusUpdateRequest(OfficerTaskStatus.CANCELLED, "Cancelled by user"));

        assertThatThrownBy(() -> officerService.updateTaskStatus(
                created.taskId(),
                new OfficerTaskStatusUpdateRequest(OfficerTaskStatus.IN_PROGRESS, "Trying to reopen")
        )).isInstanceOf(IllegalStateException.class);
    }

    // 13. Invalid AI Confidence Score Rejection
    @Test
    @DisplayName("Negative 13: Ingesting AI finding with confidence < 0.0, > 1.0, or NaN throws IllegalArgumentException")
    void testInvalidAIConfidenceScoreRejected() {
        AIFindingDTO invalidLow = new AIFindingDTO(
                UUID.randomUUID(), UUID.randomUUID(), "HAZARD", -0.5, "HIGH",
                "url", "{}", "PENDING_REVIEW", null, null, Instant.now(), null
        );
        assertThatThrownBy(() -> operationalService.ingestAIFinding(invalidLow))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence score must be between 0.0 and 1.0");

        AIFindingDTO invalidHigh = new AIFindingDTO(
                UUID.randomUUID(), UUID.randomUUID(), "HAZARD", 1.5, "HIGH",
                "url", "{}", "PENDING_REVIEW", null, null, Instant.now(), null
        );
        assertThatThrownBy(() -> operationalService.ingestAIFinding(invalidHigh))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 14. Invalid AI Review Decision Rejection
    @Test
    @DisplayName("Negative 14: Reviewing AI finding with illegal decision throws IllegalArgumentException")
    void testInvalidAIReviewDecisionRejected() {
        AIFindingDTO valid = new AIFindingDTO(
                UUID.randomUUID(), UUID.randomUUID(), "HAZARD", 0.88, "MEDIUM",
                "url", "{}", "PENDING_REVIEW", null, null, Instant.now(), null
        );
        AIFindingIngestResult res = operationalService.ingestAIFinding(valid);

        AIFindingReviewRequest invalidReview = new AIFindingReviewRequest("DESTROY", "notes", "OFFICER-7712");
        assertThatThrownBy(() -> operationalService.reviewAIFinding(res.findingId(), invalidReview))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid review decision");
    }

    // 15. Notification Without Required Recipient Validation Rejection
    @Test
    @DisplayName("Negative 15: Notification with invalid phone number fails validation")
    void testNotificationInvalidRecipientRejected() {
        NotificationEvent event = NotificationEvent.builder()
                .eventType(NotificationEventType.SYSTEM_ALERT)
                .referenceId("REF-INVALID-PHONE")
                .tenantId(tenantA)
                .channels(Set.of(NotificationChannel.SMS))
                .recipients(Map.of(NotificationChannel.SMS, "invalid-not-a-number"))
                .payload(Map.of("alertType", "SYS", "message", "Test alert"))
                .build();

        List<NotificationRecord> records = notificationService.processEvent(event);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(records.get(0).getFailureReason()).contains("Invalid recipient");
    }

    // 16. Non-Existent Storage File Download 404
    @Test
    @DisplayName("Negative 16: Downloading non-existent file ID returns failure response with ERR_STORAGE_FILE_NOT_FOUND")
    void testNonExistentFileDownloadReturnsNotFound() {
        IntegrationResponse<FileDownloadResult> resp = storageAdapter.downloadFile(
                IntegrationRequest.of(new FileDownloadRequest("FILE-NON-EXISTENT-ID"))
        );
        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.error().code()).isEqualTo("ERR_STORAGE_FILE_NOT_FOUND");
    }
}
