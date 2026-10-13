package com.firesphere.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firesphere.backend.common.security.JwtService;
import com.firesphere.backend.common.security.Role;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase7WorkflowTests {

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
    private ObjectMapper objectMapper;

    private User citizenA;
    private User officerA;
    private User adminA;
    private Organization orgA;

    @BeforeEach
    void setup() {
        nocRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        orgA = new Organization();
        orgA.setName("Org A");
        orgA = organizationRepository.save(orgA);

        citizenA = createUser("citizenA@example.com", Role.CITIZEN);
        officerA = createUser("officerA@example.com", Role.OFFICER);
        adminA = createUser("adminA@example.com", Role.ADMIN);
    }

    private User createUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("pass");
        user.setStatus("ACTIVE");
        user.setRole(role.name());
        user.setOrganization(orgA);
        user.setOrganizationId(orgA.getId());
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return userRepository.save(user);
    }

    private NocApplication createNoc(String title, NocStatus status) {
        NocApplication noc = new NocApplication();
        noc.setOrgId(orgA.getId());
        noc.setOrganizationId(orgA.getUuid());
        noc.setApplicantId(citizenA.getUuid());
        noc.setTitle(title);
        noc.setBuildingName("RESIDENTIAL");
        noc.setAddress("A1");
        noc.setStatus(status);
        return nocRepository.save(noc);
    }

    @Test
    void officerCanStartDocumentVerification() throws Exception {
        NocApplication noc = createNoc("App1", NocStatus.SUBMITTED);
        String token = jwtService.generateToken(officerA.getUuid());
        
        mockMvc.perform(post("/api/v1/noc/applications/" + noc.getUuid() + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"START_DOCUMENT_VERIFICATION\"}")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState", is("DOCUMENT_VERIFICATION")));
    }

    @Test
    void citizenCannotStartDocumentVerification() throws Exception {
        NocApplication noc = createNoc("App1", NocStatus.SUBMITTED);
        String token = jwtService.generateToken(citizenA.getUuid());
        
        mockMvc.perform(post("/api/v1/noc/applications/" + noc.getUuid() + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"START_DOCUMENT_VERIFICATION\"}")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
    
    @Test
    void officerCanCompleteDocumentVerification() throws Exception {
        NocApplication noc = createNoc("App1", NocStatus.DOCUMENT_VERIFICATION);
        String token = jwtService.generateToken(officerA.getUuid());
        
        mockMvc.perform(post("/api/v1/noc/applications/" + noc.getUuid() + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"COMPLETE_DOCUMENT_VERIFICATION\"}")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState", is("DOCUMENTS_VERIFIED")));
    }
    
    @Test
    void officerCanGenerateChecklist() throws Exception {
        NocApplication noc = createNoc("App1", NocStatus.DOCUMENTS_VERIFIED);
        String token = jwtService.generateToken(officerA.getUuid());
        
        mockMvc.perform(post("/api/v1/noc/applications/" + noc.getUuid() + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"GENERATE_CHECKLIST\"}")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState", is("CHECKLIST_GENERATED")));
    }
    
    @Test
    void officerCanScheduleInspection() throws Exception {
        NocApplication noc = createNoc("App1", NocStatus.CHECKLIST_GENERATED);
        String token = jwtService.generateToken(officerA.getUuid());
        
        mockMvc.perform(post("/api/v1/noc/applications/" + noc.getUuid() + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"SCHEDULE_INSPECTION\"}")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState", is("INSPECTION_SCHEDULED")));
    }
    
    @Test
    void officerCanStartInspection() throws Exception {
        NocApplication noc = createNoc("App1", NocStatus.INSPECTION_SCHEDULED);
        String token = jwtService.generateToken(officerA.getUuid());
        
        mockMvc.perform(post("/api/v1/noc/applications/" + noc.getUuid() + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"START_INSPECTION\"}")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState", is("INSPECTION_IN_PROGRESS")));
    }
    
    @Test
    void officerCanCompleteInspection() throws Exception {
        NocApplication noc = createNoc("App1", NocStatus.INSPECTION_IN_PROGRESS);
        String token = jwtService.generateToken(officerA.getUuid());
        
        mockMvc.perform(post("/api/v1/noc/applications/" + noc.getUuid() + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"COMPLETE_INSPECTION\"}")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState", is("INSPECTION_COMPLETED")));
    }
    
    @Test
    void officerCannotStartInspectionFromDraft() throws Exception {
        NocApplication noc = createNoc("App1", NocStatus.DRAFT);
        String token = jwtService.generateToken(officerA.getUuid());
        
        mockMvc.perform(post("/api/v1/noc/applications/" + noc.getUuid() + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"START_INSPECTION\"}")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approverApprovalRejectionMaintainsBoundary() throws Exception {
        NocApplication noc = createNoc("App1", NocStatus.INSPECTION_COMPLETED);
        String token = jwtService.generateToken(adminA.getUuid());
        
        mockMvc.perform(post("/api/v1/noc/applications/" + noc.getUuid() + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"SUBMIT_FOR_APPROVAL\"}")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void approverCannotRequestReinspectionSincePendingApprovalIsDeferred() throws Exception {
        NocApplication noc = createNoc("App1", NocStatus.PENDING_APPROVAL);
        String token = jwtService.generateToken(adminA.getUuid());
        
        mockMvc.perform(post("/api/v1/noc/applications/" + noc.getUuid() + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"REQUEST_REINSPECTION\"}")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
