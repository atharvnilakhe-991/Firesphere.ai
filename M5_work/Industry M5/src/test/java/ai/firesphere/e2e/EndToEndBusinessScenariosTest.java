package ai.firesphere.e2e;

import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.contracts.integration.AIInspectionResultProviderContract.*;
import ai.firesphere.contracts.mock.MockAIInspectionResultProvider;
import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.integration.adapter.payment.PaymentIntegrationAdapter;
import ai.firesphere.integration.adapter.storage.StorageIntegrationAdapter;
import ai.firesphere.integration.api.payment.PaymentInitiateRequest;
import ai.firesphere.integration.api.payment.PaymentInitiateResult;
import ai.firesphere.integration.api.payment.PaymentWebhookRequest;
import ai.firesphere.integration.api.payment.PaymentWebhookResult;
import ai.firesphere.integration.api.storage.FileDownloadRequest;
import ai.firesphere.integration.api.storage.FileDownloadResult;
import ai.firesphere.integration.api.storage.FileUploadRequest;
import ai.firesphere.integration.api.storage.FileUploadResult;
import ai.firesphere.integration.core.configuration.IntegrationProperties;
import ai.firesphere.integration.core.idempotency.InMemoryIdempotencyStore;
import ai.firesphere.integration.core.logging.IntegrationLogger;
import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;
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

class EndToEndBusinessScenariosTest {

    private UUID tenantId;
    private Clock fixedClock;

    // Storage
    private MockStorageProvider storageProvider;
    private StorageIntegrationAdapter storageAdapter;

    // Payment
    private MockPaymentProvider paymentProvider;
    private PaymentIntegrationAdapter paymentAdapter;

    // AI
    private MockAIInspectionResultProvider aiProvider;
    private OperationalIntegrationServiceImpl operationalService;

    // Notification
    private NotificationServiceImpl notificationService;

    // Officer Operations
    private InMemoryOfficerTaskRepository taskRepository;
    private OfficerOperationsServiceImpl officerService;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        fixedClock = Clock.fixed(Instant.parse("2026-10-10T10:00:00Z"), ZoneOffset.UTC);

        // Security Context
        TestingAuthenticationToken auth = new TestingAuthenticationToken("OFFICER-7712", "pass", "ROLE_OFFICER");
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 1. Storage
        storageProvider = new MockStorageProvider();
        IntegrationProperties intProps = new IntegrationProperties();
        IntegrationRetryExecutor retryExecutor = new IntegrationRetryExecutor();
        InMemoryIdempotencyStore idempStore = new InMemoryIdempotencyStore();
        IntegrationLogger intLogger = new IntegrationLogger();
        storageAdapter = new StorageIntegrationAdapter(storageProvider, intProps, retryExecutor, idempStore, intLogger);

        // 2. Payment
        paymentProvider = new MockPaymentProvider();
        paymentAdapter = new PaymentIntegrationAdapter(paymentProvider, intProps, retryExecutor, idempStore, intLogger);

        // 3. AI
        aiProvider = new MockAIInspectionResultProvider();
        operationalService = new OperationalIntegrationServiceImpl(aiProvider, null);

        // 4. Notification
        notificationService = new NotificationServiceImpl(
                new MockPushProvider(),
                new MockSmsProvider(),
                new MockEmailProvider(),
                new MockWhatsAppProvider(),
                new NotificationTemplateRegistry(),
                new RecipientValidator(),
                new InMemoryNotificationRecordRepository()
        );

        // 5. Officer Operations
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

