package com.firesphere.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firesphere.backend.ai.entity.AiAnalysisFinding;
import com.firesphere.backend.ai.entity.AiAnalysisRun;
import com.firesphere.backend.ai.entity.AiAnalysisStatus;
import com.firesphere.backend.ai.repository.AiAnalysisRunRepository;
import com.firesphere.backend.ai.repository.AiFindingRepository;
import com.firesphere.backend.auth.entity.User;
import com.firesphere.backend.auth.repository.UserRepository;
import com.firesphere.backend.checklist.dto.*;
import com.firesphere.backend.checklist.entity.ChecklistLifecycleStatus;
import com.firesphere.backend.checklist.entity.ChecklistResponseStatus;
import com.firesphere.backend.checklist.repository.InspectionChecklistRepository;
import com.firesphere.backend.common.security.JwtService;
import com.firesphere.backend.common.security.Role;
import com.firesphere.backend.inspection.entity.NocInspection;
import com.firesphere.backend.inspection.entity.NocInspectionEvidence;
import com.firesphere.backend.inspection.repository.NocInspectionEvidenceRepository;
import com.firesphere.backend.inspection.repository.NocInspectionRepository;
import com.firesphere.backend.noc.entity.NocApplication;
import com.firesphere.backend.noc.entity.NocStatus;
import com.firesphere.backend.noc.repository.NocApplicationRepository;
import com.firesphere.backend.organization.entity.Organization;
import com.firesphere.backend.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ChecklistDomainTests {

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
    private NocInspectionRepository inspectionRepository;

    @Autowired
    private NocInspectionEvidenceRepository evidenceRepository;

    @Autowired
    private InspectionChecklistRepository checklistRepository;

    @Autowired
    private AiAnalysisRunRepository aiRunRepository;

    @Autowired
    private AiFindingRepository aiFindingRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Organization orgA;
    private User officerA;
    private User officerB;
    private User adminA;
    private User citizenA;

    private NocApplication nocA;
    private NocInspection insA;
    private NocInspectionEvidence evidenceA;

    @BeforeEach
    void setup() {
        checklistRepository.deleteAll();
        aiFindingRepository.deleteAll();
        aiRunRepository.deleteAll();
        evidenceRepository.deleteAll();
        inspectionRepository.deleteAll();
        nocRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        orgA = new Organization();
        orgA.setName("Fire Prevention Dept A");
        orgA = organizationRepository.save(orgA);

        officerA = createUser("officerA@test.com", Role.OFFICER, orgA);
        officerB = createUser("officerB@test.com", Role.OFFICER, orgA);
        adminA = createUser("adminA@test.com", Role.ADMIN, orgA);
        citizenA = createUser("citizenA@test.com", Role.CITIZEN, orgA);

        nocA = new NocApplication();
        nocA.setOrgId(orgA.getId());
        nocA.setOrganizationId(orgA.getUuid());
        nocA.setApplicantId(citizenA.getUuid());
        nocA.setTitle("Commercial High-Rise Fire NOC");
        nocA.setBuildingName("Apex Tower");
        nocA.setAddress("Sector 18, Commercial Zone");
        nocA.setStatus(NocStatus.INSPECTION_IN_PROGRESS);
        nocA = nocRepository.save(nocA);

        insA = new NocInspection();
        insA.setApplicationId(nocA.getUuid());
        insA.setOrganizationId(orgA.getUuid());
        insA.setOrgId(orgA.getId());
        insA.setNocApplicationId(nocA.getId());
        insA.setInspectionType("ROUTINE");
        insA.setScheduledAt(OffsetDateTime.now());
        insA.setAssignedOfficer(officerA.getUuid());
        insA = inspectionRepository.save(insA);

        evidenceA = new NocInspectionEvidence();
        evidenceA.setOrgId(orgA.getId());
        evidenceA.setInspectionId(insA.getId());
        evidenceA.setFileName("extinguisher_apex_f1.jpg");
        evidenceA.setContentType("image/jpeg");
        evidenceA.setFileSizeBytes(102400L);
        evidenceA.setStorageBucket("firesphere-evidence");
        evidenceA.setStorageKey("evidence/" + UUID.randomUUID() + ".jpg");
        evidenceA.setSha256Hash("hash-test-evidence-12345");
        evidenceA.setCapturedByUserId(officerA.getId());
        evidenceA = evidenceRepository.save(evidenceA);
    }

    private User createUser(String email, Role role, Organization org) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("secret");
        user.setStatus("ACTIVE");
        user.setRole(role.name());
        user.setOrganization(org);
        user.setOrganizationId(org.getId());
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return userRepository.save(user);
    }

    @Test
    @DisplayName("Verify regulatory checklist templates are versioned, active, and retrievable")
    void testChecklistTemplateVersioningAndListing() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        mockMvc.perform(get("/api/v1/inspections/checklist-templates")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[0].version", is(1)))
                .andExpect(jsonPath("$[0].isActive", is(true)))
                .andExpect(jsonPath("$[0].sourceAuthority", is("NBC_2016_PART_4")))
                .andExpect(jsonPath("$[0].items", hasSize(greaterThanOrEqualTo(4))));
    }

    @Test
    @DisplayName("Verify checklist instantiation for inspection copies template items as NOT_REVIEWED (idempotent)")
    void testChecklistCreationForInspection() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        MvcResult result = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inspectionId", is(insA.getUuid().toString())))
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.totalItems", greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.pendingCount", greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.reviewedItems", is(0)))
                .andExpect(jsonPath("$.items[0].officerResponse", is("NOT_REVIEWED")))
                .andExpect(jsonPath("$.items[0].revisionNumber", is(1)))
                .andReturn();

        InspectionChecklistResponseDto dto1 = objectMapper.readValue(result.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);

        // Verify idempotency: calling create again returns the existing instance
        MvcResult result2 = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();

        InspectionChecklistResponseDto dto2 = objectMapper.readValue(result2.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);
        assertEquals(dto1.checklistId(), dto2.checklistId());
    }

    @Test
    @DisplayName("Verify officer can record COMPLIANT response with required photographic evidence")
    void testOfficerCanRecordCompliantResponse() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        // Create checklist
        MvcResult initResult = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();

        InspectionChecklistResponseDto checklistDto = objectMapper.readValue(initResult.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);
        InspectionChecklistItemDetailDto extItem = checklistDto.items().stream()
                .filter(i -> "EXT-01".equals(i.itemCode()))
                .findFirst()
                .orElseThrow();

        // Record COMPLIANT with evidence
        RecordOfficerResponseRequest request = new RecordOfficerResponseRequest(
                "COMPLIANT",
                "Extinguishers inspected on all floors; valid inspection tags and 1.4m mounting verified.",
                List.of(evidenceA.getUuid())
        );

        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + extItem.responseId() + "/response")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")))
                .andExpect(jsonPath("$.compliantCount", is(1)))
                .andExpect(jsonPath("$.reviewedItems", is(1)));

        // Retrieve item detail and verify revision number and evidence link
        mockMvc.perform(get("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + extItem.responseId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officerResponse", is("COMPLIANT")))
                .andExpect(jsonPath("$.revisionNumber", is(2)))
                .andExpect(jsonPath("$.linkedEvidence", hasSize(1)))
                .andExpect(jsonPath("$.linkedEvidence[0].evidenceId", is(evidenceA.getUuid().toString())));
    }

    @Test
    @DisplayName("Verify rejection of invalid response status and NOT_REVIEWED as a recorded answer")
    void testRejectInvalidResponseStatus() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        MvcResult initResult = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        InspectionChecklistResponseDto checklistDto = objectMapper.readValue(initResult.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);
        UUID itemId = checklistDto.items().get(0).responseId();

        // Attempt invalid status
        RecordOfficerResponseRequest invalidReq = new RecordOfficerResponseRequest("PASS", "Looks good", List.of());
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/response")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest());

        // Attempt NOT_REVIEWED
        RecordOfficerResponseRequest notReviewedReq = new RecordOfficerResponseRequest("NOT_REVIEWED", "Skip for now", List.of());
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/response")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(notReviewedReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Verify NON_COMPLIANT response requires mandatory deficiency remarks")
    void testRejectNonCompliantWithoutRemarks() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        MvcResult initResult = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        InspectionChecklistResponseDto checklistDto = objectMapper.readValue(initResult.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);
        UUID itemId = checklistDto.items().get(0).responseId();

        // NON_COMPLIANT with empty remarks fails
        RecordOfficerResponseRequest emptyRemarks = new RecordOfficerResponseRequest("NON_COMPLIANT", "", List.of(evidenceA.getUuid()));
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/response")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyRemarks)))
                .andExpect(status().isBadRequest());

        // NON_COMPLIANT with valid remarks succeeds
        RecordOfficerResponseRequest validRemarks = new RecordOfficerResponseRequest("NON_COMPLIANT", "Pressure gauge in red zone on 3rd floor; expired recharge date.", List.of(evidenceA.getUuid()));
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/response")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRemarks)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nonCompliantCount", is(1)));
    }

    @Test
    @DisplayName("Verify NOT_APPLICABLE response requires mandatory statutory justification remarks")
    void testRejectNotApplicableWithoutRemarks() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        MvcResult initResult = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        InspectionChecklistResponseDto checklistDto = objectMapper.readValue(initResult.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);
        UUID itemId = checklistDto.items().get(0).responseId();

        // NOT_APPLICABLE with null remarks fails
        RecordOfficerResponseRequest emptyRemarks = new RecordOfficerResponseRequest("NOT_APPLICABLE", null, null);
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/response")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyRemarks)))
                .andExpect(status().isBadRequest());

        // NOT_APPLICABLE with valid remarks succeeds
        RecordOfficerResponseRequest validRemarks = new RecordOfficerResponseRequest("NOT_APPLICABLE", "Single floor commercial warehouse under 15m; wet riser exempted under NBC 2016 Part 4 Table 7.", null);
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/response")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRemarks)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notApplicableCount", is(1)));
    }

    @Test
    @DisplayName("Verify rejection when mandatory evidence is missing for items requiring photographic evidence")
    void testRejectMandatoryEvidenceMissing() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        MvcResult initResult = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        InspectionChecklistResponseDto checklistDto = objectMapper.readValue(initResult.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);
        InspectionChecklistItemDetailDto extItem = checklistDto.items().stream()
                .filter(i -> Boolean.TRUE.equals(i.evidenceRequired()))
                .findFirst()
                .orElseThrow();

        // Attempt COMPLIANT without evidence on evidence-required item
        RecordOfficerResponseRequest noEvidence = new RecordOfficerResponseRequest("COMPLIANT", "I looked at it and it looked okay.", List.of());
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + extItem.responseId() + "/response")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noEvidence)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Verify officer cannot overwrite another officer's response without administrator privilege")
    void testOfficerCannotOverwriteOtherOfficerResponseWithoutAdmin() throws Exception {
        String tokenA = jwtService.generateToken(officerA.getUuid());
        String tokenB = jwtService.generateToken(officerB.getUuid());
        String tokenAdmin = jwtService.generateToken(adminA.getUuid());

        MvcResult initResult = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated())
                .andReturn();

        InspectionChecklistResponseDto checklistDto = objectMapper.readValue(initResult.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);
        InspectionChecklistItemDetailDto extItem = checklistDto.items().stream()
                .filter(i -> "EXT-01".equals(i.itemCode()))
                .findFirst()
                .orElseThrow();

        // Officer A records COMPLIANT
        RecordOfficerResponseRequest reqA = new RecordOfficerResponseRequest("COMPLIANT", "Mounting inspected by Officer A", List.of(evidenceA.getUuid()));
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + extItem.responseId() + "/response")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isOk());

        // Officer B attempts to overwrite Officer A's response -> 403 Forbidden
        RecordOfficerResponseRequest reqB = new RecordOfficerResponseRequest("NON_COMPLIANT", "Officer B disagrees with Officer A", List.of(evidenceA.getUuid()));
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + extItem.responseId() + "/response")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isForbidden());

        // Administrator overrides Officer A's response -> 200 OK
        RecordOfficerResponseRequest reqAdmin = new RecordOfficerResponseRequest("NON_COMPLIANT", "Administrative supervisor review: defect confirmed.", List.of(evidenceA.getUuid()));
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + extItem.responseId() + "/response")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nonCompliantCount", is(1)));
    }

    @Test
    @DisplayName("Verify AI finding link preserves advisory separation and NEVER overwrites officer response")
    void testAiFindingLinkPreservesAdvisorySeparation() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        // Create checklist
        MvcResult initResult = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        InspectionChecklistResponseDto checklistDto = objectMapper.readValue(initResult.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);
        InspectionChecklistItemDetailDto obsItem = checklistDto.items().stream()
                .filter(i -> "OBS-01".equals(i.itemCode()))
                .findFirst()
                .orElseThrow();

        // Create AI analysis run & finding
        AiAnalysisRun run = new AiAnalysisRun();
        run.setOrgId(orgA.getId());
        run.setInspectionId(insA.getId());
        run.setStatus(AiAnalysisStatus.COMPLETED);
        run.setModelName("qwen2.5-vl:3b");
        run = aiRunRepository.save(run);

        AiAnalysisFinding finding = new AiAnalysisFinding();
        finding.setRun(run);
        finding.setEvidenceId(evidenceA.getId());
        finding.setFindingType("OBSTACLE");
        finding.setSeverity("HIGH");
        finding.setConfidence(0.92);
        finding.setDescription("Pallet blocking exit corridor in wing B");
        finding.setIsAdvisory(true);
        finding = aiFindingRepository.save(finding);

        // Link AI finding to checklist item OBS-01
        LinkAiFindingRequest linkReq = new LinkAiFindingRequest(
                finding.getUuid(),
                "NON_COMPLIANT",
                0.92,
                "AI_SUGGESTION"
        );

        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + obsItem.responseId() + "/ai-links")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(linkReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiSuggestions", hasSize(1)))
                .andExpect(jsonPath("$.aiSuggestions[0].findingId", is(finding.getUuid().toString())))
                .andExpect(jsonPath("$.aiSuggestions[0].confidence", is(0.92)))
                .andExpect(jsonPath("$.aiSuggestions[0].suggestedCompliance", is("NON_COMPLIANT")))
                .andExpect(jsonPath("$.aiSuggestions[0].linkStatus", is("SUGGESTED")))
                .andExpect(jsonPath("$.aiSuggestions[0].reviewStatus", is("PENDING_REVIEW")))
                // CRUCIAL INVARIANT: Officer response remains NOT_REVIEWED!
                .andExpect(jsonPath("$.officerResponse", is("NOT_REVIEWED")));
    }

    @Test
    @DisplayName("Verify officer can review and confirm or dismiss AI suggestion link")
    void testOfficerReviewOfAiFindingLink() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        MvcResult initResult = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        InspectionChecklistResponseDto checklistDto = objectMapper.readValue(initResult.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);
        InspectionChecklistItemDetailDto item = checklistDto.items().get(0);

        AiAnalysisRun run = new AiAnalysisRun();
        run.setOrgId(orgA.getId());
        run.setInspectionId(insA.getId());
        run.setStatus(AiAnalysisStatus.COMPLETED);
        run = aiRunRepository.save(run);

        AiAnalysisFinding finding = new AiAnalysisFinding();
        finding.setRun(run);
        finding.setFindingType("EXIT_SIGN");
        finding.setConfidence(0.88);
        finding = aiFindingRepository.save(finding);

        LinkAiFindingRequest linkReq = new LinkAiFindingRequest(finding.getUuid(), "COMPLIANT", 0.88, "AI_SUGGESTION");
        MvcResult linkResult = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + item.responseId() + "/ai-links")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(linkReq)))
                .andExpect(status().isOk())
                .andReturn();

        InspectionChecklistItemDetailDto detail = objectMapper.readValue(linkResult.getResponse().getContentAsString(), InspectionChecklistItemDetailDto.class);
        UUID linkId = detail.aiSuggestions().get(0).linkId();

        // Officer confirms the AI suggestion
        ReviewAiLinkRequest reviewReq = new ReviewAiLinkRequest("CONFIRMED", "Verified illuminated exit sign present at northeast stairwell.");
        mockMvc.perform(patch("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + item.responseId() + "/ai-links/" + linkId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiSuggestions[0].reviewStatus", is("CONFIRMED")))
                .andExpect(jsonPath("$.aiSuggestions[0].linkStatus", is("ACCEPTED")))
                .andExpect(jsonPath("$.aiSuggestions[0].officerComment", is("Verified illuminated exit sign present at northeast stairwell.")));
    }

    @Test
    @DisplayName("Verify checklist completion rejects if any mandatory item is still NOT_REVIEWED")
    void testChecklistCompletionRejectsUnreviewedMandatoryItems() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        // Initialize checklist
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        // Attempt completion while items are NOT_REVIEWED -> fails with 400 Bad Request
        CompleteChecklistRequest completeReq = new CompleteChecklistRequest("Inspection done.");
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("NOT_REVIEWED")));
    }

    @Test
    @DisplayName("Verify checklist completion succeeds when all mandatory items are reviewed and validated")
    void testChecklistCompletionSucceedsWhenAllMandatoryItemsReviewed() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        MvcResult initResult = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        InspectionChecklistResponseDto checklistDto = objectMapper.readValue(initResult.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);

        // Review all items
        for (InspectionChecklistItemDetailDto item : checklistDto.items()) {
            if ("EXT-01".equals(item.itemCode()) || "EXIT-01".equals(item.itemCode()) || "EXIT-02".equals(item.itemCode()) || "OBS-01".equals(item.itemCode())) {
                RecordOfficerResponseRequest req = new RecordOfficerResponseRequest(
                        "COMPLIANT",
                        "Verified compliant with NBC 2016 Part 4 requirements during physical inspection.",
                        List.of(evidenceA.getUuid())
                );
                mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + item.responseId() + "/response")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isOk());
            } else if ("ALARM-01".equals(item.itemCode())) {
                RecordOfficerResponseRequest req = new RecordOfficerResponseRequest(
                        "COMPLIANT",
                        "Manual call point alarm tested and verified functional.",
                        List.of()
                );
                mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + item.responseId() + "/response")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isOk());
            } else if ("HYDR-01".equals(item.itemCode())) {
                RecordOfficerResponseRequest req = new RecordOfficerResponseRequest(
                        "NOT_APPLICABLE",
                        "Structure height is 12m; internal wet riser installation exempted under NBC Table 7.",
                        List.of()
                );
                mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + item.responseId() + "/response")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isOk());
            }
        }

        // Complete checklist
        CompleteChecklistRequest completeReq = new CompleteChecklistRequest("All mandatory requirements inspected and verified on-site.");
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.pendingCount", is(0)))
                .andExpect(jsonPath("$.completedAt", notNullValue()))
                .andExpect(jsonPath("$.completedByOfficer", is(officerA.getUuid().toString())));
    }

    @Test
    @DisplayName("Verify existing NocInspection lifecycle functionality remains unaffected")
    void testExistingInspectionFunctionalityUnaffected() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        mockMvc.perform(get("/api/v1/noc/applications/" + nocA.getUuid() + "/inspections")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(insA.getUuid().toString())));
    }

    @Test
    @DisplayName("Verify cross-tenant access to checklist is strictly forbidden (Step 3)")
    void testCrossTenantChecklistAccessForbidden() throws Exception {
        Organization orgB = new Organization();
        orgB.setName("Fire Prevention Dept B");
        orgB = organizationRepository.save(orgB);

        User officerOrgB = createUser("officerB_tenant2@test.com", Role.OFFICER, orgB);
        String tokenOrgB = jwtService.generateToken(officerOrgB.getUuid());

        // Officer from Org B attempting to create checklist for Inspection in Org A -> 403
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + tokenOrgB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        // Officer from Org B attempting to read checklist for Inspection in Org A -> 403
        mockMvc.perform(get("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + tokenOrgB))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Verify AI finding from unrelated inspection/tenant cannot be linked (Invariant B)")
    void testRejectAiFindingFromDifferentInspectionOrTenant() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        // Initialize checklist
        MvcResult initResult = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        InspectionChecklistResponseDto checklistDto = objectMapper.readValue(initResult.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);
        InspectionChecklistItemDetailDto item = checklistDto.items().get(0);

        // Create AI analysis run for a completely different inspection ID
        AiAnalysisRun foreignRun = new AiAnalysisRun();
        foreignRun.setOrgId(orgA.getId());
        foreignRun.setInspectionId(99999L);
        foreignRun.setStatus(AiAnalysisStatus.COMPLETED);
        foreignRun = aiRunRepository.save(foreignRun);

        AiAnalysisFinding foreignFinding = new AiAnalysisFinding();
        foreignFinding.setRun(foreignRun);
        foreignFinding.setFindingType("OBSTACLE");
        foreignFinding.setConfidence(0.95);
        foreignFinding = aiFindingRepository.save(foreignFinding);

        LinkAiFindingRequest linkReq = new LinkAiFindingRequest(foreignFinding.getUuid(), "NON_COMPLIANT", 0.95, "AI_SUGGESTION");
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + item.responseId() + "/ai-links")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(linkReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("does not belong to this inspection")));
    }

    @Test
    @DisplayName("Verify completed checklist cannot be modified (Invariant I)")
    void testCompletedChecklistCannotBeModified() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        // Initialize and review all items
        MvcResult initResult = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        InspectionChecklistResponseDto checklistDto = objectMapper.readValue(initResult.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);
        for (InspectionChecklistItemDetailDto item : checklistDto.items()) {
            RecordOfficerResponseRequest req = new RecordOfficerResponseRequest(
                    "COMPLIANT",
                    "Requirement inspected and compliant with NBC 2016 Part 4.",
                    Boolean.TRUE.equals(item.evidenceRequired()) ? List.of(evidenceA.getUuid()) : List.of()
            );
            mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + item.responseId() + "/response")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }

        // Complete the checklist
        CompleteChecklistRequest completeReq = new CompleteChecklistRequest("All inspections complete.");
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));

        UUID targetItemId = checklistDto.items().get(0).responseId();

        // Attempt to update response on completed checklist -> 400 Bad Request
        RecordOfficerResponseRequest updateReq = new RecordOfficerResponseRequest("NON_COMPLIANT", "Late defect observed", List.of(evidenceA.getUuid()));
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + targetItemId + "/response")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already been finalized and completed")));

        // Attempt to attach evidence on completed checklist -> 400 Bad Request
        AttachEvidenceRequest attachReq = new AttachEvidenceRequest(evidenceA.getUuid(), "Late photo");
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + targetItemId + "/evidence")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attachReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already been finalized and completed")));
    }

    @Test
    @DisplayName("Verify inactive or deleted evidence is rejected (Invariant F & L)")
    void testRejectInactiveEvidence() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        // Create inactive evidence
        NocInspectionEvidence deletedEvidence = new NocInspectionEvidence();
        deletedEvidence.setOrgId(orgA.getId());
        deletedEvidence.setInspectionId(insA.getId());
        deletedEvidence.setFileName("corrupted_photo.jpg");
        deletedEvidence.setContentType("image/jpeg");
        deletedEvidence.setFileSizeBytes(5000L);
        deletedEvidence.setStorageBucket("firesphere-evidence");
        deletedEvidence.setStorageKey("evidence/deleted-" + UUID.randomUUID() + ".jpg");
        deletedEvidence.setSha256Hash("corrupt-hash-999");
        deletedEvidence.setStatus("DELETED");
        deletedEvidence = evidenceRepository.save(deletedEvidence);

        MvcResult initResult = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        InspectionChecklistResponseDto checklistDto = objectMapper.readValue(initResult.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);
        UUID itemId = checklistDto.items().get(0).responseId();

        // Attempt to attach deleted evidence
        AttachEvidenceRequest attachReq = new AttachEvidenceRequest(deletedEvidence.getUuid(), "Deleted photo");
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/evidence")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attachReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("not active or has been invalidated")));
    }

    // =========================================================================
    // PHASE 17 — AI Findings-to-Checklist Mapping Tests
    // =========================================================================

    private AiAnalysisFinding createFinding(NocInspection inspection, String modelName, String modelProvider, String type, Double conf, String severity) {
        AiAnalysisRun run = new AiAnalysisRun();
        run.setOrgId(inspection.getOrgId());
        run.setInspectionId(inspection.getId());
        run.setStatus(AiAnalysisStatus.COMPLETED);
        run.setModelName(modelName);
        run.setModelProvider(modelProvider);
        run = aiRunRepository.save(run);

        AiAnalysisFinding finding = new AiAnalysisFinding();
        finding.setRun(run);
        finding.setEvidenceId(evidenceA.getId());
        finding.setFindingType(type);
        finding.setSeverity(severity != null ? severity : "MEDIUM");
        finding.setConfidence(conf != null ? conf : 0.85);
        finding.setDescription("Automated AI finding for " + type);
        finding.setIsAdvisory(true);
        return aiFindingRepository.save(finding);
    }

    @Test
    @DisplayName("Phase 17 Req 1: Valid AI finding maps deterministically to applicable checklist item with provenance")
    void testValidFindingMapsToApplicableItem() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        // Initialize checklist
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        // Create CV finding for FIRE_EXTINGUISHER
        AiAnalysisFinding finding = createFinding(insA, "yolov8x-fire", "CV", "FIRE_EXTINGUISHER", 0.89, "MEDIUM");

        // Trigger mapping
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/map-ai-findings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFindingsCount", is(1)))
                .andExpect(jsonPath("$.mappedCount", is(1)))
                .andExpect(jsonPath("$.unmappedCount", is(0)))
                .andExpect(jsonPath("$.mappings[0].findingId", is(finding.getUuid().toString())))
                .andExpect(jsonPath("$.mappings[0].itemCode", is("EXT-01")))
                .andExpect(jsonPath("$.mappings[0].sourceProvenance", is("CV")))
                .andExpect(jsonPath("$.mappings[0].originalCategory", is("FIRE_EXTINGUISHER")))
                .andExpect(jsonPath("$.mappings[0].suggestedCompliance", is("NEEDS_FURTHER_INSPECTION")))
                .andExpect(jsonPath("$.mappings[0].mappingReason", containsString("NBC 2016 Part 4")));

        // Verify finding link appears in checklist item detail
        MvcResult clResult = mockMvc.perform(get("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        InspectionChecklistResponseDto cl = objectMapper.readValue(clResult.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);
        InspectionChecklistItemDetailDto extItem = cl.items().stream().filter(i -> "EXT-01".equals(i.itemCode())).findFirst().orElseThrow();
        assertEquals(1, extItem.aiSuggestions().size());
        assertEquals("CV", extItem.aiSuggestions().get(0).sourceProvenance());
        assertEquals("PENDING_REVIEW", extItem.aiSuggestions().get(0).reviewStatus());
    }

    @Test
    @DisplayName("Phase 17 Req 2: Unsupported categories remain unmapped with explanation and no invented items")
    void testUnsupportedCategoryRemainsUnmapped() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        createFinding(insA, "qwen2.5-vl:3b", "VLM", "SPRINKLER_HEAD_DEFECT", 0.91, "HIGH");
        createFinding(insA, "yolov8x", "CV", "ELECTRICAL_PANEL_HAZARD", 0.85, "HIGH");
        createFinding(insA, "yolov8x", "CV", "CHAIR", 0.95, "LOW");

        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/map-ai-findings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFindingsCount", is(3)))
                .andExpect(jsonPath("$.mappedCount", is(0)))
                .andExpect(jsonPath("$.unmappedCount", is(3)))
                .andExpect(jsonPath("$.ambiguousCount", is(0)))
                .andExpect(jsonPath("$.unmappedFindings[?(@.originalCategory == 'SPRINKLER_HEAD_DEFECT')].reason", hasItem(containsString("sprinkler inspection clause"))))
                .andExpect(jsonPath("$.unmappedFindings[?(@.originalCategory == 'ELECTRICAL_PANEL_HAZARD')].reason", hasItem(containsString("electrical panel clause"))))
                .andExpect(jsonPath("$.unmappedFindings[?(@.originalCategory == 'CHAIR')].reason", hasItem(containsString("does not correspond"))));
    }

    @Test
    @DisplayName("Phase 17 Req 3: Ambiguous categories are not silently mapped and are held for officer disambiguation")
    void testAmbiguousCategoryNotSilentlyMapped() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        createFinding(insA, "qwen2.5-vl:3b", "VLM", "EMERGENCY_EXIT_OBSTRUCTED", 0.92, "CRITICAL");
        createFinding(insA, "qwen2.5-vl:3b", "VLM", "COMBUSTIBLE_MATERIAL_HAZARD", 0.88, "HIGH");
        createFinding(insA, "yolov8x", "CV", "GENERIC_FIRE_SAFETY_DEFECT", 0.75, "MEDIUM");

        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/map-ai-findings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFindingsCount", is(3)))
                .andExpect(jsonPath("$.mappedCount", is(0)))
                .andExpect(jsonPath("$.ambiguousCount", is(3)))
                .andExpect(jsonPath("$.unmappedFindings[?(@.originalCategory == 'EMERGENCY_EXIT_OBSTRUCTED')].status", hasItem("AMBIGUOUS")))
                .andExpect(jsonPath("$.unmappedFindings[?(@.originalCategory == 'COMBUSTIBLE_MATERIAL_HAZARD')].status", hasItem("AMBIGUOUS")))
                .andExpect(jsonPath("$.unmappedFindings[?(@.originalCategory == 'GENERIC_FIRE_SAFETY_DEFECT')].status", hasItem("AMBIGUOUS")));
    }

    @Test
    @DisplayName("Phase 17 Req 4: Cross-inspection finding links are strictly rejected")
    void testCrossInspectionFindingLinkRejected() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        // Create checklist on Inspection A
        MvcResult clResult = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        InspectionChecklistResponseDto clA = objectMapper.readValue(clResult.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);
        UUID itemIdA = clA.items().get(0).responseId();

        // Create second inspection B in org A
        NocInspection insB = new NocInspection();
        insB.setApplicationId(nocA.getUuid());
        insB.setOrganizationId(orgA.getUuid());
        insB.setOrgId(orgA.getId());
        insB.setNocApplicationId(nocA.getId());
        insB.setInspectionType("FOLLOW_UP");
        insB.setScheduledAt(OffsetDateTime.now());
        insB.setAssignedOfficer(officerA.getUuid());
        insB = inspectionRepository.save(insB);

        // Finding created under inspection B
        AiAnalysisFinding findingB = createFinding(insB, "yolov8x", "CV", "FIRE_EXTINGUISHER", 0.88, "MEDIUM");

        // Attempt manual link of findingB to checklist of inspectionA -> 400 Bad Request
        LinkAiFindingRequest linkReq = new LinkAiFindingRequest(findingB.getUuid(), "COMPLIANT", 0.88, "AI_SUGGESTION");
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemIdA + "/ai-links")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(linkReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("AI Finding does not belong to this inspection")));

        // Run automated mapping on inspection A -> findingB is not mapped
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/map-ai-findings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mappedCount", is(0)));
    }

    @Test
    @DisplayName("Phase 17 Req 5: Cross-tenant finding links are strictly rejected")
    void testCrossTenantFindingLinkRejected() throws Exception {
        String tokenA = jwtService.generateToken(officerA.getUuid());

        // Create Org B, Officer B, Noc B, Inspection B
        Organization orgB = new Organization();
        orgB.setName("Fire Prevention Dept B");
        orgB = organizationRepository.save(orgB);

        User officerBForeign = createUser("foreignOfficer@test.com", Role.OFFICER, orgB);
        String tokenB = jwtService.generateToken(officerBForeign.getUuid());

        NocApplication nocB = new NocApplication();
        nocB.setOrgId(orgB.getId());
        nocB.setOrganizationId(orgB.getUuid());
        nocB.setTitle("Commercial B");
        nocB.setStatus(NocStatus.INSPECTION_IN_PROGRESS);
        nocB = nocRepository.save(nocB);

        NocInspection insB = new NocInspection();
        insB.setApplicationId(nocB.getUuid());
        insB.setOrganizationId(orgB.getUuid());
        insB.setOrgId(orgB.getId());
        insB.setNocApplicationId(nocB.getId());
        insB.setInspectionType("ROUTINE");
        insB = inspectionRepository.save(insB);

        AiAnalysisFinding findingForeign = createFinding(insB, "yolov8x", "CV", "FIRE_EXTINGUISHER", 0.85, "MEDIUM");

        // Initialize checklist on Ins A
        MvcResult clResult = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated())
                .andReturn();
        InspectionChecklistResponseDto clA = objectMapper.readValue(clResult.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);
        UUID itemIdA = clA.items().get(0).responseId();

        // Officer A tries to link foreign finding to Ins A -> 400 Bad Request
        LinkAiFindingRequest linkReq = new LinkAiFindingRequest(findingForeign.getUuid(), "COMPLIANT", 0.85, "AI_SUGGESTION");
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemIdA + "/ai-links")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(linkReq)))
                .andExpect(status().isBadRequest());

        // Foreign officer tries to map Ins A -> 403 Forbidden
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/map-ai-findings")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Phase 17 Req 6: Duplicate mapping requests are idempotent and do not duplicate links")
    void testDuplicateMappingIdempotent() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        createFinding(insA, "yolov8x", "CV", "FIRE_EXTINGUISHER", 0.88, "MEDIUM");

        // First mapping execution
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/map-ai-findings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mappedCount", is(1)));

        // Second mapping execution
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/map-ai-findings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mappedCount", is(1)));

        // Verify only 1 link in checklist item
        MvcResult clResult = mockMvc.perform(get("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        InspectionChecklistResponseDto cl = objectMapper.readValue(clResult.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);
        InspectionChecklistItemDetailDto extItem = cl.items().stream().filter(i -> "EXT-01".equals(i.itemCode())).findFirst().orElseThrow();
        assertEquals(1, extItem.aiSuggestions().size());
    }

    @Test
    @DisplayName("Phase 17 Req 7: Mapping operations never overwrite officer recorded response")
    void testMappingDoesNotChangeOfficerResponse() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        MvcResult initResult = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        InspectionChecklistResponseDto cl = objectMapper.readValue(initResult.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);
        UUID extItemId = cl.items().stream().filter(i -> "EXT-01".equals(i.itemCode())).findFirst().orElseThrow().responseId();

        // Officer records response COMPLIANT
        RecordOfficerResponseRequest officerReq = new RecordOfficerResponseRequest("COMPLIANT", "Physically verified 4kg ABC extinguisher fully charged", List.of(evidenceA.getUuid()));
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + extItemId + "/response")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(officerReq)))
                .andExpect(status().isOk());

        // An AI finding is detected suggesting NON_COMPLIANT
        createFinding(insA, "qwen2.5-vl:3b", "VLM", "FIRE_EXTINGUISHER_MISSING", 0.94, "HIGH");

        // Run automated mapping
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/map-ai-findings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mappedCount", is(1)));

        // Verify officer response remains COMPLIANT and officer remarks untouched
        MvcResult verifyResult = mockMvc.perform(get("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + extItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        InspectionChecklistItemDetailDto extItem = objectMapper.readValue(verifyResult.getResponse().getContentAsString(), InspectionChecklistItemDetailDto.class);
        assertEquals(ChecklistResponseStatus.COMPLIANT, extItem.officerResponse());
        assertEquals("Physically verified 4kg ABC extinguisher fully charged", extItem.officerRemarks());
        assertEquals("NON_COMPLIANT", extItem.aiSuggestions().get(0).suggestedCompliance());
    }

    @Test
    @DisplayName("Phase 17 Req 8: Accepting an AI suggestion does not set a checklist compliance result")
    void testAcceptingSuggestionDoesNotSetComplianceResult() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        MvcResult initResult = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        InspectionChecklistResponseDto cl = objectMapper.readValue(initResult.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);
        UUID obsItemId = cl.items().stream().filter(i -> "OBS-01".equals(i.itemCode())).findFirst().orElseThrow().responseId();

        createFinding(insA, "yolov8x", "CV", "OBSTACLE", 0.90, "HIGH");

        // Map finding
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/map-ai-findings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Get suggestion link ID
        MvcResult itemResult = mockMvc.perform(get("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + obsItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        InspectionChecklistItemDetailDto item = objectMapper.readValue(itemResult.getResponse().getContentAsString(), InspectionChecklistItemDetailDto.class);
        UUID linkId = item.aiSuggestions().get(0).linkId();

        // Officer accepts suggestion
        ReviewAiLinkRequest reviewReq = new ReviewAiLinkRequest("CONFIRMED", "Agreed, cardboard box pile is relevant to corridor clearance");
        mockMvc.perform(patch("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + obsItemId + "/ai-links/" + linkId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiSuggestions[0].linkStatus", is("ACCEPTED")))
                .andExpect(jsonPath("$.aiSuggestions[0].reviewStatus", is("CONFIRMED")));

        // CRITICAL INVARIANT: Checklist item officer response MUST STILL BE NOT_REVIEWED
        MvcResult afterResult = mockMvc.perform(get("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + obsItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        InspectionChecklistItemDetailDto afterItem = objectMapper.readValue(afterResult.getResponse().getContentAsString(), InspectionChecklistItemDetailDto.class);
        assertEquals(ChecklistResponseStatus.NOT_REVIEWED, afterItem.officerResponse());
    }

    @Test
    @DisplayName("Phase 17 Req 9: Dismissing a suggestion preserves original AI finding and provenance")
    void testDismissingSuggestionPreservesFinding() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        MvcResult initResult = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        InspectionChecklistResponseDto cl = objectMapper.readValue(initResult.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);
        UUID exitItemId = cl.items().stream().filter(i -> "EXIT-01".equals(i.itemCode())).findFirst().orElseThrow().responseId();

        AiAnalysisFinding finding = createFinding(insA, "yolov8x", "CV", "EXIT_SIGN", 0.86, "MEDIUM");

        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/map-ai-findings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        MvcResult itemResult = mockMvc.perform(get("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + exitItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        InspectionChecklistItemDetailDto item = objectMapper.readValue(itemResult.getResponse().getContentAsString(), InspectionChecklistItemDetailDto.class);
        UUID linkId = item.aiSuggestions().get(0).linkId();

        // Dismiss suggestion
        ReviewAiLinkRequest dismissReq = new ReviewAiLinkRequest("DISMISSED", "Optical artifact from skylight, not an illuminated sign");
        mockMvc.perform(patch("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + exitItemId + "/ai-links/" + linkId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dismissReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiSuggestions[0].linkStatus", is("DISMISSED")));

        // Verify underlying finding in database still exists completely intact
        AiAnalysisFinding persistedFinding = aiFindingRepository.findByUuid(finding.getUuid()).orElseThrow();
        assertEquals(0.86, persistedFinding.getConfidence());
        assertEquals("EXIT_SIGN", persistedFinding.getFindingType());
        assertNotNull(persistedFinding.getRun());
    }

    @Test
    @DisplayName("Phase 17 Req 10: Conflicting CV and VLM findings retain their distinct provenance and are both visible")
    void testConflictingCvAndVlmFindingsRetainProvenance() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        // Finding 1: CV detected extinguisher present
        createFinding(insA, "yolov8x-fire", "CV", "FIRE_EXTINGUISHER", 0.85, "MEDIUM");
        // Finding 2: VLM detected missing extinguisher
        createFinding(insA, "qwen2.5-vl:3b", "VLM", "FIRE_EXTINGUISHER_MISSING", 0.91, "HIGH");

        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/map-ai-findings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mappedCount", is(2)))
                .andExpect(jsonPath("$.mappings[0].conflictType", is("CV_VLM_CONFLICT")))
                .andExpect(jsonPath("$.mappings[1].conflictType", is("CV_VLM_CONFLICT")));

        // Both links exist in EXT-01
        MvcResult clResult = mockMvc.perform(get("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        InspectionChecklistResponseDto cl = objectMapper.readValue(clResult.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);
        InspectionChecklistItemDetailDto extItem = cl.items().stream().filter(i -> "EXT-01".equals(i.itemCode())).findFirst().orElseThrow();
        assertEquals(2, extItem.aiSuggestions().size());

        List<String> sources = extItem.aiSuggestions().stream().map(AiSuggestionLinkDto::sourceProvenance).toList();
        assertTrue(sources.contains("CV"));
        assertTrue(sources.contains("VLM"));
    }

    @Test
    @DisplayName("Phase 17 Req 11: Missing or invalid evidence does not create a compliance pass")
    void testMissingEvidenceDoesNotCreateCompliancePass() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        MvcResult initResult = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        InspectionChecklistResponseDto cl = objectMapper.readValue(initResult.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);

        // Map finding for extinguisher
        createFinding(insA, "yolov8x", "CV", "FIRE_EXTINGUISHER", 0.95, "LOW");
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/map-ai-findings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Answer all items without evidence for EXT-01
        for (InspectionChecklistItemDetailDto item : cl.items()) {
            if ("EXT-01".equals(item.itemCode())) {
                // Try recording COMPLIANT without evidence (fails validation rule 4)
                RecordOfficerResponseRequest noEvReq = new RecordOfficerResponseRequest("COMPLIANT", "Extinguisher looks good", List.of());
                mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + item.responseId() + "/response")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(noEvReq)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message", containsString("Photographic evidence is mandatory")));
            }
        }
    }

    @Test
    @DisplayName("Phase 17 Req 12: Completed checklists reject mapping mutations")
    void testCompletedChecklistRejectsMappingMutations() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());

        MvcResult initResult = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        InspectionChecklistResponseDto cl = objectMapper.readValue(initResult.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);

        // Fully respond and complete checklist
        for (InspectionChecklistItemDetailDto item : cl.items()) {
            List<UUID> evList = Boolean.TRUE.equals(item.evidenceRequired()) ? List.of(evidenceA.getUuid()) : List.of();
            RecordOfficerResponseRequest respReq = new RecordOfficerResponseRequest("COMPLIANT", "Statutory compliance verified on site", evList);
            mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + item.responseId() + "/response")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(respReq)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CompleteChecklistRequest("All inspections complete."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));

        // Attempt mapping on completed checklist -> 400 Bad Request
        createFinding(insA, "yolov8x", "CV", "FIRE_EXTINGUISHER", 0.90, "MEDIUM");
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/map-ai-findings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already been finalized and completed")));
    }

    @Test
    @DisplayName("Phase 17 Req 13: Unauthorized users cannot create or review mappings")
    void testUnauthorizedUserCannotCreateOrReviewMappings() throws Exception {
        String citizenToken = jwtService.generateToken(citizenA.getUuid());
        String officerToken = jwtService.generateToken(officerA.getUuid());

        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isCreated());

        createFinding(insA, "yolov8x", "CV", "FIRE_EXTINGUISHER", 0.88, "MEDIUM");

        // Citizen attempts mapping execution -> 403 Forbidden
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/map-ai-findings")
                        .header("Authorization", "Bearer " + citizenToken))
                .andExpect(status().isForbidden());

        // Unauthenticated attempts mapping -> 401 Unauthorized
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/map-ai-findings"))
                .andExpect(status().isUnauthorized());

        // Citizen is DENIED access to internal AI mappings -> 403 Forbidden
        mockMvc.perform(get("/api/v1/inspections/" + insA.getUuid() + "/checklist/ai-mappings")
                        .header("Authorization", "Bearer " + citizenToken))
                .andExpect(status().isForbidden());

        // Officer CAN view mappings read-only -> 200 OK
        mockMvc.perform(get("/api/v1/inspections/" + insA.getUuid() + "/checklist/ai-mappings")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk());
    }
}
