package ai.firesphere.integration;

import ai.firesphere.contracts.integration.AIInspectionResultProviderContract;
import ai.firesphere.contracts.integration.ExternalCadIntegrationContract;
import ai.firesphere.integration.adapter.email.EmailIntegrationAdapter;
import ai.firesphere.integration.adapter.government.GovernmentServiceIntegrationAdapter;
import ai.firesphere.integration.adapter.maps.MapsIntegrationAdapter;
import ai.firesphere.integration.adapter.payment.PaymentIntegrationAdapter;
import ai.firesphere.integration.adapter.signature.DigitalSignatureIntegrationAdapter;
import ai.firesphere.integration.adapter.sms.SmsIntegrationAdapter;
import ai.firesphere.integration.adapter.whatsapp.WhatsAppIntegrationAdapter;
import ai.firesphere.integration.api.email.EmailMessageRequest;
import ai.firesphere.integration.api.email.EmailMessageResult;
import ai.firesphere.integration.api.government.*;
import ai.firesphere.integration.api.maps.*;
import ai.firesphere.integration.api.payment.*;
import ai.firesphere.integration.api.signature.*;
import ai.firesphere.integration.api.sms.SmsMessageRequest;
import ai.firesphere.integration.api.sms.SmsMessageResult;
import ai.firesphere.integration.api.whatsapp.WhatsAppMessageRequest;
import ai.firesphere.integration.api.whatsapp.WhatsAppMessageResult;
import ai.firesphere.integration.core.configuration.IntegrationProperties;
import ai.firesphere.integration.core.correlation.CorrelationContext;
import ai.firesphere.integration.core.idempotency.IdempotencyKey;
import ai.firesphere.integration.core.idempotency.IdempotencyStore;
import ai.firesphere.integration.core.idempotency.InMemoryIdempotencyStore;
import ai.firesphere.integration.core.logging.IntegrationLogger;
import ai.firesphere.integration.core.model.IntegrationErrorCategory;
import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;
import ai.firesphere.integration.core.model.IntegrationStatus;
import ai.firesphere.integration.core.retry.IntegrationRetryExecutor;
import ai.firesphere.integration.dto.AIFindingReviewRequest;
import ai.firesphere.integration.provider.email.MockEmailProvider;
import ai.firesphere.integration.provider.government.MockGovernmentServiceProvider;
import ai.firesphere.integration.provider.maps.MockMapsProvider;
import ai.firesphere.integration.provider.payment.MockPaymentProvider;
import ai.firesphere.integration.provider.signature.MockDigitalSignatureProvider;
import ai.firesphere.integration.provider.sms.MockSmsProvider;
import ai.firesphere.integration.provider.whatsapp.MockWhatsAppProvider;
import ai.firesphere.integration.service.OperationalIntegrationService;
import ai.firesphere.integration.service.OperationalIntegrationServiceImpl;
import ai.firesphere.contracts.mock.MockAIInspectionResultProvider;
import ai.firesphere.contracts.mock.MockExternalCadIntegrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationFrameworkTest {

    private IntegrationProperties properties;
    private IntegrationRetryExecutor retryExecutor;
    private IdempotencyStore idempotencyStore;
    private IntegrationLogger logger;

    private MockPaymentProvider mockPaymentProvider;
    private PaymentIntegrationAdapter paymentAdapter;

    private MockSmsProvider mockSmsProvider;
    private SmsIntegrationAdapter smsAdapter;

    private MockEmailProvider mockEmailProvider;
    private EmailIntegrationAdapter emailAdapter;

    private MockWhatsAppProvider mockWhatsAppProvider;
    private WhatsAppIntegrationAdapter whatsAppAdapter;

    private MockMapsProvider mockMapsProvider;
    private MapsIntegrationAdapter mapsAdapter;

    private MockDigitalSignatureProvider mockSignatureProvider;
    private DigitalSignatureIntegrationAdapter signatureAdapter;

    private MockGovernmentServiceProvider mockGovernmentProvider;
    private GovernmentServiceIntegrationAdapter governmentAdapter;

    private OperationalIntegrationService operationalService;

    @BeforeEach
    void setUp() {
        properties = new IntegrationProperties();
        // Fast retries for testing
        properties.getPayment().setInitialRetryDelayMs(5);
        properties.getPayment().setMaxRetries(3);
        properties.getSms().setInitialRetryDelayMs(5);
        properties.getEmail().setInitialRetryDelayMs(5);
        properties.getWhatsapp().setInitialRetryDelayMs(5);
        properties.getMaps().setInitialRetryDelayMs(5);
        properties.getDigitalSignature().setInitialRetryDelayMs(5);
        properties.getCad().setInitialRetryDelayMs(5);

        retryExecutor = new IntegrationRetryExecutor();
        idempotencyStore = new InMemoryIdempotencyStore();
        logger = new IntegrationLogger();

        mockPaymentProvider = new MockPaymentProvider();
        paymentAdapter = new PaymentIntegrationAdapter(mockPaymentProvider, properties, retryExecutor, idempotencyStore, logger);

        mockSmsProvider = new MockSmsProvider();
        smsAdapter = new SmsIntegrationAdapter(mockSmsProvider, properties, retryExecutor, idempotencyStore, logger);

        mockEmailProvider = new MockEmailProvider();
        emailAdapter = new EmailIntegrationAdapter(mockEmailProvider, properties, retryExecutor, idempotencyStore, logger);

        mockWhatsAppProvider = new MockWhatsAppProvider();
        whatsAppAdapter = new WhatsAppIntegrationAdapter(mockWhatsAppProvider, properties, retryExecutor, idempotencyStore, logger);

        mockMapsProvider = new MockMapsProvider();
        mapsAdapter = new MapsIntegrationAdapter(mockMapsProvider, properties, retryExecutor, idempotencyStore, logger);

        mockSignatureProvider = new MockDigitalSignatureProvider();
        signatureAdapter = new DigitalSignatureIntegrationAdapter(mockSignatureProvider, properties, retryExecutor, idempotencyStore, logger);

        mockGovernmentProvider = new MockGovernmentServiceProvider();
        governmentAdapter = new GovernmentServiceIntegrationAdapter(mockGovernmentProvider, properties, retryExecutor, idempotencyStore, logger);

        operationalService = new OperationalIntegrationServiceImpl(
                new MockAIInspectionResultProvider(),
                new MockExternalCadIntegrationService()
        );

        CorrelationContext.clear();
    }

    // ==========================================
    // 1. Payment Integration Tests
    // ==========================================

    @Test
    @DisplayName("Payment: Successful initiation returns valid gateway order and URL")
    void testPaymentInitiationSuccess() {
        PaymentInitiateRequest payload = new PaymentInitiateRequest(
                "NOC-ORDER-101",
                new BigDecimal("2500.00"),
                "INR",
                "officer@firesphere.ai",
                "+919876543210",
                "Fire Safety NOC Verification Fee"
        );
        IntegrationRequest<PaymentInitiateRequest> request = IntegrationRequest.of(payload);

        IntegrationResponse<PaymentInitiateResult> response = paymentAdapter.initiatePayment(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.status()).isEqualTo(IntegrationStatus.SUCCESS);
        assertThat(response.data()).isNotNull();
        assertThat(response.data().paymentGatewayOrderId()).startsWith("order_mock_");
        assertThat(response.data().paymentUrl()).contains("checkout");
        assertThat(response.data().amount()).isEqualByComparingTo("2500.00");
        assertThat(mockPaymentProvider.getInvocationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Payment: Idempotent duplicate initiation replayed without provider call")
    void testPaymentInitiationIdempotency() {
        IdempotencyKey idempKey = IdempotencyKey.of("IDEMP-PAY-101");
        PaymentInitiateRequest payload = new PaymentInitiateRequest(
                "NOC-ORDER-102",
                new BigDecimal("500.00"),
                "INR",
                "user@firesphere.ai",
                "+919876543210",
                "Inspection Fee"
        );
        IntegrationRequest<PaymentInitiateRequest> req1 = IntegrationRequest.of("CORR-001", idempKey, payload);

        IntegrationResponse<PaymentInitiateResult> res1 = paymentAdapter.initiatePayment(req1);
        assertThat(res1.status()).isEqualTo(IntegrationStatus.SUCCESS);
        assertThat(mockPaymentProvider.getInvocationCount()).isEqualTo(1);

        // Second invocation with identical idempotency key
        IntegrationRequest<PaymentInitiateRequest> req2 = IntegrationRequest.of("CORR-002", idempKey, payload);
        IntegrationResponse<PaymentInitiateResult> res2 = paymentAdapter.initiatePayment(req2);

        assertThat(res2.status()).isEqualTo(IntegrationStatus.DUPLICATE);
        assertThat(res2.data()).isNotNull();
        assertThat(res2.data().paymentGatewayOrderId()).isEqualTo(res1.data().paymentGatewayOrderId());
        assertThat(mockPaymentProvider.getInvocationCount()).isEqualTo(1); // Provider NOT invoked again
    }

    @Test
    @DisplayName("Payment: Non-retryable failure halts immediately without retries")
    void testPaymentNonRetryableFailure() {
        mockPaymentProvider.simulateFailure("ERR_CARD_DECLINED", "Insufficient funds", false);

        PaymentInitiateRequest payload = new PaymentInitiateRequest(
                "NOC-ORDER-103",
                new BigDecimal("1000.00"),
                "INR",
                "user@firesphere.ai",
                "+919876543210",
                "License Fee"
        );
        IntegrationResponse<PaymentInitiateResult> response = paymentAdapter.initiatePayment(IntegrationRequest.of(payload));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.status()).isEqualTo(IntegrationStatus.FAILED);
        assertThat(response.getError()).isPresent();
        assertThat(response.getError().get().category()).isEqualTo(IntegrationErrorCategory.PROVIDER_ERROR);
        assertThat(response.getError().get().retryable()).isFalse();
        assertThat(mockPaymentProvider.getInvocationCount()).isEqualTo(1); // No retries for non-retryable failure
    }

    @Test
    @DisplayName("Payment: Transient failure recovers on retry attempt")
    void testPaymentTransientRetryRecovery() {
        // Fail 2 attempts with retryable error, 3rd attempt will succeed
        mockPaymentProvider.failNextAttempts(2, "ERR_GATEWAY_BUSY", "Temporary connection spike", true);

        PaymentInitiateRequest payload = new PaymentInitiateRequest(
                "NOC-ORDER-104",
                new BigDecimal("1200.00"),
                "INR",
                "user@firesphere.ai",
                "+919876543210",
                "Permit Renewal"
        );
        IntegrationResponse<PaymentInitiateResult> response = paymentAdapter.initiatePayment(IntegrationRequest.of(payload));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.status()).isEqualTo(IntegrationStatus.SUCCESS);
        assertThat(mockPaymentProvider.getInvocationCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Payment: Timeout error marked retryable")
    void testPaymentTimeoutHandling() {
        mockPaymentProvider.simulateTimeout(0); // Instant timeout exception

        PaymentInitiateRequest payload = new PaymentInitiateRequest(
                "NOC-ORDER-105",
                new BigDecimal("1500.00"),
                "INR",
                "user@firesphere.ai",
                "+919876543210",
                "NOC Clearance"
        );
        IntegrationResponse<PaymentInitiateResult> response = paymentAdapter.initiatePayment(IntegrationRequest.of(payload));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.status()).isEqualTo(IntegrationStatus.FAILED);
        assertThat(mockPaymentProvider.getInvocationCount()).isEqualTo(3); // retried 3 times
    }

    @Test
    @DisplayName("Payment: Verification and status checks execute successfully")
    void testPaymentVerificationAndStatus() {
        PaymentVerificationRequest verifyReq = new PaymentVerificationRequest(
                "order_mock_123",
                "pay_mock_999",
                "mock_sig_hash",
                new BigDecimal("2500.00")
        );
        IntegrationResponse<PaymentVerificationResult> verifyResp = paymentAdapter.verifyPayment(IntegrationRequest.of(verifyReq));
        assertThat(verifyResp.isSuccess()).isTrue();
        assertThat(verifyResp.data().verified()).isTrue();
        assertThat(verifyResp.data().status()).isEqualTo("SETTLED");

        IntegrationResponse<PaymentStatusResult> statusResp = paymentAdapter.checkStatus(IntegrationRequest.of("pay_mock_999"));
        assertThat(statusResp.isSuccess()).isTrue();
        assertThat(statusResp.data().status()).isEqualTo("CAPTURED");
    }

    // ==========================================
    // 2. SMS Integration Tests
    // ==========================================

    @Test
    @DisplayName("SMS: Dispatch succeeds and returns receipt with idempotency protection")
    void testSmsIntegrationSuccessAndIdempotency() {
        SmsMessageRequest payload = new SmsMessageRequest(
                "+919123456789",
                "Alert: EVACUATE Zone 4 immediately due to chemical fire",
                "TPL-EVAC-01",
                "FIRE-HQ"
        );
        IdempotencyKey key = IdempotencyKey.of("IDEMP-SMS-001");
        IntegrationRequest<SmsMessageRequest> req1 = IntegrationRequest.of("CORR-SMS-1", key, payload);

        IntegrationResponse<SmsMessageResult> res1 = smsAdapter.sendSms(req1);
        assertThat(res1.isSuccess()).isTrue();
        assertThat(res1.data().deliveryStatus()).isEqualTo("DELIVERED");
        assertThat(res1.data().externalMessageId()).startsWith("SMS-MOCK-");
        assertThat(mockSmsProvider.getInvocationCount()).isEqualTo(1);

        // Replay
        IntegrationResponse<SmsMessageResult> res2 = smsAdapter.sendSms(IntegrationRequest.of("CORR-SMS-2", key, payload));
        assertThat(res2.status()).isEqualTo(IntegrationStatus.DUPLICATE);
        assertThat(res2.data().externalMessageId()).isEqualTo(res1.data().externalMessageId());
        assertThat(mockSmsProvider.getInvocationCount()).isEqualTo(1);
    }

    // ==========================================
    // 3. Email Integration Tests
    // ==========================================

    @Test
    @DisplayName("Email: Dispatch succeeds and formats receipt")
    void testEmailIntegrationSuccess() {
        EmailMessageRequest payload = new EmailMessageRequest(
                "chief@firesphere.gov",
                "Urgent: Commercial Building NOC Revoked",
                "<h1>NOC Revocation Notice</h1><p>Building safety violation detected.</p>",
                "NOC Revocation Notice. Building safety violation detected.",
                List.of("admin@firesphere.gov"),
                Collections.emptyList()
        );
        IntegrationResponse<EmailMessageResult> response = emailAdapter.sendEmail(IntegrationRequest.of(payload));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.data().status()).isEqualTo("SENT");
        assertThat(response.data().externalMessageId()).startsWith("EMAIL-MOCK-");
        assertThat(mockEmailProvider.getInvocationCount()).isEqualTo(1);
    }

    // ==========================================
    // 4. WhatsApp Integration Tests
    // ==========================================

    @Test
    @DisplayName("WhatsApp: Dispatch message succeeds with template parameters")
    void testWhatsAppIntegrationSuccess() {
        WhatsAppMessageRequest payload = new WhatsAppMessageRequest(
                "+919888877777",
                "fire_safety_alert",
                "en",
                Collections.singletonMap("building_name", "Tower 1"),
                "Emergency alert for Tower 1"
        );
        IntegrationResponse<WhatsAppMessageResult> response = whatsAppAdapter.sendWhatsApp(IntegrationRequest.of(payload));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.data().deliveryStatus()).isEqualTo("DELIVERED");
        assertThat(response.data().externalMessageId()).startsWith("WA-MOCK-");
        assertThat(mockWhatsAppProvider.getInvocationCount()).isEqualTo(1);
    }

    // ==========================================
    // 5. Maps & Routing Integration Tests
    // ==========================================

    @Test
    @DisplayName("Maps: Geocoding, reverse geocoding, and route calculation execute correctly")
    void testMapsIntegrationOperations() {
        // Geocode
        GeocodingRequest geoReq = new GeocodingRequest("MG Road, Bengaluru", "Bengaluru", "560001", "IND");
        IntegrationResponse<GeocodingResult> geoResp = mapsAdapter.geocode(IntegrationRequest.of(geoReq));
        assertThat(geoResp.isSuccess()).isTrue();
        assertThat(geoResp.data().latitude()).isEqualTo(12.9716);
        assertThat(geoResp.data().longitude()).isEqualTo(77.5946);

        // Reverse geocode
        ReverseGeocodingRequest revReq = new ReverseGeocodingRequest(12.9716, 77.5946);
        IntegrationResponse<ReverseGeocodingResult> revResp = mapsAdapter.reverseGeocode(IntegrationRequest.of(revReq));
        assertThat(revResp.isSuccess()).isTrue();
        assertThat(revResp.data().city()).isEqualTo("Bengaluru");

        // Calculate Route
        RouteMatrixRequest routeReq = new RouteMatrixRequest(12.9716, 77.5946, 12.9800, 77.6000, "EMERGENCY");
        IntegrationResponse<RouteMatrixResult> routeResp = mapsAdapter.calculateRoute(IntegrationRequest.of(routeReq));
        assertThat(routeResp.isSuccess()).isTrue();
        assertThat(routeResp.data().distanceMeters()).isGreaterThan(0.0);
        assertThat(routeResp.data().estimatedDurationSeconds()).isGreaterThan(0L);
    }

    // ==========================================
    // 6. Digital Signature Integration Tests
    // ==========================================

    @Test
    @DisplayName("Digital Signature: Sign and verify certificates with cryptographic tokens")
    void testDigitalSignatureOperations() {
        byte[] docHash = "SHA256-HASH-OF-NOC-CERTIFICATE-DOCUMENT".getBytes(StandardCharsets.UTF_8);

        SignatureRequest signReq = new SignatureRequest(
                "NOC-DOC-777",
                "FIRE_SAFETY_NOC",
                docHash,
                "CHIEF_FIRE_OFFICER_01",
                "Chief Fire Officer",
                "Issuance of Fire Safety NOC"
        );
        IntegrationResponse<SignatureResult> signResp = signatureAdapter.signDocument(IntegrationRequest.of(signReq));
        assertThat(signResp.isSuccess()).isTrue();
        assertThat(signResp.data().signatureToken()).startsWith("SIG-TOKEN-");

        SignatureVerificationRequest verifyReq = new SignatureVerificationRequest(
                "NOC-DOC-777",
                docHash,
                signResp.data().signatureToken()
        );
        IntegrationResponse<SignatureVerificationResult> verifyResp = signatureAdapter.verifySignature(IntegrationRequest.of(verifyReq));
        assertThat(verifyResp.isSuccess()).isTrue();
        assertThat(verifyResp.data().valid()).isTrue();
    }

    // ==========================================
    // 7. Government Services & CAD Integration Tests
    // ==========================================

    @Test
    @DisplayName("Government & CAD: Incident transmission, status querying, and compliance checking")
    void testGovernmentCadOperations() {
        UUID incId = UUID.randomUUID();
        CadTransmitRequest cadReq = new CadTransmitRequest(
                incId,
                "INC-2026-999",
                "STRUCTURE_FIRE",
                "CRITICAL",
                12.9716,
                77.5946,
                "Brigade Road, Commercial Complex",
                "Security Guard",
                "+919876500000",
                "Smoke billowing from 3rd floor server room"
        );
        IntegrationResponse<CadTransmitResult> cadResp = governmentAdapter.transmitCadIncident(IntegrationRequest.of(cadReq));
        assertThat(cadResp.isSuccess()).isTrue();
        assertThat(cadResp.data().externalCadIncidentId()).startsWith("CAD-112-");
        assertThat(cadResp.data().status()).isEqualTo("TRANSMITTED");

        // Query CAD status
        IntegrationResponse<CadStatusQueryResult> statusResp = governmentAdapter.queryCadStatus(
                IntegrationRequest.of(new CadStatusQueryRequest(cadResp.data().externalCadIncidentId()))
        );
        assertThat(statusResp.isSuccess()).isTrue();
        assertThat(statusResp.data().externalStatus()).isEqualTo("ON_SCENE");

        // Compliance check
        ComplianceLookupRequest compReq = new ComplianceLookupRequest("TAX-BLDG-5554", "BBMP-NORTH", "COMMERCIAL_OFFICE");
        IntegrationResponse<ComplianceLookupResult> compResp = governmentAdapter.verifyCompliance(IntegrationRequest.of(compReq));
        assertThat(compResp.isSuccess()).isTrue();
        assertThat(compResp.data().compliant()).isTrue();
        assertThat(compResp.data().nocStatus()).isEqualTo("ACTIVE");
    }

    // ==========================================
    // 8. Correlation ID Propagation Tests
    // ==========================================

    @Test
    @DisplayName("CorrelationContext: Automatically preserves or generates correlation IDs")
    void testCorrelationContextHandling() {
        String customId = "CORR-AUDIT-XYZ-999";
        CorrelationContext.set(customId);
        assertThat(CorrelationContext.get()).isEqualTo(customId);

        String result = CorrelationContext.executeWith("CORR-INNER", () -> {
            assertThat(CorrelationContext.get()).isEqualTo("CORR-INNER");
            return "SUCCESS";
        });
        assertThat(result).isEqualTo("SUCCESS");
        // Restores outer correlation ID
        assertThat(CorrelationContext.get()).isEqualTo(customId);

        CorrelationContext.clear();
        String generated = CorrelationContext.getOrGenerate();
        assertThat(generated).startsWith("CORR-");
    }

    // ==========================================
    // 9. Operational Integration Service Tests (AI Inspection & CAD)
    // ==========================================

    @Test
    @DisplayName("Operational Integration: Ingest, get, and review AI inspection findings")
    void testOperationalIntegrationAiFindings() {
        UUID findingId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();

        AIInspectionResultProviderContract.AIFindingDTO finding = new AIInspectionResultProviderContract.AIFindingDTO(
                findingId,
                buildingId,
                "BLOCKED_EMERGENCY_EXIT",
                0.94,
                "HIGH",
                "https://storage.firesphere.ai/evidence/exit_blocked.jpg",
                "{\"x\": 10, \"y\": 20, \"w\": 100, \"h\": 150}",
                "PENDING_REVIEW",
                null,
                null,
                Instant.now(),
                null
        );

        AIInspectionResultProviderContract.AIFindingIngestResult ingestResult = operationalService.ingestAIFinding(finding);
        assertThat(ingestResult.accepted()).isTrue();
        assertThat(ingestResult.findingId()).isEqualTo(findingId);

        AIInspectionResultProviderContract.AIFindingDTO fetched = operationalService.getAIFinding(findingId);
        assertThat(fetched).isNotNull();
        assertThat(fetched.findingType()).isEqualTo("BLOCKED_EMERGENCY_EXIT");
        assertThat(fetched.status()).isEqualTo("PENDING_REVIEW");

        // Human-in-the-loop review
        AIFindingReviewRequest reviewReq = new AIFindingReviewRequest(
                "APPROVED",
                "Confirmed exit is barricaded by storage boxes. Notice issued.",
                "INSPECTOR_BADGE_441"
        );
        AIInspectionResultProviderContract.AIFindingDTO reviewed = operationalService.reviewAIFinding(findingId, reviewReq);
        assertThat(reviewed.status()).isEqualTo("APPROVED");
        assertThat(reviewed.reviewedBy()).isEqualTo("INSPECTOR_BADGE_441");
    }

    @Test
    @DisplayName("Operational Integration: Transmit incident to external CAD via operational service")
    void testOperationalIntegrationTransmitToCad() {
        UUID incId = UUID.randomUUID();
        ExternalCadIntegrationContract.CadIncidentDTO incident = new ExternalCadIntegrationContract.CadIncidentDTO(
                incId,
                "INC-2026-888",
                "ELECTRICAL_FIRE",
                "HIGH",
                13.0100,
                77.5500,
                "Industrial Area Phase 2",
                "Substation Operator",
                "+919876599999",
                "Transformer smoking"
        );

        ExternalCadIntegrationContract.CadTransmissionReceipt receipt = operationalService.transmitToCad(incident);
        assertThat(receipt).isNotNull();
        assertThat(receipt.externalCadIncidentId()).startsWith("CAD-EXT-");
        assertThat(receipt.status()).isEqualTo("TRANSMITTED");
        assertThat(receipt.internalIncidentId()).isEqualTo(incId);
    }
}