    @Test
    @DisplayName("Scenario A: Citizen Application Lifecycle - Document upload, payment initiation, webhook settlement, notification")
    void testScenarioA_CitizenApplicationLifecycle() {
        // Step 1: Citizen uploads floor plan blueprints to Object Storage
        byte[] blueprintBytes = "ARCHITECTURAL BLUEPRINT DRAWING 2026".getBytes(StandardCharsets.UTF_8);
        FileUploadRequest uploadReq = new FileUploadRequest(
                "commercial_tower_blueprint.pdf",
                "application/pdf",
                blueprintBytes,
                "FLOOR_PLAN",
                "APP-2026-NOC-001",
                Map.of("building", "Skyline Towers", "heightMeters", "45")
        );
        IntegrationResponse<FileUploadResult> uploadResp = storageAdapter.uploadFile(IntegrationRequest.of(uploadReq));
        assertThat(uploadResp.isSuccess()).isTrue();
        String fileId = uploadResp.data().fileId();

        // Verify file can be retrieved
        IntegrationResponse<FileDownloadResult> downloadResp = storageAdapter.downloadFile(
                IntegrationRequest.of(new FileDownloadRequest(fileId))
        );
        assertThat(downloadResp.isSuccess()).isTrue();
        assertThat(downloadResp.data().sizeBytes()).isEqualTo(blueprintBytes.length);

        // Step 2: Citizen initiates payment for NOC application
        PaymentInitiateRequest payReq = new PaymentInitiateRequest(
                "APP-2026-NOC-001",
                new BigDecimal("5000.00"),
                "INR",
                "manager@skylinetowers.com",
                "+919876543210",
                "Commercial NOC Application Fee"
        );
        IntegrationResponse<PaymentInitiateResult> payResp = paymentAdapter.initiatePayment(IntegrationRequest.of(payReq));
        assertThat(payResp.isSuccess()).isTrue();
        String orderId = payResp.data().paymentGatewayOrderId();

        // Step 3: Payment Gateway sends webhook notification with HMAC-SHA256 signature
        PaymentWebhookRequest webhookReq = new PaymentWebhookRequest(
                "payment.captured",
                "EVT-PAY-001",
                orderId,
                "TX-SETTLED-555",
                new BigDecimal("5000.00"),
                "INR",
                "CAPTURED",
                Instant.now(),
                Map.of("method", "NET_BANKING")
        );
        String rawWebhookPayload = webhookReq.paymentGatewayOrderId() + "|" + webhookReq.paymentTransactionId() + "|" + webhookReq.amount() + "|" + webhookReq.status();
        String signature = paymentProvider.generateWebhookSignature(rawWebhookPayload);

        IntegrationResponse<PaymentWebhookResult> webhookResp = paymentAdapter.processWebhook(
                IntegrationRequest.of(webhookReq),
                signature
        );
        assertThat(webhookResp.isSuccess()).isTrue();
        assertThat(webhookResp.data().status()).isEqualTo("CAPTURED");

        // Step 4: Dispatch Application Update Notification to Citizen
        NotificationEvent appEvent = NotificationEvent.builder()
                .eventType(NotificationEventType.APPLICATION_UPDATE)
                .referenceId("APP-2026-NOC-001")
                .tenantId(tenantId)
                .channels(Set.of(NotificationChannel.SMS, NotificationChannel.EMAIL))
                .recipients(Map.of(
                        NotificationChannel.SMS, "+919876543210",
                        NotificationChannel.EMAIL, "manager@skylinetowers.com"
                ))
                .payload(Map.of(
                        "applicationId", "APP-2026-NOC-001",
                        "status", "PAYMENT_CONFIRMED",
                        "applicantName", "Skyline Management"
                ))
                .build();
        List<NotificationRecord> notifs = notificationService.processEvent(appEvent);
        assertThat(notifs).hasSize(2);
        assertThat(notifs).allMatch(r -> r.getStatus() == NotificationStatus.SENT);
    }

