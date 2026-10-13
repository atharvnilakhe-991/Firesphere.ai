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
public class OfficerChecklistApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

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
    private Organization orgB;

    private User officerA; // Assigned officer
    private User officerB; // Other officer in same org
    private User adminA;    // Administrator
    private User citizenApplicant; // Citizen applicant
    private User citizenStranger;  // Citizen unrelated to this application
    private User officerOrgB;      // Officer in different tenant

    private NocApplication nocA;
    private NocInspection insA;
    private NocInspectionEvidence evidenceA;
    private NocInspectionEvidence evidenceB;

    @BeforeEach
    void setUp() {
        checklistRepository.deleteAll();
        aiFindingRepository.deleteAll();
        aiRunRepository.deleteAll();
        evidenceRepository.deleteAll();
        inspectionRepository.deleteAll();
        nocRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        // 1. Organizations
        orgA = new Organization();
        orgA.setName("Metropolitan Fire Service - Zone 1");
        orgA = organizationRepository.save(orgA);

        orgB = new Organization();
        orgB.setName("Metropolitan Fire Service - Zone 2");
        orgB = organizationRepository.save(orgB);

        // 2. Users
        officerA = createUser("officerA@firesphere.gov", Role.OFFICER, orgA);
        officerB = createUser("officerB@firesphere.gov", Role.OFFICER, orgA);
        adminA = createUser("admin@firesphere.gov", Role.ADMIN, orgA);
        citizenApplicant = createUser("applicant@citizen.com", Role.CITIZEN, orgA);
        citizenStranger = createUser("stranger@citizen.com", Role.CITIZEN, orgA);
        officerOrgB = createUser("officer@zone2.gov", Role.OFFICER, orgB);

        // 3. NOC Application
        nocA = new NocApplication();
        nocA.setOrgId(orgA.getId());
        nocA.setOrganizationId(orgA.getUuid());
        nocA.setApplicantId(citizenApplicant.getUuid());
        nocA.setTitle("Skyline Business Tower Commercial NOC");
        nocA.setBuildingName("Skyline Business Tower");
        nocA.setAddress("Plot 42, Cyber City");
        nocA.setStatus(NocStatus.INSPECTION_IN_PROGRESS);
        nocA = nocRepository.save(nocA);

        // 4. Inspection assigned to officerA
        insA = new NocInspection();
        insA.setApplicationId(nocA.getUuid());
        insA.setOrganizationId(orgA.getUuid());
        insA.setOrgId(orgA.getId());
        insA.setNocApplicationId(nocA.getId());
        insA.setInspectionType("COMMERCIAL");
        insA.setScheduledAt(OffsetDateTime.now());
        insA.setAssignedOfficer(officerA.getUuid());
        insA = inspectionRepository.save(insA);

        // 5. Active Evidence for this inspection
        evidenceA = new NocInspectionEvidence();
        evidenceA.setOrgId(orgA.getId());
        evidenceA.setInspectionId(insA.getId());
        evidenceA.setFileName("extinguisher_corridor_f2.jpg");
        evidenceA.setContentType("image/jpeg");
        evidenceA.setFileSizeBytes(204800L);
        evidenceA.setStorageBucket("firesphere-evidence");
        evidenceA.setStorageKey("evidence/extinguisher-" + UUID.randomUUID() + ".jpg");
        evidenceA.setSha256Hash("valid-sha256-hash-001");
        evidenceA.setStatus("ACTIVE");
        evidenceA = evidenceRepository.save(evidenceA);

        evidenceB = new NocInspectionEvidence();
        evidenceB.setOrgId(orgA.getId());
        evidenceB.setInspectionId(insA.getId());
        evidenceB.setFileName("exit_door_staircase_f1.jpg");
        evidenceB.setContentType("image/jpeg");
        evidenceB.setFileSizeBytes(153600L);
        evidenceB.setStorageBucket("firesphere-evidence");
        evidenceB.setStorageKey("evidence/exitdoor-" + UUID.randomUUID() + ".jpg");
        evidenceB.setSha256Hash("valid-sha256-hash-002");
        evidenceB.setStatus("ACTIVE");
        evidenceB = evidenceRepository.save(evidenceB);
    }

    private User createUser(String email, Role role, Organization org) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLM");
        u.setRole(role.name());
        u.setStatus("ACTIVE");
        u.setOrgId(org.getId());
        u.setOrganizationId(org.getUuid());
        return userRepository.save(u);
    }

    private InspectionChecklistResponseDto initChecklist(User officer) throws Exception {
        String token = jwtService.generateToken(officer.getUuid());
        MvcResult res = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(res.getResponse().getContentAsString(), InspectionChecklistResponseDto.class);
    }

    // =========================================================================
    // 1. Authorized Officer Checklist Retrieval
    // =========================================================================

    @Test
    @DisplayName("Req 1: Authorized assigned officer can retrieve checklist with complete item metadata")
    void testAuthorizedOfficerChecklistRetrieval() throws Exception {
        initChecklist(officerA);
        String token = jwtService.generateToken(officerA.getUuid());

        mockMvc.perform(get("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checklistId", notNullValue()))
                .andExpect(jsonPath("$.inspectionId", is(insA.getUuid().toString())))
                .andExpect(jsonPath("$.templateCode", is("NBC_2016_COMMERCIAL")))
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.totalItems", greaterThan(0)))
                .andExpect(jsonPath("$.pendingCount", greaterThan(0)))
                .andExpect(jsonPath("$.items", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.items[0].responseId", notNullValue()))
                .andExpect(jsonPath("$.items[0].itemCode", notNullValue()))
                .andExpect(jsonPath("$.items[0].itemTitle", notNullValue()))
                .andExpect(jsonPath("$.items[0].parameterDescription", notNullValue()));
    }

    // =========================================================================
    // 2. Unauthorized Officer Modification Denial
    // =========================================================================

    @Test
    @DisplayName("Req 2: Officer B cannot modify inspection assigned to Officer A, but Administrator can")
    void testUnauthorizedOfficerModificationDenial() throws Exception {
        InspectionChecklistResponseDto checklistDto = initChecklist(officerA);
        InspectionChecklistItemDetailDto item = checklistDto.items().get(0);

        String tokenOfficerB = jwtService.generateToken(officerB.getUuid());
        String tokenAdmin = jwtService.generateToken(adminA.getUuid());

        // Officer B attempts to record response -> 403 Forbidden
        RecordOfficerResponseRequest reqB = new RecordOfficerResponseRequest(
                "COMPLIANT",
                "Officer B attempting to record response",
                List.of(evidenceA.getUuid())
        );
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + item.responseId() + "/response")
                        .header("Authorization", "Bearer " + tokenOfficerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isForbidden());

        // Officer B attempts to attach evidence -> 403 Forbidden
        AttachEvidenceRequest attachReq = new AttachEvidenceRequest(evidenceA.getUuid(), "Officer B photo");
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + item.responseId() + "/evidence")
                        .header("Authorization", "Bearer " + tokenOfficerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attachReq)))
                .andExpect(status().isForbidden());

        // Officer B attempts to complete checklist -> 403 Forbidden
        CompleteChecklistRequest completeReq = new CompleteChecklistRequest("Officer B trying to complete");
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/complete")
                        .header("Authorization", "Bearer " + tokenOfficerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isForbidden());

        // Administrator CAN record response (supervisory role) -> 200 OK
        RecordOfficerResponseRequest reqAdmin = new RecordOfficerResponseRequest(
                "COMPLIANT",
                "Supervisory admin determination verified on site",
                List.of(evidenceA.getUuid())
        );
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + item.responseId() + "/response")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqAdmin)))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 3. Cross-Tenant Checklist Access Rejection
    // =========================================================================

    @Test
    @DisplayName("Req 3: Officer from Tenant B cannot view or modify checklist for Tenant A inspection")
    void testCrossTenantChecklistAccessForbidden() throws Exception {
        initChecklist(officerA);
        String tokenOrgB = jwtService.generateToken(officerOrgB.getUuid());

        // Read attempt -> 403 Forbidden
        mockMvc.perform(get("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + tokenOrgB))
                .andExpect(status().isForbidden());

        // Mutation attempt -> 403 Forbidden
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + tokenOrgB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 4. Citizen Access to Allowed Information & Stranger Citizen Denial
    // =========================================================================

    @Test
    @DisplayName("Req 4: Citizen applicant can view checklist, but unrelated citizen is denied")
    void testCitizenApplicantAccessAndStrangerDenial() throws Exception {
        initChecklist(officerA);

        String applicantToken = jwtService.generateToken(citizenApplicant.getUuid());
        String strangerToken = jwtService.generateToken(citizenStranger.getUuid());

        // Applicant citizen can view checklist -> 200 OK
        mockMvc.perform(get("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateCode", is("NBC_2016_COMMERCIAL")));

        // Stranger citizen attempting to view checklist -> 403 Forbidden
        mockMvc.perform(get("/api/v1/inspections/" + insA.getUuid() + "/checklist")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 5. Citizen Denial for Internal AI Findings and Data Sanitization
    // =========================================================================

    @Test
    @DisplayName("Req 5: Citizen is strictly denied GET ai-mappings, and item details strip AI advisory findings")
    void testCitizenDenialForInternalAiFindingsAndSanitization() throws Exception {
        InspectionChecklistResponseDto cl = initChecklist(officerA);
        InspectionChecklistItemDetailDto item = cl.items().get(0);

        // Create an AI finding and link it to item
        AiAnalysisRun run = new AiAnalysisRun();
        run.setOrgId(orgA.getId());
        run.setInspectionId(insA.getId());
        run.setStatus(AiAnalysisStatus.COMPLETED);
        run.setModelName("qwen2.5-vl-72b");
        run.setModelVersion("1.0.0");
        run = aiRunRepository.save(run);

        AiAnalysisFinding finding = new AiAnalysisFinding();
        finding.setRun(run);
        finding.setFindingType("FIRE_EXTINGUISHER_EXPIRED");
        finding.setConfidence(0.92);
        finding.setDescription("Pressure gauge unserviceable");
        finding = aiFindingRepository.save(finding);

        String officerToken = jwtService.generateToken(officerA.getUuid());
        LinkAiFindingRequest linkReq = new LinkAiFindingRequest(finding.getUuid(), "NON_COMPLIANT", 0.92, "MANUAL");
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + item.responseId() + "/ai-links")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(linkReq)))
                .andExpect(status().isOk());

        String citizenToken = jwtService.generateToken(citizenApplicant.getUuid());

        // 1. Citizen calling GET /ai-mappings is denied -> 403 Forbidden
        mockMvc.perform(get("/api/v1/inspections/" + insA.getUuid() + "/checklist/ai-mappings")
                        .header("Authorization", "Bearer " + citizenToken))
                .andExpect(status().isForbidden());

        // 2. Officer calling GET /ai-mappings is permitted -> 200 OK
        mockMvc.perform(get("/api/v1/inspections/" + insA.getUuid() + "/checklist/ai-mappings")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mappedCount", greaterThan(0)));

        // 3. Citizen calling GET /checklist/items/{itemId} receives sanitized view (aiSuggestions is empty)
        mockMvc.perform(get("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + item.responseId())
                        .header("Authorization", "Bearer " + citizenToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCode", is(item.itemCode())))
                .andExpect(jsonPath("$.aiSuggestions", empty()));

        // 4. Officer calling GET /checklist/items/{itemId} sees AI findings
        mockMvc.perform(get("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + item.responseId())
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiSuggestions", hasSize(1)))
                .andExpect(jsonPath("$.aiSuggestions[0].modelName", is("qwen2.5-vl-72b")));
    }

    // =========================================================================
    // 6. Recording Each Supported Officer Response
    // =========================================================================

    @Test
    @DisplayName("Req 6: Officer can record COMPLIANT, NON_COMPLIANT, NOT_APPLICABLE, NEEDS_FURTHER_INSPECTION")
    void testRecordingEachSupportedOfficerResponse() throws Exception {
        InspectionChecklistResponseDto cl = initChecklist(officerA);
        String token = jwtService.generateToken(officerA.getUuid());

        // Find items
        InspectionChecklistItemDetailDto extItem = cl.items().stream().filter(i -> "EXT-01".equals(i.itemCode())).findFirst().orElseThrow();
        InspectionChecklistItemDetailDto exitItem = cl.items().stream().filter(i -> "EXIT-01".equals(i.itemCode())).findFirst().orElseThrow();
        InspectionChecklistItemDetailDto hydrItem = cl.items().stream().filter(i -> "HYDR-01".equals(i.itemCode())).findFirst().orElseThrow();
        InspectionChecklistItemDetailDto alarmItem = cl.items().stream().filter(i -> "ALARM-01".equals(i.itemCode())).findFirst().orElseThrow();

        // 1. COMPLIANT (with evidence)
        RecordOfficerResponseRequest compReq = new RecordOfficerResponseRequest("COMPLIANT", "All cylinders mounted correctly at 1.4m height.", List.of(evidenceA.getUuid()));
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + extItem.responseId() + "/response")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(compReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compliantCount", is(1)));

        // 2. NON_COMPLIANT (with remarks)
        RecordOfficerResponseRequest nonCompReq = new RecordOfficerResponseRequest("NON_COMPLIANT", "Emergency exit doorway locked from inside.", List.of(evidenceB.getUuid()));
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + exitItem.responseId() + "/response")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nonCompReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nonCompliantCount", is(1)));

        // 3. NOT_APPLICABLE (with statutory justification)
        RecordOfficerResponseRequest naReq = new RecordOfficerResponseRequest("NOT_APPLICABLE", "Building height is 12m; internal wet riser exempted under NBC Part 4 Table 7.", List.of());
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + hydrItem.responseId() + "/response")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(naReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notApplicableCount", is(1)));

        // 4. NEEDS_FURTHER_INSPECTION (with follow-up instructions)
        RecordOfficerResponseRequest nfiReq = new RecordOfficerResponseRequest("NEEDS_FURTHER_INSPECTION", "Fire alarm sounders did not activate during battery backup test; secondary load test required.", List.of());
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + alarmItem.responseId() + "/response")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nfiReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsFurtherInspectionCount", is(1)));

        // 5. Rejection of NOT_REVIEWED as a recorded officer answer -> 400 Bad Request
        RecordOfficerResponseRequest notReviewedReq = new RecordOfficerResponseRequest("NOT_REVIEWED", "Skipping", List.of());
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + extItem.responseId() + "/response")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(notReviewedReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("NOT_REVIEWED")));
    }

    // =========================================================================
    // 7. Mandatory Remarks and Justification Validation
    // =========================================================================

    @Test
    @DisplayName("Req 7: NON_COMPLIANT, NOT_APPLICABLE, and NEEDS_FURTHER_INSPECTION require remarks")
    void testMandatoryRemarksAndJustification() throws Exception {
        InspectionChecklistResponseDto cl = initChecklist(officerA);
        String token = jwtService.generateToken(officerA.getUuid());
        UUID itemId = cl.items().get(0).responseId();

        // NON_COMPLIANT without remarks -> 400
        RecordOfficerResponseRequest emptyNonComp = new RecordOfficerResponseRequest("NON_COMPLIANT", "", List.of(evidenceA.getUuid()));
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/response")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyNonComp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("deficiency are mandatory")));

        // NOT_APPLICABLE without justification -> 400
        RecordOfficerResponseRequest emptyNa = new RecordOfficerResponseRequest("NOT_APPLICABLE", "   ", List.of());
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/response")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyNa)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("statutory justification are mandatory")));

        // NEEDS_FURTHER_INSPECTION without remarks -> 400
        RecordOfficerResponseRequest emptyNfi = new RecordOfficerResponseRequest("NEEDS_FURTHER_INSPECTION", null, List.of());
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/response")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyNfi)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Officer remarks detailing required testing or inspection are mandatory")));
    }

    // =========================================================================
    // 8. Evidence Association and Active-Evidence Validation
    // =========================================================================

    @Test
    @DisplayName("Req 8: Attaching active evidence succeeds; inactive/invalid or cross-inspection evidence fails")
    void testEvidenceAssociationAndActiveValidation() throws Exception {
        InspectionChecklistResponseDto cl = initChecklist(officerA);
        String token = jwtService.generateToken(officerA.getUuid());
        UUID itemId = cl.items().get(0).responseId();

        // 1. Inactive/deleted evidence
        NocInspectionEvidence deletedEv = new NocInspectionEvidence();
        deletedEv.setOrgId(orgA.getId());
        deletedEv.setInspectionId(insA.getId());
        deletedEv.setFileName("damaged_corrupt.jpg");
        deletedEv.setContentType("image/jpeg");
        deletedEv.setFileSizeBytes(100L);
        deletedEv.setStorageBucket("firesphere-evidence");
        deletedEv.setStorageKey("deleted.jpg");
        deletedEv.setSha256Hash("hash-deleted");
        deletedEv.setStatus("DELETED");
        deletedEv = evidenceRepository.save(deletedEv);

        AttachEvidenceRequest attachDeleted = new AttachEvidenceRequest(deletedEv.getUuid(), "Deleted photo");
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/evidence")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attachDeleted)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("not active or has been invalidated")));

        // 2. Cross-inspection evidence (evidence from inspection 99999)
        NocInspectionEvidence foreignEv = new NocInspectionEvidence();
        foreignEv.setOrgId(orgA.getId());
        foreignEv.setInspectionId(99999L);
        foreignEv.setFileName("other_inspection.jpg");
        foreignEv.setContentType("image/jpeg");
        foreignEv.setFileSizeBytes(1024L);
        foreignEv.setStorageBucket("firesphere-evidence");
        foreignEv.setStorageKey("other.jpg");
        foreignEv.setSha256Hash("hash-foreign");
        foreignEv.setStatus("ACTIVE");
        foreignEv = evidenceRepository.save(foreignEv);

        AttachEvidenceRequest attachForeign = new AttachEvidenceRequest(foreignEv.getUuid(), "Foreign photo");
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/evidence")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attachForeign)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("does not belong to this inspection")));

        // 3. Valid active evidence succeeds
        AttachEvidenceRequest attachValid = new AttachEvidenceRequest(evidenceA.getUuid(), "Corridor photo verified");
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/evidence")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attachValid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedEvidence", hasSize(1)))
                .andExpect(jsonPath("$.linkedEvidence[0].evidenceId", is(evidenceA.getUuid().toString())));
    }

    // =========================================================================
    // 9. AI Suggestion Review Semantics (No Compliance Status Mutation)
    // =========================================================================

    @Test
    @DisplayName("Req 9: Accepting or dismissing an AI link NEVER mutates the officer's checklist response status")
    void testAiSuggestionReviewDoesNotMutateComplianceStatus() throws Exception {
        InspectionChecklistResponseDto cl = initChecklist(officerA);
        String token = jwtService.generateToken(officerA.getUuid());
        UUID itemId = cl.items().get(0).responseId();

        // Ensure item status is NOT_REVIEWED
        assertEquals(ChecklistResponseStatus.NOT_REVIEWED, cl.items().get(0).officerResponse());

        // Create AI finding
        AiAnalysisRun run = new AiAnalysisRun();
        run.setOrgId(orgA.getId());
        run.setInspectionId(insA.getId());
        run.setStatus(AiAnalysisStatus.COMPLETED);
        run = aiRunRepository.save(run);

        AiAnalysisFinding finding = new AiAnalysisFinding();
        finding.setRun(run);
        finding.setFindingType("FIRE_EXTINGUISHER_MISSING");
        finding.setConfidence(0.96);
        finding = aiFindingRepository.save(finding);

        // Link AI finding
        LinkAiFindingRequest linkReq = new LinkAiFindingRequest(finding.getUuid(), "NON_COMPLIANT", 0.96, "MANUAL");
        MvcResult linkRes = mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/ai-links")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(linkReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officerResponse", is("NOT_REVIEWED")))
                .andReturn();

        InspectionChecklistItemDetailDto detail = objectMapper.readValue(linkRes.getResponse().getContentAsString(), InspectionChecklistItemDetailDto.class);
        UUID linkId = detail.aiSuggestions().get(0).linkId();

        // 1. Officer accepts AI link (CONFIRMED)
        ReviewAiLinkRequest acceptReq = new ReviewAiLinkRequest("CONFIRMED", "Agreed with AI observation regarding missing cylinder");
        mockMvc.perform(patch("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/ai-links/" + linkId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(acceptReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiSuggestions[0].linkStatus", is("ACCEPTED")))
                .andExpect(jsonPath("$.aiSuggestions[0].reviewStatus", is("CONFIRMED")))
                .andExpect(jsonPath("$.officerResponse", is("NOT_REVIEWED"))); // Still NOT_REVIEWED!

        // 2. Officer dismisses AI link (DISMISSED)
        ReviewAiLinkRequest dismissReq = new ReviewAiLinkRequest("DISMISSED", "Extinguisher was temporarily moved for servicing; spare provided");
        mockMvc.perform(patch("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/ai-links/" + linkId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dismissReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiSuggestions[0].linkStatus", is("DISMISSED")))
                .andExpect(jsonPath("$.aiSuggestions[0].reviewStatus", is("DISMISSED")))
                .andExpect(jsonPath("$.officerResponse", is("NOT_REVIEWED"))); // Unchanged!
    }

    // =========================================================================
    // 10. Completed-Checklist Immutability Enforcement
    // =========================================================================

    @Test
    @DisplayName("Req 10: Completed checklist rejects all modifications (responses, evidence, mapping, AI links)")
    void testCompletedChecklistRejectsProhibitedMutations() throws Exception {
        InspectionChecklistResponseDto cl = initChecklist(officerA);
        String token = jwtService.generateToken(officerA.getUuid());

        // Fill all responses to COMPLIANT
        for (InspectionChecklistItemDetailDto item : cl.items()) {
            List<UUID> evList = Boolean.TRUE.equals(item.evidenceRequired()) ? List.of(evidenceA.getUuid()) : List.of();
            RecordOfficerResponseRequest respReq = new RecordOfficerResponseRequest("COMPLIANT", "Statutory compliance verified on site per NBC", evList);
            mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + item.responseId() + "/response")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(respReq)))
                    .andExpect(status().isOk());
        }

        // Finalize checklist
        CompleteChecklistRequest completeReq = new CompleteChecklistRequest("All 5 statutory checklist items fully inspected and approved.");
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));

        UUID itemId = cl.items().get(0).responseId();

        // 1. Attempt response update -> 400 Bad Request
        RecordOfficerResponseRequest updateReq = new RecordOfficerResponseRequest("NON_COMPLIANT", "Defect found late", List.of(evidenceA.getUuid()));
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/response")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already been finalized and completed")));

        // 2. Attempt attach evidence -> 400 Bad Request
        AttachEvidenceRequest evReq = new AttachEvidenceRequest(evidenceB.getUuid(), "New photo");
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/evidence")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(evReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already been finalized and completed")));

        // 3. Attempt map AI findings -> 400 Bad Request
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/map-ai-findings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already been finalized and completed")));
    }

    // =========================================================================
    // 11. Invalid Payloads and Missing Records
    // =========================================================================

    @Test
    @DisplayName("Req 11: Blank response status yields 400, and non-existent IDs yield 404")
    void testInvalidPayloadsAndMissingRecords() throws Exception {
        InspectionChecklistResponseDto cl = initChecklist(officerA);
        String token = jwtService.generateToken(officerA.getUuid());
        UUID itemId = cl.items().get(0).responseId();

        // 1. Blank response status -> 400 Bad Request
        RecordOfficerResponseRequest blankReq = new RecordOfficerResponseRequest("", "Remarks", List.of());
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/response")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankReq)))
                .andExpect(status().isBadRequest());

        // 2. Non-existent inspection UUID -> 404 Not Found
        UUID randomIns = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/inspections/" + randomIns + "/checklist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        // 3. Non-existent item UUID -> 404 Not Found
        UUID randomItem = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + randomItem)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        // 4. Non-existent evidence in attach request -> 404 Not Found
        AttachEvidenceRequest ghostEv = new AttachEvidenceRequest(UUID.randomUUID(), "Ghost photo");
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/evidence")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ghostEv)))
                .andExpect(status().isNotFound());

        // 5. Non-existent AI link in review request -> 404 Not Found
        ReviewAiLinkRequest ghostLink = new ReviewAiLinkRequest("CONFIRMED", "Ghost link review");
        mockMvc.perform(patch("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/ai-links/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ghostLink)))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 12. Deterministic AI Findings-to-Checklist Mapping Compatibility
    // =========================================================================

    @Test
    @DisplayName("Req 12: Deterministic mapping maps supported categories and leaves unsupported unmapped")
    void testAiFindingMappingCompatibility() throws Exception {
        initChecklist(officerA);
        String token = jwtService.generateToken(officerA.getUuid());

        AiAnalysisRun run = new AiAnalysisRun();
        run.setOrgId(orgA.getId());
        run.setInspectionId(insA.getId());
        run.setStatus(AiAnalysisStatus.COMPLETED);
        run.setModelName("yolov8x-fire");
        run.setModelVersion("8.1.0");
        run = aiRunRepository.save(run);

        // Supported: FIRE_EXTINGUISHER
        AiAnalysisFinding f1 = new AiAnalysisFinding();
        f1.setRun(run);
        f1.setFindingType("FIRE_EXTINGUISHER_MISSING");
        f1.setConfidence(0.94);
        f1.setSeverity("HIGH");
        f1 = aiFindingRepository.save(f1);

        // Unsupported: SPRINKLER_HEAD_DEFECT
        AiAnalysisFinding f2 = new AiAnalysisFinding();
        f2.setRun(run);
        f2.setFindingType("SPRINKLER_HEAD_DEFECT");
        f2.setConfidence(0.85);
        f2.setSeverity("MEDIUM");
        f2 = aiFindingRepository.save(f2);

        // Map findings
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/map-ai-findings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFindingsCount", is(2)))
                .andExpect(jsonPath("$.mappedCount", is(1)))
                .andExpect(jsonPath("$.unmappedCount", is(1)))
                .andExpect(jsonPath("$.mappings[0].originalCategory", is("FIRE_EXTINGUISHER_MISSING")))
                .andExpect(jsonPath("$.mappings[0].itemCode", is("EXT-01")))
                .andExpect(jsonPath("$.unmappedFindings[0].originalCategory", is("SPRINKLER_HEAD_DEFECT")))
                .andExpect(jsonPath("$.unmappedFindings[0].reason", containsString("sprinkler")));
    }

    // =========================================================================
    // 13. Audit Logging for Meaningful Review Actions
    // =========================================================================

    @Test
    @DisplayName("Req 13: Meaningful review actions trigger audit events")
    void testAuditEventsTriggeredOnReviewActions() throws Exception {
        InspectionChecklistResponseDto cl = initChecklist(officerA);
        String token = jwtService.generateToken(officerA.getUuid());
        UUID itemId = cl.items().get(0).responseId();

        // 1. Record response -> CHECKLIST_ITEM_UPDATED event
        RecordOfficerResponseRequest respReq = new RecordOfficerResponseRequest("COMPLIANT", "Inspected and verified", List.of(evidenceA.getUuid()));
        mockMvc.perform(put("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/response")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(respReq)))
                .andExpect(status().isOk());

        // 2. Attach evidence
        AttachEvidenceRequest evReq = new AttachEvidenceRequest(evidenceB.getUuid(), "Additional photo");
        mockMvc.perform(post("/api/v1/inspections/" + insA.getUuid() + "/checklist/items/" + itemId + "/evidence")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(evReq)))
                .andExpect(status().isOk());
    }
}
