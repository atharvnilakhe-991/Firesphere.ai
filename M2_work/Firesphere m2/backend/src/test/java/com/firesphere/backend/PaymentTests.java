package com.firesphere.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firesphere.backend.noc.entity.NocApplication;
import com.firesphere.backend.noc.entity.NocStatus;
import com.firesphere.backend.noc.repository.NocApplicationRepository;
import com.firesphere.backend.payment.dto.PaymentCallbackRequest;
import com.firesphere.backend.payment.entity.PaymentRecord;
import com.firesphere.backend.payment.entity.PaymentStatus;
import com.firesphere.backend.payment.repository.PaymentRepository;
import com.firesphere.backend.common.security.JwtService;
import com.firesphere.backend.auth.entity.User;
import com.firesphere.backend.auth.repository.UserRepository;
import com.firesphere.backend.common.security.Role;
import com.firesphere.backend.organization.entity.Organization;
import com.firesphere.backend.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"payment.webhook.secret=TEST_SECRET"})
public class PaymentTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NocApplicationRepository nocRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private UUID nocId;
    private UUID orgId;
    private UUID applicantId;
    private String token;

    @BeforeEach
    public void setup() {
        nocRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        Organization org = new Organization();
        org.setName("Org A");
        org = organizationRepository.save(org);
        orgId = org.getUuid();

        User citizen = new User();
        citizen.setEmail("citizena@test.com");
        citizen.setPassword("pass");
        citizen.setStatus("ACTIVE");
        citizen.setRole(Role.CITIZEN.name());
        citizen.setOrganization(org);
        citizen.setOrganizationId(org.getId());
        citizen.setCreatedAt(OffsetDateTime.now());
        citizen.setUpdatedAt(OffsetDateTime.now());
        citizen = userRepository.save(citizen);
        applicantId = citizen.getUuid();
        token = jwtService.generateToken(applicantId);

        NocApplication noc = new NocApplication();
        noc.setOrgId(org.getId());
        noc.setOrganizationId(orgId);
        noc.setApplicantId(applicantId);
        noc.setStatus(NocStatus.SUBMITTED);
        noc.setTitle("Test NOC");
        noc = nocRepository.save(noc);
        nocId = noc.getUuid();
    }

    @Test
    public void testInitiatePaymentTransitionsToPending() throws Exception {
        mockMvc.perform(post("/api/v1/noc/applications/" + nocId + "/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "init-1"))
                .andExpect(status().isOk());

        assertEquals(NocStatus.PAYMENT_PENDING, nocRepository.findByUuid(nocId).get().getStatus());
        
        PaymentRecord record = paymentRepository.findAll().stream()
                .filter(p -> p.getApplicationId().equals(nocId))
                .findFirst().orElseThrow();
        assertEquals(PaymentStatus.PENDING, record.getStatus());
        assertNotNull(record.getReferenceId());
    }

    @Test
    public void testCallbackUnauthenticated() throws Exception {
        PaymentCallbackRequest request = new PaymentCallbackRequest("ref-123", true, "txn-123");

        mockMvc.perform(post("/api/v1/payments/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized()); // No signature
    }

    @Test
    public void testFullPaymentLifecycle() throws Exception {
        // 1. Initiate
        mockMvc.perform(post("/api/v1/noc/applications/" + nocId + "/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "init-lifecycle"))
                .andExpect(status().isOk());

        PaymentRecord record = paymentRepository.findAll().stream()
                .filter(p -> p.getApplicationId().equals(nocId))
                .findFirst().orElseThrow();

        // 2. Callback success
        PaymentCallbackRequest cb = new PaymentCallbackRequest(record.getReferenceId(), true, "txn-123");
        
        String payload = objectMapper.writeValueAsString(cb);
        
        mockMvc.perform(post("/api/v1/payments/callback")
                .header("X-Provider-Signature", generateSignature(payload))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        assertEquals(NocStatus.PAYMENT_COMPLETED, nocRepository.findByUuid(nocId).get().getStatus());
        assertEquals(PaymentStatus.COMPLETED, paymentRepository.findById(record.getId()).get().getStatus());
    }

    @Test
    public void testFailedPaymentLifecycle() throws Exception {
        // 1. Initiate
        mockMvc.perform(post("/api/v1/noc/applications/" + nocId + "/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "init-fail"))
                .andExpect(status().isOk());

        PaymentRecord record = paymentRepository.findAll().stream()
                .filter(p -> p.getApplicationId().equals(nocId))
                .findFirst().orElseThrow();

        // 2. Callback failed
        PaymentCallbackRequest cb = new PaymentCallbackRequest(record.getReferenceId(), false, "txn-123");
        
        String payload = objectMapper.writeValueAsString(cb);
        mockMvc.perform(post("/api/v1/payments/callback")
                .header("X-Provider-Signature", generateSignature(payload))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        // Stays pending because payment failed, but record is marked failed
        assertEquals(NocStatus.PAYMENT_PENDING, nocRepository.findByUuid(nocId).get().getStatus());
        assertEquals(PaymentStatus.FAILED, paymentRepository.findById(record.getId()).get().getStatus());
    }

    @Test
    public void testCallbackWithInvalidSignature() throws Exception {
        PaymentCallbackRequest cb = new PaymentCallbackRequest("ref-123", true, "txn-123");
        String payload = objectMapper.writeValueAsString(cb);
        
        mockMvc.perform(post("/api/v1/payments/callback")
                .header("X-Provider-Signature", "invalid_signature")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testCallbackWithModifiedPayload() throws Exception {
        PaymentCallbackRequest cb = new PaymentCallbackRequest("ref-123", true, "txn-123");
        String originalPayload = objectMapper.writeValueAsString(cb);
        String signature = generateSignature(originalPayload);
        
        PaymentCallbackRequest modified = new PaymentCallbackRequest("ref-123", false, "txn-123");
        String modifiedPayload = objectMapper.writeValueAsString(modified);

        mockMvc.perform(post("/api/v1/payments/callback")
                .header("X-Provider-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(modifiedPayload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testDirectSubmitToPaymentCompletedFails() throws Exception {
        // Test C: Direct SUBMITTED -> PAYMENT_COMPLETED fails
        mockMvc.perform(post("/api/v1/noc/applications/" + nocId + "/transitions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\": \"COMPLETE_PAYMENT\"}"))
                .andExpect(status().isBadRequest()); // SUBMITTED -> COMPLETE_PAYMENT is invalid state transition
    }

    @Test
    public void testClientCannotCompletePayment() throws Exception {
        // Test D: Unauthenticated client attempting COMPLETE_PAYMENT fails. Or even authenticated client fails because COMPLETE_PAYMENT requires currentUser == null.
        // First transition to PAYMENT_PENDING
        mockMvc.perform(post("/api/v1/noc/applications/" + nocId + "/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "init-c1"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/noc/applications/" + nocId + "/transitions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\": \"COMPLETE_PAYMENT\"}"))
                .andExpect(status().isForbidden()); // COMPLETE_PAYMENT is system-only
    }

    @Test
    public void testCallbackReplayProtection() throws Exception {
        // Test E: Already completed payment callback cannot complete the payment again (replay).
        mockMvc.perform(post("/api/v1/noc/applications/" + nocId + "/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "init-replay"))
                .andExpect(status().isOk());

        PaymentRecord record = paymentRepository.findAll().stream()
                .filter(p -> p.getApplicationId().equals(nocId))
                .findFirst().orElseThrow();

        PaymentCallbackRequest cb = new PaymentCallbackRequest(record.getReferenceId(), true, "txn-replay");
        String payload = objectMapper.writeValueAsString(cb);
        String sig = generateSignature(payload);

        // First callback succeeds
        mockMvc.perform(post("/api/v1/payments/callback")
                .header("X-Provider-Signature", sig)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        assertEquals(PaymentStatus.COMPLETED, paymentRepository.findById(record.getId()).get().getStatus());

        // Replay same callback -> should just return OK without error, but business logic shouldn't run again.
        mockMvc.perform(post("/api/v1/payments/callback")
                .header("X-Provider-Signature", sig)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    public void testMissingSignature() throws Exception {
        PaymentCallbackRequest cb = new PaymentCallbackRequest("ref-123", true, "txn-123");
        mockMvc.perform(post("/api/v1/payments/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cb)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testMissingSecretBehavior() throws Exception {
        // Mock a scenario where webhook secret is not configured
        com.firesphere.backend.payment.service.PaymentCallbackAuthenticator badAuth = 
            new com.firesphere.backend.payment.service.PaymentCallbackAuthenticator("");
        
        assertFalse(badAuth.authenticate("payload", "signature"), "Missing secret should fail closed and return false");
        
        com.firesphere.backend.payment.service.PaymentCallbackAuthenticator nullAuth = 
            new com.firesphere.backend.payment.service.PaymentCallbackAuthenticator(null);
        
        assertFalse(nullAuth.authenticate("payload", "signature"), "Null secret should fail closed and return false");
    }

    private String generateSignature(String payload) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec("TEST_SECRET".getBytes("UTF-8"), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        return Base64.getEncoder().encodeToString(sha256_HMAC.doFinal(payload.getBytes("UTF-8")));
    }
}