    @Test
    @DisplayName("Scenario B: Officer Inspection Lifecycle - Assignment, checklist execution, notes, completion, report ready notice")
    void testScenarioB_OfficerInspectionLifecycle() {
        // Step 1: Create Officer Inspection Task with checklist items
        OfficerTaskCreateRequest createReq = new OfficerTaskCreateRequest(
                "INSP-TASK-2026-01",
                OfficerTaskType.INSPECTION,
                "Annual Safety Audit - Grand Hyatt Hotel",
                "Inspect emergency stairs, fire pump room, and riser valves",
                "OFFICER-7712",
                UUID.randomUUID(),
                DispatchPriority.HIGH,
                Instant.parse("2026-10-10T11:00:00Z"),
                Instant.parse("2026-10-10T16:00:00Z"),
                List.of(
                        "Check fire alarm panel status",
                        "Inspect riser valve pressure",
                        "Verify exit corridor clear of obstructions",
                        "Check fire extinguishers servicing dates"
                ),
                "Initial assignment for field audit"
        );

        OfficerTaskResponseDTO createdTask = officerService.createTask(createReq);
        assertThat(createdTask.status()).isEqualTo(OfficerTaskStatus.ASSIGNED);
        assertThat(createdTask.checklistItems()).hasSize(4);
        assertThat(createdTask.completedChecklistItems()).isEmpty();

        // Step 2: Officer updates task to IN_PROGRESS
        officerService.updateTaskStatus(
                createdTask.taskId(),
                new OfficerTaskStatusUpdateRequest(OfficerTaskStatus.IN_PROGRESS, "Arrived at Grand Hyatt, inspection underway")
        );

        // Step 3: Officer updates checklist progress and adds field notes
        OfficerTaskChecklistUpdateRequest checklistUpdate = new OfficerTaskChecklistUpdateRequest(
                List.of(
                        "Check fire alarm panel status",
                        "Inspect riser valve pressure",
                        "Verify exit corridor clear of obstructions",
                        "Check fire extinguishers servicing dates"
                ),
                "All 4 safety checks verified. Riser pressure normal at 7.2 bar. All extinguishers serviced."
        );
        OfficerTaskResponseDTO checklistUpdated = officerService.updateTaskChecklist(createdTask.taskId(), checklistUpdate);
        assertThat(checklistUpdated.completedChecklistItems()).hasSize(4);
        assertThat(checklistUpdated.inspectionNotes()).contains("7.2 bar");

        // Step 4: Officer completes inspection
        OfficerTaskResponseDTO completedTask = officerService.updateTaskStatus(
                createdTask.taskId(),
                new OfficerTaskStatusUpdateRequest(OfficerTaskStatus.COMPLETED, "Inspection completed satisfactorily")
        );
        assertThat(completedTask.status()).isEqualTo(OfficerTaskStatus.COMPLETED);

        // Verification: Notifications repository contains INSPECTION_REPORT_READY
        List<NotificationRecord> reportNotifs = notificationService.getNotificationsByReferenceId(createdTask.taskId().toString());
        assertThat(reportNotifs).isNotEmpty();
    }

    @Test
    @DisplayName("Scenario C: AI Evidence & Analysis Lifecycle - Photo upload, async AI job, hazard detection, explanation preservation")
    void testScenarioC_AIEvidenceAndAnalysisLifecycle() {
        UUID inspectionId = UUID.randomUUID();

        // Step 1: Upload inspection site photograph to Object Storage
        byte[] photoBytes = "JPEG RAW BYTES EMERGENCY EXIT PHOTO".getBytes(StandardCharsets.UTF_8);
        FileUploadRequest uploadReq = new FileUploadRequest(
                "emergency_exit_corridor.jpg",
                "image/jpeg",
                photoBytes,
                "EVIDENCE",
                inspectionId.toString(),
                Map.of("location", "Floor 2 East Wing")
        );
        IntegrationResponse<FileUploadResult> uploadResp = storageAdapter.uploadFile(IntegrationRequest.of(uploadReq));
        assertThat(uploadResp.isSuccess()).isTrue();
        String fileId = uploadResp.data().fileId();
        String storageUrl = uploadResp.data().storageUrl();

        // Step 2: Submit asynchronous AI computer vision analysis job
        AIJobSubmissionRequest jobReq = new AIJobSubmissionRequest(
                "JOB-AI-CORRIDOR-01",
                inspectionId,
                fileId,
                storageUrl,
                "BLOCKED_EXIT",
                Map.of("confidenceThreshold", "0.85")
        );

        AIJobStatusResponse jobResp = operationalService.submitAIJob(jobReq);
        assertThat(jobResp.status()).isEqualTo(AIJobState.COMPLETED);
        assertThat(jobResp.explanation()).contains("Computer vision model detected exit corridor obstruction");
        assertThat(jobResp.generatedFindings()).hasSize(1);

        AIFindingDTO finding = jobResp.generatedFindings().get(0);
        assertThat(finding.findingType()).isEqualTo("BLOCKED_EXIT");
        assertThat(finding.confidence()).isGreaterThan(0.90);
        assertThat(finding.status()).isEqualTo("PENDING_REVIEW"); // Guardrail: Human must review

        // Step 3: Trigger AI Analysis Completion notification to assigned officer
        NotificationEvent aiNotif = NotificationEvent.builder()
                .eventType(NotificationEventType.AI_ANALYSIS_COMPLETION)
                .referenceId("JOB-AI-CORRIDOR-01")
                .tenantId(tenantId)
                .channels(Set.of(NotificationChannel.PUSH, NotificationChannel.EMAIL))
                .recipients(Map.of(
                        NotificationChannel.PUSH, "device_OFFICER-7712",
                        NotificationChannel.EMAIL, "officer7712@firesphere.ai"
                ))
                .payload(Map.of(
                        "jobId", "JOB-AI-CORRIDOR-01",
                        "inspectionId", inspectionId.toString(),
                        "hazardCount", 1
                ))
                .build();
        List<NotificationRecord> records = notificationService.processEvent(aiNotif);
        assertThat(records).hasSize(2);
        assertThat(records).allMatch(r -> r.getStatus() == NotificationStatus.SENT);
    }

