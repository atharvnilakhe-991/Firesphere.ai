package com.firesphere.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firesphere.backend.ai.client.AiAnalysisClient;
import com.firesphere.backend.ai.dto.AiAnalysisRequest;
import com.firesphere.backend.ai.dto.ExternalAiRequest;
import com.firesphere.backend.ai.dto.ExternalAiResponse;
import com.firesphere.backend.ai.repository.AiAnalysisRunRepository;
import com.firesphere.backend.common.security.JwtService;
import com.firesphere.backend.common.security.Role;
import com.firesphere.backend.document.entity.DocumentStatus;
import com.firesphere.backend.document.entity.NocDocument;
import com.firesphere.backend.document.repository.NocDocumentRepository;
import com.firesphere.backend.noc.entity.NocApplication;
import com.firesphere.backend.noc.entity.NocStatus;
import com.firesphere.backend.noc.repository.NocApplicationRepository;
import com.firesphere.backend.auth.entity.User;
import com.firesphere.backend.auth.repository.UserRepository;
import com.firesphere.backend.organization.entity.Organization;
import com.firesphere.backend.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiAnalysisTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private NocApplicationRepository nocRepository;

    @Autowired
    private NocDocumentRepository documentRepository;
    
    @Autowired
    private AiAnalysisRunRepository runRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiAnalysisClient aiClient;

    private Organization orgA;
    private Organization orgB;
    
    private User citizenA;
    private User officerA;
    private User citizenB;
    
    private NocApplication nocA;
    private NocApplication nocB;
    
    private NocDocument docA;

    @BeforeEach
    void setup() {
        runRepository.deleteAll();
        documentRepository.deleteAll();
        nocRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        orgA = new Organization();
        orgA.setName("Org A");
        orgA = organizationRepository.save(orgA);

        orgB = new Organization();
        orgB.setName("Org B");
        orgB = organizationRepository.save(orgB);

        citizenA = createUser("citizena@example.com", Role.CITIZEN, orgA.getId());
        officerA = createUser("officera@example.com", Role.OFFICER, orgA.getId());
        citizenB = createUser("citizenb@example.com", Role.CITIZEN, orgB.getId());

        nocA = new NocApplication();
        nocA.setOrganizationId(orgA.getId());
        nocA.setApplicantId(citizenA.getId());
        nocA.setTitle("NOC A");
        nocA.setBuildingName("RES");
        nocA.setAddress("Add");
        nocA.setStatus(NocStatus.SUBMITTED);
        nocA = nocRepository.save(nocA);

        nocB = new NocApplication();
        nocB.setOrganizationId(orgB.getId());
        nocB.setApplicantId(citizenB.getId());
        nocB.setTitle("NOC B");
        nocB.setBuildingName("RES");
        nocB.setAddress("Add");
        nocB.setStatus(NocStatus.SUBMITTED);
        nocB = nocRepository.save(nocB);

        docA = new NocDocument();
        docA.setApplicationId(nocA.getId());
        docA.setOrganizationId(orgA.getId());
        docA.setDocumentType("PLAN");
        docA.setOriginalFileName("plan.pdf");
        docA.setStorageReference("s3://bucket/plan.pdf");
        docA.setContentType("application/pdf");
        docA.setFileSize(1000L);
        docA.setStatus(DocumentStatus.UPLOADED);
        docA.setUploadedBy(citizenA.getId());
        docA = documentRepository.save(docA);
    }

    private User createUser(String email, Role role, UUID orgId) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("hash");
        user.setStatus("ACTIVE");
        user.setRole(role.name());
        user.setOrganizationId(orgId);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return userRepository.save(user);
    }

    @Test
    void canRequestAiAnalysis() throws Exception {
        String token = jwtService.generateToken(officerA.getId());
        AiAnalysisRequest req = new AiAnalysisRequest(docA.getId(), "FIRE_SAFETY_PLAN");

        when(aiClient.analyze(any(ExternalAiRequest.class))).thenReturn(
                new ExternalAiResponse("SUCCESS", "yolo-v8", "1.0", OffsetDateTime.now(),
                        List.of(new ExternalAiResponse.Finding("Extinguisher Detected", 0.95)), null)
        );

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getId() + "/ai-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
                
        // Ensure NOC status is completely untouched
        NocApplication nocAfterAi = nocRepository.findById(nocA.getId()).orElseThrow();
        assert nocAfterAi.getStatus() == NocStatus.SUBMITTED;
    }

    @Test
    void cannotAnalyzeCrossTenantDocument() throws Exception {
        String token = jwtService.generateToken(citizenB.getId());
        AiAnalysisRequest req = new AiAnalysisRequest(docA.getId(), "FIRE_SAFETY_PLAN");

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getId() + "/ai-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()); // The noc application itself will return 404 for tenant B
    }

    @Test
    void cannotAnalyzeOtherCitizenApplication() throws Exception {
        User citizenA2 = createUser("citizena2@example.com", Role.CITIZEN, orgA.getId());
        String token = jwtService.generateToken(citizenA2.getId());
        AiAnalysisRequest req = new AiAnalysisRequest(docA.getId(), "FIRE_SAFETY_PLAN");

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getId() + "/ai-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()); // Same tenant, but not owner
    }

    @Test
    void cannotAnalyzeRejectedDocument() throws Exception {
        docA.setStatus(DocumentStatus.REJECTED);
        documentRepository.save(docA);

        String token = jwtService.generateToken(officerA.getId());
        AiAnalysisRequest req = new AiAnalysisRequest(docA.getId(), "FIRE_SAFETY_PLAN");

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getId() + "/ai-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aiServiceInvalidConfidenceHandled() throws Exception {
        String token = jwtService.generateToken(officerA.getId());
        AiAnalysisRequest req = new AiAnalysisRequest(docA.getId(), "FIRE_SAFETY_PLAN");

        when(aiClient.analyze(any(ExternalAiRequest.class))).thenReturn(
                new ExternalAiResponse("SUCCESS", "yolo-v8", "1.0", OffsetDateTime.now(),
                        List.of(new ExternalAiResponse.Finding("Extinguisher Detected", 1.5)), null)
        );

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getId() + "/ai-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
                
        // verify it's FAILED
        assert runRepository.findAllByApplicationIdAndOrganizationId(nocA.getId(), orgA.getId()).get(0).getStatus().name().equals("FAILED");
    }

    @Test
    void aiServiceErrorResponseHandled() throws Exception {
        String token = jwtService.generateToken(officerA.getId());
        AiAnalysisRequest req = new AiAnalysisRequest(docA.getId(), "FIRE_SAFETY_PLAN");

        // Simulate 400/500 which HttpAiAnalysisClient maps to FAILED status
        when(aiClient.analyze(any(ExternalAiRequest.class))).thenReturn(
                new ExternalAiResponse("FAILED", null, null, null, null, "AI Service returned error: 400 BAD_REQUEST")
        );

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getId() + "/ai-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()); // We still return 200 to the client, but the run is marked FAILED

        assert runRepository.findAllByApplicationIdAndOrganizationId(nocA.getId(), orgA.getId()).get(0).getStatus().name().equals("FAILED");
    }

    @Test
    void aiServiceTimeoutHandled() throws Exception {
        String token = jwtService.generateToken(officerA.getId());
        AiAnalysisRequest req = new AiAnalysisRequest(docA.getId(), "FIRE_SAFETY_PLAN");

        // Simulate timeout which throws RestClientException and mapped to FAILED
        when(aiClient.analyze(any(ExternalAiRequest.class))).thenReturn(
                new ExternalAiResponse("FAILED", null, null, null, null, "Error communicating with AI service: Read timed out")
        );

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getId() + "/ai-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assert runRepository.findAllByApplicationIdAndOrganizationId(nocA.getId(), orgA.getId()).get(0).getStatus().name().equals("FAILED");
    }
}
