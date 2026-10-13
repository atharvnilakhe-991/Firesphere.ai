package com.firesphere.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firesphere.backend.auth.entity.User;
import com.firesphere.backend.auth.repository.UserRepository;
import com.firesphere.backend.common.audit.SimpleAuditLogger;
import com.firesphere.backend.common.security.JwtService;
import com.firesphere.backend.common.security.Role;
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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class WorkflowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private NocApplicationRepository nocRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @SpyBean
    private SimpleAuditLogger auditLogger;

    private User citizenA;
    private User officerA;
    private User adminA;
    private User approverA;
    private Organization orgA;
    
    private NocApplication nocDraft;
    private NocApplication nocSubmitted;
    private NocApplication nocDocVerif;
    private NocApplication nocPendingAppr;

    @BeforeEach
    void setUp() {
        nocRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        orgA = new Organization();
        orgA.setName("Org A");
        orgA = organizationRepository.save(orgA);

        citizenA = createUser("citizena@test.com", Role.CITIZEN);
        officerA = createUser("officera@test.com", Role.OFFICER);
        adminA = createUser("admina@test.com", Role.ADMIN);
        approverA = createUser("approvera@test.com", Role.APPROVAL_AUTHORITY);

        nocDraft = createNoc("Draft NOC", NocStatus.DRAFT);
        nocSubmitted = createNoc("Submitted NOC", NocStatus.SUBMITTED);
        nocDocVerif = createNoc("Doc Verif NOC", NocStatus.DOCUMENT_VERIFICATION);
        nocPendingAppr = createNoc("Pending Appr NOC", NocStatus.PENDING_APPROVAL);
    }

    private User createUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("pass");
        user.setStatus("ACTIVE");
        user.setRole(role.name());
        user.setOrganizationId(orgA.getId());
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return userRepository.save(user);
    }

    private NocApplication createNoc(String title, NocStatus status) {
        NocApplication noc = new NocApplication();
        noc.setApplicantId(citizenA.getId());
        noc.setOrganizationId(orgA.getId());
        noc.setTitle(title);
        noc.setBuildingName("B1");
        noc.setAddress("A1");
        noc.setStatus(status);
        return nocRepository.save(noc);
    }

    @Test
    void citizenCanSubmitDraft() throws Exception {
        String token = jwtService.generateToken(citizenA.getId());
        String body = "{\"action\":\"SUBMIT\"}";

        mockMvc.perform(post("/api/v1/noc/applications/" + nocDraft.getId() + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState", is("SUBMITTED")));
    }

    @Test
    void officerCannotSubmitCitizenDraft() throws Exception {
        String token = jwtService.generateToken(officerA.getId());
        String body = "{\"action\":\"SUBMIT\"}";

        mockMvc.perform(post("/api/v1/noc/applications/" + nocDraft.getId() + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void officerCanStartDocVerification() throws Exception {
        String token = jwtService.generateToken(officerA.getId());
        String body = "{\"action\":\"START_DOCUMENT_VERIFICATION\"}";

        mockMvc.perform(post("/api/v1/noc/applications/" + nocSubmitted.getId() + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState", is("DOCUMENT_VERIFICATION")));
    }

    @Test
    void officerCanRequestCorrection() throws Exception {
        String token = jwtService.generateToken(officerA.getId());
        String body = "{\"action\":\"REQUEST_CORRECTION\"}";

        mockMvc.perform(post("/api/v1/noc/applications/" + nocDocVerif.getId() + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState", is("CORRECTION_REQUIRED")));
    }

    @Test
    void citizenCanResubmit() throws Exception {
        NocApplication nocCorr = createNoc("Corr NOC", NocStatus.CORRECTION_REQUIRED);
        
        String token = jwtService.generateToken(citizenA.getId());
        String body = "{\"action\":\"RESUBMIT\"}";

        mockMvc.perform(post("/api/v1/noc/applications/" + nocCorr.getId() + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState", is("DOCUMENT_VERIFICATION")));
    }

    @Test
    void approverCannotRequestReinspection() throws Exception {
        String token = jwtService.generateToken(approverA.getId());
        String body = "{\"action\":\"REQUEST_REINSPECTION\"}";

        mockMvc.perform(post("/api/v1/noc/applications/" + nocPendingAppr.getId() + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCannotBypassWorkflowBoundary() throws Exception {
        String token = jwtService.generateToken(adminA.getId());
        String body = "{\"action\":\"SUBMIT_FOR_APPROVAL\"}";

        mockMvc.perform(post("/api/v1/noc/applications/" + nocSubmitted.getId() + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
