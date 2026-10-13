package ai.firesphere.integration;

import ai.firesphere.contracts.integration.AIInspectionResultProviderContract;
import ai.firesphere.integration.api.government.CadTransmitRequest;
import ai.firesphere.integration.api.government.ComplianceLookupRequest;
import ai.firesphere.integration.api.email.EmailMessageRequest;
import ai.firesphere.integration.api.maps.GeocodingRequest;
import ai.firesphere.integration.api.maps.ReverseGeocodingRequest;
import ai.firesphere.integration.api.maps.RouteMatrixRequest;
import ai.firesphere.integration.api.payment.PaymentInitiateRequest;
import ai.firesphere.integration.api.payment.PaymentVerificationRequest;
import ai.firesphere.integration.api.signature.SignatureRequest;
import ai.firesphere.integration.api.signature.SignatureVerificationRequest;
import ai.firesphere.integration.api.sms.SmsMessageRequest;
import ai.firesphere.integration.api.whatsapp.WhatsAppMessageRequest;
import ai.firesphere.integration.dto.AIFindingReviewRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntegrationOperationsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // --- 1. Payment Controller Tests ---

    @Test
    @DisplayName("POST /api/v1/integrations/payment/initiate initiates transaction successfully")
    void testInitiatePaymentEndpoint() throws Exception {
        PaymentInitiateRequest request = new PaymentInitiateRequest(
                "NOC-TX-8801",
                new BigDecimal("1500.00"),
                "INR",
                "applicant@firesafety.gov",
                "+919876543210",
                "Fire NOC Application Fee"
        );

        mockMvc.perform(post("/api/v1/integrations/payment/initiate")
                        .header("X-Correlation-ID", "CORR-PAY-01")
                        .header("X-Idempotency-Key", "IDEMP-PAY-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.providerName", notNullValue()))
                .andExpect(jsonPath("$.data.paymentGatewayOrderId", notNullValue()))
                .andExpect(jsonPath("$.data.amount", is(1500.00)));
    }

    @Test
    @DisplayName("POST /api/v1/integrations/payment/verify verifies transaction")
    void testVerifyPaymentEndpoint() throws Exception {
        PaymentVerificationRequest request = new PaymentVerificationRequest(
                "order_mock_001",
                "tx_mock_123",
                "sig_hash_mock",
                new BigDecimal("1500.00")
        );

        mockMvc.perform(post("/api/v1/integrations/payment/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.data.verified", is(true)))
                .andExpect(jsonPath("$.data.status", is("SETTLED")));
    }

    @Test
    @DisplayName("GET /api/v1/integrations/payment/status/{id} checks status")
    void testPaymentStatusEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/integrations/payment/status/tx_mock_999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.data.status", is("CAPTURED")));
    }

    // --- 2. SMS Controller Test ---

    @Test
    @DisplayName("POST /api/v1/integrations/sms/send dispatches SMS")
    void testSendSmsEndpoint() throws Exception {
        SmsMessageRequest request = new SmsMessageRequest(
                "+919876543210",
                "Emergency evacuation alert for Sector 7",
                "TPL-EVAC",
                "FIRE-DEPT"
        );

        mockMvc.perform(post("/api/v1/integrations/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.data.deliveryStatus", is("DELIVERED")));
    }

    // --- 3. Email Controller Test ---

    @Test
    @DisplayName("POST /api/v1/integrations/email/send dispatches email")
    void testSendEmailEndpoint() throws Exception {
        EmailMessageRequest request = new EmailMessageRequest(
                "safety.officer@firesphere.gov",
                "Hydrant Inspection Notice",
                "<p>Quarterly hydrant inspection scheduled tomorrow.</p>",
                "Quarterly hydrant inspection scheduled tomorrow.",
                List.of(),
                List.of()
        );

        mockMvc.perform(post("/api/v1/integrations/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.data.status", is("SENT")));
    }

    // --- 4. WhatsApp Controller Test ---

    @Test
    @DisplayName("POST /api/v1/integrations/whatsapp/send dispatches WhatsApp")
    void testSendWhatsAppEndpoint() throws Exception {
        WhatsAppMessageRequest request = new WhatsAppMessageRequest(
                "+919876543210",
                "inspection_reminder",
                "en",
                Map.of("date", "2026-10-10"),
                "Reminder: Inspection on 2026-10-10"
        );

        mockMvc.perform(post("/api/v1/integrations/whatsapp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.data.deliveryStatus", is("DELIVERED")));
    }

    // --- 5. Maps Controller Tests ---

    @Test
    @DisplayName("Maps endpoints: geocode, reverse-geocode, route")
    void testMapsEndpoints() throws Exception {
        GeocodingRequest geoReq = new GeocodingRequest("Brigade Road, Bengaluru", "Bengaluru", "560001", "IND");
        mockMvc.perform(post("/api/v1/integrations/maps/geocode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(geoReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.data.latitude", is(12.9716)));

        ReverseGeocodingRequest revReq = new ReverseGeocodingRequest(12.9716, 77.5946);
        mockMvc.perform(post("/api/v1/integrations/maps/reverse-geocode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.data.city", is("Bengaluru")));

        RouteMatrixRequest routeReq = new RouteMatrixRequest(12.9716, 77.5946, 12.9800, 77.6000, "EMERGENCY");
        mockMvc.perform(post("/api/v1/integrations/maps/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(routeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.data.distanceMeters", is(4500.0)));
    }

    // --- 6. Digital Signature Controller Tests ---

    @Test
    @DisplayName("Digital Signature: sign and verify document")
    void testDigitalSignatureEndpoints() throws Exception {
        byte[] docHash = "TEST-HASH-12345".getBytes(StandardCharsets.UTF_8);
        SignatureRequest signReq = new SignatureRequest(
                "DOC-NOC-0099",
                "FIRE_SAFETY_NOC",
                docHash,
                "OFFICER_01",
                "Fire Marshal",
                "Inspection Approved"
        );

        mockMvc.perform(post("/api/v1/integrations/signature/sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.data.signatureToken", notNullValue()));

        SignatureVerificationRequest verifyReq = new SignatureVerificationRequest(
                "DOC-NOC-0099",
                docHash,
                "SIG-TOKEN-MOCK-SAMPLE"
        );

        mockMvc.perform(post("/api/v1/integrations/signature/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.data.valid", is(true)));
    }

    // --- 7. Government & CAD Controller Tests ---

    @Test
    @DisplayName("Government & CAD endpoints: transmit, status, compliance")
    void testGovernmentCadEndpoints() throws Exception {
        CadTransmitRequest cadReq = new CadTransmitRequest(
                UUID.randomUUID(),
                "INC-REST-CAD-01",
                "STRUCTURE_FIRE",
                "HIGH",
                12.9716,
                77.5946,
                "Main St, City Center",
                "Citizen Caller",
                "+919876543210",
                "Smoke from 2nd floor window"
        );

        mockMvc.perform(post("/api/v1/integrations/cad/transmit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cadReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.data.externalCadIncidentId", notNullValue()));

        mockMvc.perform(get("/api/v1/integrations/cad/status/CAD-112-REST01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.data.externalStatus", is("ON_SCENE")));

        ComplianceLookupRequest compReq = new ComplianceLookupRequest("TAX-BLDG-1122", "BBMP-EAST", "COMMERCIAL");
        mockMvc.perform(post("/api/v1/integrations/cad/compliance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(compReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.data.compliant", is(true)));
    }

    // --- 8. AI Inspection Findings Controller Tests ---

    @Test
    @DisplayName("Operational AI Findings: Ingest, Retrieve, and Review")
    void testAiFindingsEndpoints() throws Exception {
        UUID findingId = UUID.randomUUID();
        AIInspectionResultProviderContract.AIFindingDTO finding = new AIInspectionResultProviderContract.AIFindingDTO(
                findingId,
                UUID.randomUUID(),
                "MISSING_FIRE_EXTINGUISHER",
                0.97,
                "CRITICAL",
                "https://storage.firesphere.ai/evidence/ext_missing.jpg",
                "{}",
                "PENDING_REVIEW",
                null,
                null,
                Instant.now(),
                null
        );

        mockMvc.perform(post("/api/v1/integrations/ai-findings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(finding)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted", is(true)))
                .andExpect(jsonPath("$.findingId", is(findingId.toString())));

        mockMvc.perform(get("/api/v1/integrations/ai-findings/" + findingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findingId", is(findingId.toString())))
                .andExpect(jsonPath("$.status", is("PENDING_REVIEW")));

        AIFindingReviewRequest reviewReq = new AIFindingReviewRequest(
                "APPROVED",
                "Violation validated by field inspector",
                "INSPECTOR_007"
        );

        mockMvc.perform(post("/api/v1/integrations/ai-findings/" + findingId + "/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findingId", is(findingId.toString())))
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.reviewedBy", is("INSPECTOR_007")));
    }
}