    @Test
    @DisplayName("Scenario D: Officer Decision & Compliance Lifecycle - Officer review, human override, compliance outcome")
    void testScenarioD_OfficerDecisionAndComplianceLifecycle() {
        UUID buildingId = UUID.randomUUID();

        // Ingest AI finding into system
        AIFindingDTO rawFinding = new AIFindingDTO(
                UUID.randomUUID(),
                buildingId,
                "EXTINGUISHER_MISSING",
                0.91,
                "HIGH",
                "mock://storage/evidence/photo_wall.jpg",
                "{\"boxes\":[]}",
                "PENDING_REVIEW",
                null,
                null,
                Instant.now(),
                null
        );
        AIFindingIngestResult ingestResult = operationalService.ingestAIFinding(rawFinding);
        UUID findingId = ingestResult.findingId();

        // Human officer examines evidence: finds that extinguisher was temporarily moved for servicing
        AIFindingReviewRequest review = new AIFindingReviewRequest(
                "APPROVED",
                "Verified missing extinguisher in server room; replacement issued on-site immediately",
                "OFFICER-7712"
        );

        AIFindingDTO reviewedFinding = operationalService.reviewAIFinding(findingId, review);
        assertThat(reviewedFinding.status()).isEqualTo("APPROVED");
        assertThat(reviewedFinding.reviewedBy()).isEqualTo("OFFICER-7712");
        assertThat(reviewedFinding.reviewerNotes()).contains("replacement issued");
    }

    @Test
    @DisplayName("Scenario E: Annual Renewal Lifecycle - Expiry tracking, automated renewal reminder, renewal task creation")
    void testScenarioE_AnnualRenewalLifecycle() {
        String certNumber = "NOC-MUM-2025-0044";
        String facility = "Metro Commercial Plaza";
        String expiry = "2026-11-15";

        // Step 1: Automated renewal reminder dispatched to management across Email and WhatsApp
        NotificationEvent renewalEvent = NotificationEvent.builder()
                .eventType(NotificationEventType.RENEWAL_REMINDER)
                .referenceId(certNumber)
                .tenantId(tenantId)
                .channels(Set.of(NotificationChannel.EMAIL, NotificationChannel.WHATSAPP))
                .recipients(Map.of(
                        NotificationChannel.EMAIL, "management@metroplaza.in",
                        NotificationChannel.WHATSAPP, "+919876543210"
                ))
                .payload(Map.of(
                        "certificateNumber", certNumber,
                        "facilityName", facility,
                        "expiryDate", expiry
                ))
                .build();

        List<NotificationRecord> records = notificationService.processEvent(renewalEvent);
        assertThat(records).hasSize(2);
        assertThat(records).allMatch(r -> r.getStatus() == NotificationStatus.SENT);

        // Step 2: System creates annual renewal inspection task with high priority
        OfficerTaskCreateRequest renewalTaskReq = new OfficerTaskCreateRequest(
                "TASK-RENEWAL-" + certNumber,
                OfficerTaskType.INSPECTION,
                "Annual Fire NOC Renewal Audit: " + facility,
                "Verify maintenance logs and equipment calibration for NOC renewal",
                "OFFICER-7712",
                UUID.randomUUID(),
                DispatchPriority.HIGH,
                Instant.parse("2026-10-15T09:00:00Z"),
                Instant.parse("2026-10-20T17:00:00Z"),
                List.of("Verify sprinkler riser pressure", "Check smoke detector test records"),
                "Scheduled ahead of certificate expiry on " + expiry
        );

        OfficerTaskResponseDTO renewalTask = officerService.createTask(renewalTaskReq);
        assertThat(renewalTask.taskReference()).isEqualTo("TASK-RENEWAL-" + certNumber);
        assertThat(renewalTask.priority()).isEqualTo(DispatchPriority.HIGH);
        assertThat(renewalTask.status()).isEqualTo(OfficerTaskStatus.ASSIGNED);
    }
}
