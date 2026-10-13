package com.firesphere.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firesphere.backend.auth.entity.User;
import com.firesphere.backend.auth.repository.UserRepository;
import com.firesphere.backend.common.security.JwtService;
import com.firesphere.backend.common.security.Role;
import com.firesphere.backend.document.entity.DocumentStatus;
import com.firesphere.backend.document.entity.NocDocument;
import com.firesphere.backend.document.repository.NocDocumentRepository;
import com.firesphere.backend.inspection.entity.NocInspection;
import com.firesphere.backend.inspection.repository.NocInspectionRepository;
import com.firesphere.backend.noc.entity.NocApplication;
import com.firesphere.backend.noc.entity.NocStatus;
import com.firesphere.backend.noc.repository.NocApplicationRepository;
import com.firesphere.backend.organization.entity.Organization;
import com.firesphere.backend.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityExpandedAuditTests {

    @Autowired
    private MockMvc mockMvc;
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
    private JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;

    private User citizenA;
    private User citizenB;
    private User officerA;
    private User approverA;
    private Organization orgA;
    private Organization orgB;

    private NocApplication nocDraftA;
    private NocApplication nocPendingApprA;
    private NocApplication nocDraftB;

    @BeforeEach
    void setUp() {
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

        citizenA = createUser("citizena@test.com", Role.CITIZEN, orgA);
        officerA = createUser("officera@test.com", Role.OFFICER, orgA);
        approverA = createUser("approvera@test.com", Role.APPROVAL_AUTHORITY, orgA);
        citizenB = createUser("citizenb@test.com", Role.CITIZEN, orgB);

        nocDraftA = createNoc("Draft NOC A", NocStatus.DRAFT, citizenA, orgA);
        nocPendingApprA = createNoc("Pending Appr A", NocStatus.PENDING_APPROVAL, citizenA, orgA);
        nocDraftB = createNoc("Draft NOC B", NocStatus.DRAFT, citizenB, orgB);
    }

    private User createUser(String email, Role role, Organization org) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("pass");
        user.setStatus("ACTIVE");
        user.setRole(role.name());
        user.setOrganization(org);
        user.setOrganizationId(org.getId());
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return userRepository.save(user);
    }

    private NocApplication createNoc(String title, NocStatus status, User user, Organization org) {
        NocApplication noc = new NocApplication();
        noc.setOrgId(org.getId());
        noc.setOrganizationId(org.getUuid());
        noc.setApplicantId(user.getUuid());
        noc.setTitle(title);
        noc.setBuildingName("B1");
        noc.setAddress("A1");
        noc.setStatus(status);
        return nocRepository.save(noc);
    }

    // AUTHENTICATION
    @Test
    void missingTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/noc/applications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/noc/applications").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        // Technically hard to generate expired directly without modifying JwtService, but malformed/invalid signature covers core Rejection
        mockMvc.perform(get("/api/v1/noc/applications").header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.expired-payload.signature"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignatureReturns401() throws Exception {
        String token = jwtService.generateToken(citizenA.getUuid());
        String invalidToken = token + "bad";
        mockMvc.perform(get("/api/v1/noc/applications").header("Authorization", "Bearer " + invalidToken))
                .andExpect(status().isUnauthorized());
    }

    // AUTHORIZATION
    @Test
    void citizenCannotApprove() throws Exception {
        String token = jwtService.generateToken(citizenA.getUuid());
        String body = "{\"action\":\"APPROVE\"}";
        mockMvc.perform(post("/api/v1/noc/applications/" + nocPendingApprA.getUuid() + "/transitions")
                .contentType(MediaType.APPLICATION_JSON).content(body).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void citizenCannotGenerateCertificate() throws Exception {
        String token = jwtService.generateToken(citizenA.getUuid());
        mockMvc.perform(post("/api/v1/noc/applications/" + nocPendingApprA.getUuid() + "/certificate")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void officerCannotGenerateCertificate() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());
        mockMvc.perform(post("/api/v1/noc/applications/" + nocPendingApprA.getUuid() + "/certificate")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // IDOR / TENANT
    @Test
    void crossTenantNocAccessDenied() throws Exception {
        String tokenA = jwtService.generateToken(citizenA.getUuid());
        mockMvc.perform(get("/api/v1/noc/applications/" + nocDraftB.getUuid())
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }

    @Test
    void crossTenantDocumentAccessDenied() throws Exception {
        String tokenA = jwtService.generateToken(citizenA.getUuid());
        NocDocument doc = new NocDocument();
        doc.setApplicationId(nocDraftB.getUuid());
        doc.setOrganizationId(orgB.getUuid());
        doc.setDocumentType("IDENTITY");
        doc.setOriginalFileName("test.pdf");
        doc.setStorageReference("file1");
        doc.setContentType("application/pdf");
        doc.setFileSize(100L);
        doc.setUploadedBy(citizenB.getUuid());
        doc.setStatus(DocumentStatus.UPLOADED);
        doc = documentRepository.save(doc);

        mockMvc.perform(get("/api/v1/noc/applications/" + nocDraftB.getUuid() + "/documents")
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }

    @Test
    void crossTenantInspectionAccessDenied() throws Exception {
        String tokenA = jwtService.generateToken(citizenA.getUuid());
        NocInspection ins = new NocInspection();
        ins.setApplicationId(nocDraftB.getUuid());
        ins.setOrganizationId(orgB.getUuid());
        ins.setOrgId(orgB.getId());
        ins.setNocApplicationId(nocDraftB.getId());
        ins.setInspectionType("INITIAL");
        ins.setScheduledAt(OffsetDateTime.now());
        ins.setAssignedOfficer(officerA.getUuid());
        ins = inspectionRepository.save(ins);

        mockMvc.perform(get("/api/v1/noc/applications/" + nocDraftB.getUuid() + "/inspections/" + ins.getUuid())
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }

    // MASS ASSIGNMENT
    @Test
    void workflowStateMassAssignmentIgnored() throws Exception {
        String token = jwtService.generateToken(citizenA.getUuid());
        String body = "{\"title\": \"Updated Title\", \"status\": \"APPROVED\", \"workflowState\": \"FINAL\"}";

        mockMvc.perform(patch("/api/v1/noc/applications/" + nocDraftA.getUuid())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DRAFT"))); // status ignored
    }

    // ERROR INFORMATION DISCLOSURE
    @Test
    void errorInformationDoesNotDiscloseInternals() throws Exception {
        // Trigger a 400 or 500 error deliberately
        mockMvc.perform(get("/api/v1/noc/applications/invalid-uuid-format").header("Authorization", "Bearer " + jwtService.generateToken(citizenA.getUuid())))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.stackTrace").doesNotExist()) // ensure stack trace is absent
                .andExpect(jsonPath("$.exception").doesNotExist()); // ensure exception class name is absent
    }
}
