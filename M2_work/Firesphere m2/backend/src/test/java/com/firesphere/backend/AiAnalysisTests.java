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
import com.firesphere.backend.inspection.entity.NocInspection;
import com.firesphere.backend.inspection.repository.NocInspectionRepository;
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

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    private NocInspectionRepository inspectionRepository;
    
    @Autowired
    private AiAnalysisRunRepository runRepository;

    @Autowired
    private com.firesphere.backend.ai.repository.AiOfficerReviewRepository officerReviewRepository;

    @Autowired
    private com.firesphere.backend.ai.repository.AiFindingAuditRepository auditRepository;

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
    private NocInspection insA;
    private UUID testEvidenceId;

    @BeforeEach
    void setup() {
        auditRepository.deleteAll();
        officerReviewRepository.deleteAll();
        runRepository.deleteAll();
        inspectionRepository.deleteAll();
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

        citizenA = createUser("citizena@example.com", Role.CITIZEN, orgA);
        officerA = createUser("officera@example.com", Role.OFFICER, orgA);
        citizenB = createUser("citizenb@example.com", Role.CITIZEN, orgB);

        nocA = new NocApplication();
        nocA.setOrgId(orgA.getId());
        nocA.setOrganizationId(orgA.getUuid());
        nocA.setApplicantId(citizenA.getUuid());
        nocA.setTitle("NOC A");
        nocA.setBuildingName("RES");
        nocA.setAddress("Add");
        nocA.setStatus(NocStatus.SUBMITTED);
        nocA = nocRepository.save(nocA);

        nocB = new NocApplication();
        nocB.setOrgId(orgB.getId());
        nocB.setOrganizationId(orgB.getUuid());
        nocB.setApplicantId(citizenB.getUuid());
        nocB.setTitle("NOC B");
        nocB.setBuildingName("RES");
        nocB.setAddress("Add");
        nocB.setStatus(NocStatus.SUBMITTED);
        nocB = nocRepository.save(nocB);

        docA = new NocDocument();
        docA.setApplicationId(nocA.getUuid());
        docA.setOrganizationId(orgA.getUuid());
        docA.setDocumentType("PLAN");
        docA.setOriginalFileName("plan.pdf");
        docA.setStorageReference("s3://bucket/plan.pdf");
        docA.setContentType("application/pdf");
        docA.setFileSize(1000L);
        docA.setStatus(DocumentStatus.UPLOADED);
        docA.setUploadedBy(citizenA.getUuid());
        docA = documentRepository.save(docA);

        insA = new NocInspection();
        insA.setApplicationId(nocA.getUuid());
        insA.setOrganizationId(orgA.getUuid());
        insA.setOrgId(orgA.getId());
        insA.setNocApplicationId(nocA.getId());
        insA.setInspectionType("INITIAL");
        insA.setScheduledAt(OffsetDateTime.now());
        insA = inspectionRepository.save(insA);

        testEvidenceId = UUID.randomUUID();
    }

    private User createUser(String email, Role role, Organization org) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("hash");
        user.setStatus("ACTIVE");
        user.setRole(role.name());
        user.setOrganization(org);
        user.setOrganizationId(org.getId());
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return userRepository.save(user);
    }

    private ExternalAiResponse.ExternalAiFinding createTestFinding(String type, double conf) {
        return new ExternalAiResponse.ExternalAiFinding(
                UUID.randomUUID(),
                testEvidenceId,
                type,
                conf,
                "HIGH",
                "MEDIUM",
                "Standard detection",
                50,
                "MEDIUM",
                type,
                List.of(),
                new ExternalAiResponse.BoundingBoxDto(0.1, 0.2, 0.3, 0.4),
                "GOOD",
                "REQUIRES_OFFICER_VERIFICATION",
                "yolo-v8",
                "1.0",
                false,
                "NOT_APPROVED",
                "NOT_CALIBRATED",
                "VALID",
                insA.getUuid(),
                null
        );
    }

    @Test
    void canRequestAiAnalysis() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());
        AiAnalysisRequest req = new AiAnalysisRequest(docA.getId(), "FIRE_SAFETY_PLAN", insA.getUuid(), List.of(testEvidenceId));

        when(aiClient.analyze(any(ExternalAiRequest.class))).thenReturn(
                new ExternalAiResponse(
                    UUID.randomUUID(),
                    "COMPLETED",
                    List.of(createTestFinding("Extinguisher Detected", 0.95)),
                    null,
                    "yolo-v8",
                    "1.0",
                    25.0,
                    orgA.getId().toString(),
                    "NOT_APPROVED",
                    "NOT_CALIBRATED",
                    null,
                    null
            )
        );

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/ai-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
                
        // Ensure NOC status is completely untouched
        NocApplication nocAfterAi = nocRepository.findByUuid(nocA.getUuid()).orElseThrow();
        assert nocAfterAi.getStatus() == NocStatus.SUBMITTED;
    }

    @Test
    void cannotAnalyzeCrossTenantDocument() throws Exception {
        String token = jwtService.generateToken(citizenB.getUuid());
        AiAnalysisRequest req = new AiAnalysisRequest(docA.getId(), "FIRE_SAFETY_PLAN", insA.getUuid(), List.of(testEvidenceId));

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/ai-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotAnalyzeOtherCitizenApplication() throws Exception {
        User citizenA2 = createUser("citizena2@example.com", Role.CITIZEN, orgA);
        String token = jwtService.generateToken(citizenA2.getUuid());
        AiAnalysisRequest req = new AiAnalysisRequest(docA.getId(), "FIRE_SAFETY_PLAN", insA.getUuid(), List.of(testEvidenceId));

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/ai-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()); // Same tenant, but not owner
    }

    @Test
    void cannotAnalyzeRejectedDocument() throws Exception {
        docA.setStatus(DocumentStatus.REJECTED);
        documentRepository.save(docA);

        String token = jwtService.generateToken(officerA.getUuid());
        AiAnalysisRequest req = new AiAnalysisRequest(docA.getId(), "FIRE_SAFETY_PLAN", insA.getUuid(), List.of(testEvidenceId));

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/ai-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aiServiceInvalidConfidenceHandled() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());
        AiAnalysisRequest req = new AiAnalysisRequest(docA.getId(), "FIRE_SAFETY_PLAN", insA.getUuid(), List.of(testEvidenceId));

        when(aiClient.analyze(any(ExternalAiRequest.class))).thenReturn(
                new ExternalAiResponse(
                        UUID.randomUUID(),
                        "COMPLETED",
                        List.of(createTestFinding("Extinguisher Detected", 1.5)),
                        null,
                        "yolo-v8",
                        "1.0",
                        25.0,
                        orgA.getId().toString(),
                        "NOT_APPROVED",
                        "NOT_CALIBRATED",
                        null,
                        null
                )
        );

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/ai-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
                
        // verify it's FAILED
        assert runRepository.findAllByApplicationIdAndOrganizationId(nocA.getId(), orgA.getId()).get(0).getStatus().name().equals("FAILED");
    }

    @Test
    void aiServiceErrorResponseHandled() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());
        AiAnalysisRequest req = new AiAnalysisRequest(docA.getId(), "FIRE_SAFETY_PLAN", insA.getUuid(), List.of(testEvidenceId));

        // Simulate 400/500 which HttpAiAnalysisClient maps to FAILED status
        when(aiClient.analyze(any(ExternalAiRequest.class))).thenReturn(
                ExternalAiResponse.failed("AI_SERVICE_ERROR", "AI Service returned error: 400 BAD_REQUEST")
        );

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/ai-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()); // We still return 200 to the client, but the run is marked FAILED

        assert runRepository.findAllByApplicationIdAndOrganizationId(nocA.getId(), orgA.getId()).get(0).getStatus().name().equals("FAILED");
    }

    @Test
    void aiServiceTimeoutHandled() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());
        AiAnalysisRequest req = new AiAnalysisRequest(docA.getId(), "FIRE_SAFETY_PLAN", insA.getUuid(), List.of(testEvidenceId));

        // Simulate timeout which throws RestClientException and mapped to FAILED
        when(aiClient.analyze(any(ExternalAiRequest.class))).thenReturn(
                ExternalAiResponse.failed("AI_TIMEOUT", "Error communicating with AI service: Read timed out")
        );

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/ai-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assert runRepository.findAllByApplicationIdAndOrganizationId(nocA.getId(), orgA.getId()).get(0).getStatus().name().equals("FAILED");
    }

    @Test
    void officerCanReviewFindingSuccessfullyAndStatutoryStateUnchanged() throws Exception {
        String officerToken = jwtService.generateToken(officerA.getUuid());
        AiAnalysisRequest req = new AiAnalysisRequest(docA.getId(), "FIRE_SAFETY_PLAN", insA.getUuid(), List.of(testEvidenceId));

        when(aiClient.analyze(any(ExternalAiRequest.class))).thenReturn(
                new ExternalAiResponse(
                        UUID.randomUUID(),
                        "COMPLETED",
                        List.of(createTestFinding("Missing Extinguisher", 0.92)),
                        null,
                        "yolo-v8",
                        "1.0",
                        20.0,
                        orgA.getId().toString(),
                        "NOT_APPROVED",
                        "NOT_CALIBRATED",
                        null,
                        null
                )
        );

        // 1. Request AI Analysis
        String runResponseStr = mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/ai-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var runNode = objectMapper.readTree(runResponseStr);
        String runIdStr = runNode.get("runId").asText();
        String findingIdStr = runNode.get("findings").get(0).get("id").asText();

        // 2. Submit Officer Review with remarks and verification
        String reviewPayload = """
                {
                    "reviewStatus": "CONFIRMED",
                    "officerNotes": "Physical inspection confirmed extinguisher is missing from lobby",
                    "hazardVerified": true
                }
                """;

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/ai-analysis/" + runIdStr + "/findings/" + findingIdStr + "/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewPayload)
                .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus", is("CONFIRMED")))
                .andExpect(jsonPath("$.hazardVerified", is(true)))
                .andExpect(jsonPath("$.officerNotes", is("Physical inspection confirmed extinguisher is missing from lobby")));

        // 3. Verify retrieved analysis reflects persisted review and governance indicators
        mockMvc.perform(get("/api/v1/noc/applications/" + nocA.getUuid() + "/ai-analysis/" + runIdStr)
                .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cvStatus", is("NOT_APPROVED_GATED")))
                .andExpect(jsonPath("$.vlmStatus", is("UNAVAILABLE")))
                .andExpect(jsonPath("$.modelApprovalStatus", is("NOT_APPROVED")))
                .andExpect(jsonPath("$.calibrationStatus", is("NOT_CALIBRATED")))
                .andExpect(jsonPath("$.findings[0].reviewStatus", is("CONFIRMED")))
                .andExpect(jsonPath("$.findings[0].officerNotes", is("Physical inspection confirmed extinguisher is missing from lobby")))
                .andExpect(jsonPath("$.findings[0].hazardVerified", is(true)))
                .andExpect(jsonPath("$.findings[0].provenance", is("CV_OBJECT_DETECTION")));

        // 4. Ensure statutory NOC status remains untouched (AI review cannot approve/reject NOC)
        NocApplication nocAfterReview = nocRepository.findByUuid(nocA.getUuid()).orElseThrow();
        assert nocAfterReview.getStatus() == NocStatus.SUBMITTED;
    }

    @Test
    void citizenCannotReviewAiFinding() throws Exception {
        String officerToken = jwtService.generateToken(officerA.getUuid());
        String citizenToken = jwtService.generateToken(citizenA.getUuid());
        AiAnalysisRequest req = new AiAnalysisRequest(docA.getId(), "FIRE_SAFETY_PLAN", insA.getUuid(), List.of(testEvidenceId));

        when(aiClient.analyze(any(ExternalAiRequest.class))).thenReturn(
                new ExternalAiResponse(
                        UUID.randomUUID(),
                        "COMPLETED",
                        List.of(createTestFinding("Missing Extinguisher", 0.90)),
                        null,
                        "yolo-v8",
                        "1.0",
                        20.0,
                        orgA.getId().toString(),
                        "NOT_APPROVED",
                        "NOT_CALIBRATED",
                        null,
                        null
                )
        );

        String runResponseStr = mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/ai-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var runNode = objectMapper.readTree(runResponseStr);
        String runIdStr = runNode.get("runId").asText();
        String findingIdStr = runNode.get("findings").get(0).get("id").asText();

        // Citizen attempts to verify finding -> 403 Forbidden
        String reviewPayload = "{\"reviewStatus\":\"CONFIRMED\",\"officerNotes\":\"Citizen attempt\"}";
        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/ai-analysis/" + runIdStr + "/findings/" + findingIdStr + "/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewPayload)
                .header("Authorization", "Bearer " + citizenToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void officerCannotReviewFindingCrossTenant() throws Exception {
        User officerB = createUser("officerb@example.com", Role.OFFICER, orgB);
        String officerAToken = jwtService.generateToken(officerA.getUuid());
        String officerBToken = jwtService.generateToken(officerB.getUuid());

        AiAnalysisRequest req = new AiAnalysisRequest(docA.getId(), "FIRE_SAFETY_PLAN", insA.getUuid(), List.of(testEvidenceId));

        when(aiClient.analyze(any(ExternalAiRequest.class))).thenReturn(
                new ExternalAiResponse(
                        UUID.randomUUID(),
                        "COMPLETED",
                        List.of(createTestFinding("Missing Extinguisher", 0.90)),
                        null,
                        "yolo-v8",
                        "1.0",
                        20.0,
                        orgA.getId().toString(),
                        "NOT_APPROVED",
                        "NOT_CALIBRATED",
                        null,
                        null
                )
        );

        String runResponseStr = mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/ai-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + officerAToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var runNode = objectMapper.readTree(runResponseStr);
        String runIdStr = runNode.get("runId").asText();
        String findingIdStr = runNode.get("findings").get(0).get("id").asText();

        // Officer B of Org B tries to review finding of Org A -> 403 Forbidden
        String reviewPayload = "{\"reviewStatus\":\"CONFIRMED\",\"officerNotes\":\"Cross tenant\"}";
        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/ai-analysis/" + runIdStr + "/findings/" + findingIdStr + "/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewPayload)
                .header("Authorization", "Bearer " + officerBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewRejectedForInvalidStatus() throws Exception {
        String officerToken = jwtService.generateToken(officerA.getUuid());
        AiAnalysisRequest req = new AiAnalysisRequest(docA.getId(), "FIRE_SAFETY_PLAN", insA.getUuid(), List.of(testEvidenceId));

        when(aiClient.analyze(any(ExternalAiRequest.class))).thenReturn(
                new ExternalAiResponse(
                        UUID.randomUUID(),
                        "COMPLETED",
                        List.of(createTestFinding("Missing Extinguisher", 0.90)),
                        null,
                        "yolo-v8",
                        "1.0",
                        20.0,
                        orgA.getId().toString(),
                        "NOT_APPROVED",
                        "NOT_CALIBRATED",
                        null,
                        null
                )
        );

        String runResponseStr = mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/ai-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var runNode = objectMapper.readTree(runResponseStr);
        String runIdStr = runNode.get("runId").asText();
        String findingIdStr = runNode.get("findings").get(0).get("id").asText();

        // Submitting an unsupported status string -> 400 Bad Request
        String invalidPayload = "{\"reviewStatus\":\"UNSUPPORTED_STATUS\"}";
        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/ai-analysis/" + runIdStr + "/findings/" + findingIdStr + "/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload)
                .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotReviewFindingUnderMismatchedRun() throws Exception {
        String officerToken = jwtService.generateToken(officerA.getUuid());
        AiAnalysisRequest req = new AiAnalysisRequest(docA.getId(), "FIRE_SAFETY_PLAN", insA.getUuid(), List.of(testEvidenceId));

        when(aiClient.analyze(any(ExternalAiRequest.class))).thenReturn(
                new ExternalAiResponse(
                        UUID.randomUUID(),
                        "COMPLETED",
                        List.of(createTestFinding("Missing Extinguisher", 0.90)),
                        null,
                        "yolo-v8",
                        "1.0",
                        20.0,
                        orgA.getId().toString(),
                        "NOT_APPROVED",
                        "NOT_CALIBRATED",
                        null,
                        null
                )
        );

        String runResponseStr = mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/ai-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var runNode = objectMapper.readTree(runResponseStr);
        String findingIdStr = runNode.get("findings").get(0).get("id").asText();
        UUID fakeRunId = UUID.randomUUID();

        // Submitting review with fake run ID -> 404 Not Found (or 400 Bad Request if run belongs to another app)
        String reviewPayload = "{\"reviewStatus\":\"CONFIRMED\",\"officerNotes\":\"Mismatched run\"}";
        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/ai-analysis/" + fakeRunId + "/findings/" + findingIdStr + "/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewPayload)
                .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void idempotentOfficerReviewSubmissionReturnsExistingWithoutDuplicateClutter() throws Exception {
        String officerToken = jwtService.generateToken(officerA.getUuid());
        AiAnalysisRequest req = new AiAnalysisRequest(docA.getId(), "FIRE_SAFETY_PLAN", insA.getUuid(), List.of(testEvidenceId));

        when(aiClient.analyze(any(ExternalAiRequest.class))).thenReturn(
                new ExternalAiResponse(
                        UUID.randomUUID(),
                        "COMPLETED",
                        List.of(createTestFinding("Missing Extinguisher", 0.92)),
                        null,
                        "yolo-v8",
                        "1.0",
                        20.0,
                        orgA.getId().toString(),
                        "NOT_APPROVED",
                        "NOT_CALIBRATED",
                        null,
                        null
                )
        );

        String runResponseStr = mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/ai-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var runNode = objectMapper.readTree(runResponseStr);
        String runIdStr = runNode.get("runId").asText();
        String findingIdStr = runNode.get("findings").get(0).get("id").asText();

        String reviewPayload = """
                {
                    "reviewStatus": "CONFIRMED",
                    "officerNotes": "Verified in person",
                    "hazardVerified": true
                }
                """;

        // First submission
        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/ai-analysis/" + runIdStr + "/findings/" + findingIdStr + "/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewPayload)
                .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus", is("CONFIRMED")));

        long initialReviewCount = officerReviewRepository.count();
        long initialAuditCount = auditRepository.count();

        // Second identical submission (idempotency check)
        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/ai-analysis/" + runIdStr + "/findings/" + findingIdStr + "/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewPayload)
                .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus", is("CONFIRMED")));

        // Counts must remain identical - no duplicate review or audit rows created
        assert officerReviewRepository.count() == initialReviewCount;
        assert auditRepository.count() == initialAuditCount;
    }

    @Test
    void cannotGetAnalysisByIdWithMismatchedApplicationId() throws Exception {
        String officerToken = jwtService.generateToken(officerA.getUuid());
        AiAnalysisRequest req = new AiAnalysisRequest(docA.getId(), "FIRE_SAFETY_PLAN", insA.getUuid(), List.of(testEvidenceId));

        when(aiClient.analyze(any(ExternalAiRequest.class))).thenReturn(
                new ExternalAiResponse(
                        UUID.randomUUID(),
                        "COMPLETED",
                        List.of(createTestFinding("Missing Extinguisher", 0.92)),
                        null,
                        "yolo-v8",
                        "1.0",
                        20.0,
                        orgA.getId().toString(),
                        "NOT_APPROVED",
                        "NOT_CALIBRATED",
                        null,
                        null
                )
        );

        String runResponseStr = mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/ai-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var runNode = objectMapper.readTree(runResponseStr);
        String runIdStr = runNode.get("runId").asText();

        // Create an app in the same organization
        NocApplication nocA2 = new NocApplication();
        nocA2.setOrgId(orgA.getId());
        nocA2.setOrganizationId(orgA.getUuid());
        nocA2.setApplicantId(citizenA.getUuid());
        nocA2.setTitle("NOC A2");
        nocA2.setBuildingName("RES");
        nocA2.setAddress("Add");
        nocA2.setStatus(NocStatus.SUBMITTED);
        nocA2 = nocRepository.save(nocA2);

        // Attempt to fetch run that belongs to nocA using nocA2's URL -> 400 Bad Request
        mockMvc.perform(get("/api/v1/noc/applications/" + nocA2.getUuid() + "/ai-analysis/" + runIdStr)
                .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotAccessEvidenceWithoutTenantContextOrCrossTenant() throws Exception {
        // Without auth token -> 401 Unauthorized
        mockMvc.perform(get("/api/v1/inspections/" + insA.getUuid() + "/evidence"))
                .andExpect(status().isUnauthorized());

        // Cross tenant officer -> 403 Forbidden
        User officerB = createUser("officerb_ev@example.com", Role.OFFICER, orgB);
        String officerBToken = jwtService.generateToken(officerB.getUuid());

        mockMvc.perform(get("/api/v1/inspections/" + insA.getUuid() + "/evidence")
                .header("Authorization", "Bearer " + officerBToken))
                .andExpect(status().isForbidden());
    }
}
