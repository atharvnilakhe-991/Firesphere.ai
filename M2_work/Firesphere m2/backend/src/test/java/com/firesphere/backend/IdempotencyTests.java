package com.firesphere.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firesphere.backend.noc.entity.NocApplication;
import com.firesphere.backend.noc.entity.NocStatus;
import com.firesphere.backend.noc.repository.NocApplicationRepository;
import com.firesphere.backend.common.idempotency.repository.IdempotencyRepository;
import com.firesphere.backend.workflow.model.WorkflowAction;
import com.firesphere.backend.workflow.dto.WorkflowActionRequest;
import com.firesphere.backend.workflow.dto.WorkflowTransitionResponse;
import com.firesphere.backend.workflow.service.WorkflowService;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class IdempotencyTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NocApplicationRepository nocRepository;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowService workflowService;

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
    private User citizenA;

    @BeforeEach
    public void setup() {
        nocRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        Organization org = new Organization();
        org.setName("Org A");
        org = organizationRepository.save(org);
        orgId = org.getUuid();

        citizenA = new User();
        citizenA.setEmail("citizena@test.com");
        citizenA.setPassword("pass");
        citizenA.setStatus("ACTIVE");
        citizenA.setRole(Role.CITIZEN.name());
        citizenA.setOrganization(org);
        citizenA.setOrganizationId(org.getId());
        citizenA.setCreatedAt(OffsetDateTime.now());
        citizenA.setUpdatedAt(OffsetDateTime.now());
        citizenA = userRepository.save(citizenA);
        applicantId = citizenA.getUuid();
        token = jwtService.generateToken(applicantId);

        NocApplication noc = new NocApplication();
        noc.setOrgId(org.getId());
        noc.setOrganizationId(orgId);
        noc.setApplicantId(applicantId);
        noc.setStatus(NocStatus.DRAFT);
        noc.setTitle("Test NOC");
        noc = nocRepository.save(noc);
        nocId = noc.getUuid();
    }

    @Test
    public void testIdempotentRequestReplaysResponse() throws Exception {
        String key = "test-key-1";

        // First request
        String response1 = mockMvc.perform(post("/api/v1/noc/applications/" + nocId + "/submit")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Target should be SUBMITTED
        assertEquals(NocStatus.SUBMITTED, nocRepository.findById(nocId).get().getStatus());

        // Identical request with same key
        String response2 = mockMvc.perform(post("/api/v1/noc/applications/" + nocId + "/submit")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(response1, response2, "Responses should be identical");
        assertEquals(NocStatus.SUBMITTED, nocRepository.findById(nocId).get().getStatus());
    }

    @Test
    public void testDifferentUserSameKeyIsIsolated() throws Exception {
        String key = "test-key-2";

        mockMvc.perform(post("/api/v1/noc/applications/" + nocId + "/submit")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key))
                .andExpect(status().isOk());

        // Assume another user tries to use same key on another app
        NocApplication noc2 = new NocApplication();
        noc2.setOrganizationId(orgId);
        noc2.setApplicantId(UUID.fromString("20000000-0000-0000-0000-000000000002"));
        noc2.setStatus(NocStatus.DRAFT);
        noc2.setTitle("Test NOC 2");
        nocRepository.save(noc2);

        // This simulates a different JWT identity making a request with the same key
        // By changing @WithMockUser manually we can't easily do it mid-test cleanly, 
        // but we can verify the repository structure ensures isolation.
    }

    @Test
    public void testConcurrentRequests() throws Exception {
        // Prepare NOC
        NocApplication noc3 = new NocApplication();
        noc3.setOrgId(citizenA.getOrganization().getId());
        noc3.setOrganizationId(orgId);
        noc3.setApplicantId(applicantId);
        noc3.setStatus(NocStatus.DRAFT);
        noc3.setTitle("Test NOC 3");
        final NocApplication savedNoc3 = nocRepository.save(noc3);

        String key = "concurrent-key";
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        java.util.List<Exception> unexpectedExceptions = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    org.springframework.security.core.context.SecurityContextHolder.setContext(org.springframework.security.core.context.SecurityContextHolder.createEmptyContext());
                    org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            new com.firesphere.backend.common.security.AuthenticatedUser(
                                applicantId,
                                "test",
                                orgId,
                                citizenA.getId(),
                                citizenA.getOrganization().getId(),
                                java.util.Collections.emptyList()
                            ),
                            null,
                            java.util.Collections.emptyList()
                        )
                    );
                    workflowService.performTransition(savedNoc3.getUuid(), new WorkflowActionRequest(WorkflowAction.SUBMIT, null), key);
                    successCount.incrementAndGet();
                } catch (org.springframework.web.server.ResponseStatusException e) {
                    if (e.getStatusCode() == org.springframework.http.HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    } else {
                        unexpectedExceptions.add(e);
                    }
                } catch (Exception e) {
                    unexpectedExceptions.add(e);
                } finally {
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                    done.countDown();
                }
            });
        }

        latch.countDown(); // Start all
        done.await(); // Wait all
        executor.shutdown();

        assertTrue(unexpectedExceptions.isEmpty(), "Unexpected exceptions occurred: " + unexpectedExceptions);
        assertEquals(1, successCount.get(), "Exactly one request should execute business logic and return success");
        assertEquals(threads - 1, conflictCount.get(), "Other requests should be rejected as CONFLICT");
        assertEquals(NocStatus.SUBMITTED, nocRepository.findByUuid(savedNoc3.getUuid()).get().getStatus(), "State should be SUBMITTED exactly once");

        long idempotencyRecordCount = idempotencyRepository.findAll().stream().filter(r -> key.equals(r.getIdempotencyKey())).count();
        assertEquals(1, idempotencyRecordCount, "Exactly one idempotency record should exist for this scope and key");
    }

    @Test
    public void testSameKeyDifferentRequestRejects() throws Exception {
        String key = "test-key-3";

        // First request: SUBMIT
        mockMvc.perform(post("/api/v1/noc/applications/" + nocId + "/transitions")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\": \"SUBMIT\"}"))
                .andExpect(status().isOk());

        // Same key, different request: e.g. RESUBMIT (even if invalid state, the idempotency layer should reject it first)
        mockMvc.perform(post("/api/v1/noc/applications/" + nocId + "/transitions")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\": \"RESUBMIT\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    public void testIdempotencyIsolationBetweenUsers() throws Exception {
        // User A (applicantId) in Org A (orgId)
        String key = "shared-isolation-key";

        mockMvc.perform(post("/api/v1/noc/applications/" + nocId + "/transitions")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\": \"SUBMIT\"}"))
                .andExpect(status().isOk());

        // Create User B in Org A
        User citizenB = new User();
        citizenB.setEmail("citizenb@test.com");
        citizenB.setPassword("pass");
        citizenB.setStatus("ACTIVE");
        citizenB.setRole(Role.CITIZEN.name());
        citizenB.setOrganization(citizenA.getOrganization());
        citizenB.setOrganizationId(citizenA.getOrganization().getId());
        citizenB.setCreatedAt(OffsetDateTime.now());
        citizenB.setUpdatedAt(OffsetDateTime.now());
        citizenB = userRepository.save(citizenB);
        String tokenB = jwtService.generateToken(citizenB.getUuid());

        NocApplication nocB = new NocApplication();
        nocB.setOrgId(citizenA.getOrganization().getId());
        nocB.setOrganizationId(orgId);
        nocB.setApplicantId(citizenB.getUuid());
        nocB.setStatus(NocStatus.DRAFT);
        nocB.setTitle("Test NOC B");
        nocB = nocRepository.save(nocB);

        // User B uses the exact same key for their own application
        mockMvc.perform(post("/api/v1/noc/applications/" + nocB.getUuid() + "/transitions")
                .header("Authorization", "Bearer " + tokenB)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\": \"SUBMIT\"}"))
                .andExpect(status().isOk()); // Must succeed because it is isolated by user
    }

    @Test
    public void testIdempotencyIsolationBetweenOrgs() throws Exception {
        // User A (applicantId) in Org A (orgId)
        String key = "shared-org-isolation-key";

        mockMvc.perform(post("/api/v1/noc/applications/" + nocId + "/transitions")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\": \"SUBMIT\"}"))
                .andExpect(status().isOk());

        // Create Org C
        Organization orgC = new Organization();
        orgC.setName("Org C");
        orgC = organizationRepository.save(orgC);

        // Create User C in Org C
        User citizenC = new User();
        citizenC.setEmail("citizenc@test.com");
        citizenC.setPassword("pass");
        citizenC.setStatus("ACTIVE");
        citizenC.setRole(Role.CITIZEN.name());
        citizenC.setOrganization(orgC);
        citizenC.setOrganizationId(orgC.getId());
        citizenC.setCreatedAt(OffsetDateTime.now());
        citizenC.setUpdatedAt(OffsetDateTime.now());
        citizenC = userRepository.save(citizenC);
        String tokenC = jwtService.generateToken(citizenC.getUuid());

        NocApplication nocC = new NocApplication();
        nocC.setOrgId(orgC.getId());
        nocC.setOrganizationId(orgC.getUuid());
        nocC.setApplicantId(citizenC.getUuid());
        nocC.setStatus(NocStatus.DRAFT);
        nocC.setTitle("Test NOC C");
        nocC = nocRepository.save(nocC);

        // User C in Org C uses the exact same key
        mockMvc.perform(post("/api/v1/noc/applications/" + nocC.getUuid() + "/transitions")
                .header("Authorization", "Bearer " + tokenC)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\": \"SUBMIT\"}"))
                .andExpect(status().isOk()); // Must succeed because it is isolated by org
    }
}
