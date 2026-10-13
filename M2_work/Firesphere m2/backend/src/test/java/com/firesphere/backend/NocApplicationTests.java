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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class NocApplicationTests {

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
    private User citizenB;
    private User adminB;
    private Organization orgA;
    private Organization orgB;
    private NocApplication nocDraftA;
    private NocApplication nocSubmittedA;

    @BeforeEach
    void setUp() {
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
        citizenB = createUser("citizenb@test.com", Role.CITIZEN, orgB);
        adminB = createUser("adminb@test.com", Role.ADMIN, orgB);

        nocDraftA = new NocApplication();
        nocDraftA.setOrgId(orgA.getId());
        nocDraftA.setOrganizationId(orgA.getUuid());
        nocDraftA.setApplicantId(citizenA.getUuid());
        nocDraftA.setTitle("Test NOC Draft");
        nocDraftA.setBuildingName("Building A");
        nocDraftA.setAddress("123 Street");
        nocDraftA.setStatus(NocStatus.DRAFT);
        nocDraftA = nocRepository.save(nocDraftA);

        nocSubmittedA = new NocApplication();
        nocSubmittedA.setOrgId(orgA.getId());
        nocSubmittedA.setOrganizationId(orgA.getUuid());
        nocSubmittedA.setApplicantId(citizenA.getUuid());
        nocSubmittedA.setTitle("Test NOC Submitted");
        nocSubmittedA.setBuildingName("Building A2");
        nocSubmittedA.setAddress("124 Street");
        nocSubmittedA.setStatus(NocStatus.SUBMITTED);
        nocSubmittedA = nocRepository.save(nocSubmittedA);
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

    @Test
    void citizenCanCreateNoc() throws Exception {
        String token = jwtService.generateToken(citizenA.getUuid());
        String body = "{\"title\":\"New NOC\",\"buildingName\":\"Building B\",\"address\":\"125 Street\"}";

        mockMvc.perform(post("/api/v1/noc/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("New NOC")))
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.organizationId", is(orgA.getUuid().toString())))
                .andExpect(jsonPath("$.applicantId", is(citizenA.getUuid().toString())));

        verify(auditLogger).log(eq(com.firesphere.backend.common.audit.AuditEventType.NOC_CREATED), any(), anyString());
    }

    @Test
    void citizenCanReadOwnNoc() throws Exception {
        String token = jwtService.generateToken(citizenA.getUuid());
        
        mockMvc.perform(get("/api/v1/noc/applications/" + nocDraftA.getUuid())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Test NOC Draft")));
    }

    @Test
    void citizenCannotReadOtherCitizenNocSameOrg() throws Exception {
        // Create citizen A2 in Org A
        User citizenA2 = createUser("citizena2@test.com", Role.CITIZEN, orgA);

        String token = jwtService.generateToken(citizenA2.getUuid());

        mockMvc.perform(get("/api/v1/noc/applications/" + nocDraftA.getUuid())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void citizenCannotReadCrossTenantNoc() throws Exception {
        String token = jwtService.generateToken(citizenB.getUuid());

        mockMvc.perform(get("/api/v1/noc/applications/" + nocDraftA.getUuid())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()); // Cross-tenant access prohibited
    }

    @Test
    void adminCannotReadCrossTenantNoc() throws Exception {
        String token = jwtService.generateToken(adminB.getUuid());

        mockMvc.perform(get("/api/v1/noc/applications/" + nocDraftA.getUuid())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()); // Cross-tenant access prohibited
    }

    @Test
    void citizenCanUpdateOwnDraft() throws Exception {
        String token = jwtService.generateToken(citizenA.getUuid());
        String body = "{\"title\":\"Updated NOC Draft\"}";

        mockMvc.perform(patch("/api/v1/noc/applications/" + nocDraftA.getUuid())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Updated NOC Draft")));

        verify(auditLogger).log(eq(com.firesphere.backend.common.audit.AuditEventType.NOC_UPDATED), eq(nocDraftA.getUuid()), anyString());
    }

    @Test
    void citizenCannotUpdateSubmittedNoc() throws Exception {
        String token = jwtService.generateToken(citizenA.getUuid());
        String body = "{\"title\":\"Attempt to Update Submitted NOC\"}";

        mockMvc.perform(patch("/api/v1/noc/applications/" + nocSubmittedA.getUuid())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void citizenCanSubmitDraftNoc() throws Exception {
        String token = jwtService.generateToken(citizenA.getUuid());

        mockMvc.perform(post("/api/v1/noc/applications/" + nocDraftA.getUuid() + "/submit")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState", is("SUBMITTED")));

        verify(auditLogger).log(eq(com.firesphere.backend.common.audit.AuditEventType.NOC_WORKFLOW_TRANSITION), eq(nocDraftA.getUuid()), anyString());
    }

    @Test
    void citizenCannotUpdateOtherCitizenDraft() throws Exception {
        User citizenA2 = createUser("citizena2_update@test.com", Role.CITIZEN, orgA);

        String token = jwtService.generateToken(citizenA2.getUuid());
        String body = "{\"title\":\"Hijack\"}";

        mockMvc.perform(patch("/api/v1/noc/applications/" + nocDraftA.getUuid())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void citizenCannotSubmitOtherCitizenDraft() throws Exception {
        User citizenA3 = createUser("citizena3_submit@test.com", Role.CITIZEN, orgA);

        String token = jwtService.generateToken(citizenA3.getUuid());

        mockMvc.perform(post("/api/v1/noc/applications/" + nocDraftA.getUuid() + "/submit")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSeesAllNocsInSameOrg() throws Exception {
        // Admin in org A
        User adminA = createUser("admina@test.com", Role.ADMIN, orgA);

        String token = jwtService.generateToken(adminA.getUuid());

        mockMvc.perform(get("/api/v1/noc/applications")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }
}
