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

        citizenA = new User();
        citizenA.setEmail("citizena@test.com");
        citizenA.setPassword("pass");
        citizenA.setStatus("ACTIVE");
        citizenA.setRole(Role.CITIZEN.name());
        citizenA.setOrganizationId(orgA.getId());
        citizenA.setCreatedAt(OffsetDateTime.now());
        citizenA.setUpdatedAt(OffsetDateTime.now());
        citizenA = userRepository.save(citizenA);

        citizenB = new User();
        citizenB.setEmail("citizenb@test.com");
        citizenB.setPassword("pass");
        citizenB.setStatus("ACTIVE");
        citizenB.setRole(Role.CITIZEN.name());
        citizenB.setOrganizationId(orgB.getId());
        citizenB.setCreatedAt(OffsetDateTime.now());
        citizenB.setUpdatedAt(OffsetDateTime.now());
        citizenB = userRepository.save(citizenB);

        adminB = new User();
        adminB.setEmail("adminb@test.com");
        adminB.setPassword("pass");
        adminB.setStatus("ACTIVE");
        adminB.setRole(Role.ADMIN.name());
        adminB.setOrganizationId(orgB.getId());
        adminB.setCreatedAt(OffsetDateTime.now());
        adminB.setUpdatedAt(OffsetDateTime.now());
        adminB = userRepository.save(adminB);

        nocDraftA = new NocApplication();
        nocDraftA.setApplicantId(citizenA.getId());
        nocDraftA.setOrganizationId(orgA.getId());
        nocDraftA.setTitle("Test NOC Draft");
        nocDraftA.setBuildingName("Building A");
        nocDraftA.setAddress("123 Street");
        nocDraftA.setStatus(NocStatus.DRAFT);
        nocDraftA = nocRepository.save(nocDraftA);

        nocSubmittedA = new NocApplication();
        nocSubmittedA.setApplicantId(citizenA.getId());
        nocSubmittedA.setOrganizationId(orgA.getId());
        nocSubmittedA.setTitle("Test NOC Submitted");
        nocSubmittedA.setBuildingName("Building A2");
        nocSubmittedA.setAddress("124 Street");
        nocSubmittedA.setStatus(NocStatus.SUBMITTED);
        nocSubmittedA = nocRepository.save(nocSubmittedA);
    }

    @Test
    void citizenCanCreateNoc() throws Exception {
        String token = jwtService.generateToken(citizenA.getId());
        String body = "{\"title\":\"New NOC\",\"buildingName\":\"Building B\",\"address\":\"125 Street\"}";

        mockMvc.perform(post("/api/v1/noc/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("New NOC")))
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.organizationId", is(orgA.getId().toString())))
                .andExpect(jsonPath("$.applicantId", is(citizenA.getId().toString())));

        verify(auditLogger).log(eq(com.firesphere.backend.common.audit.AuditEventType.NOC_CREATED), any(), anyString());
    }

    @Test
    void citizenCanReadOwnNoc() throws Exception {
        String token = jwtService.generateToken(citizenA.getId());
        
        mockMvc.perform(get("/api/v1/noc/applications/" + nocDraftA.getId())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Test NOC Draft")));
    }

    @Test
    void citizenCannotReadOtherCitizenNocSameOrg() throws Exception {
        // Create citizen A2 in Org A
        User citizenA2 = new User();
        citizenA2.setEmail("citizena2@test.com");
        citizenA2.setPassword("pass");
        citizenA2.setStatus("ACTIVE");
        citizenA2.setRole(Role.CITIZEN.name());
        citizenA2.setOrganizationId(orgA.getId());
        citizenA2.setCreatedAt(OffsetDateTime.now());
        citizenA2.setUpdatedAt(OffsetDateTime.now());
        citizenA2 = userRepository.save(citizenA2);

        String token = jwtService.generateToken(citizenA2.getId());

        mockMvc.perform(get("/api/v1/noc/applications/" + nocDraftA.getId())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void citizenCannotReadCrossTenantNoc() throws Exception {
        String token = jwtService.generateToken(citizenB.getId());

        mockMvc.perform(get("/api/v1/noc/applications/" + nocDraftA.getId())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()); // NotFound because of tenant scoping
    }

    @Test
    void adminCannotReadCrossTenantNoc() throws Exception {
        String token = jwtService.generateToken(adminB.getId());

        mockMvc.perform(get("/api/v1/noc/applications/" + nocDraftA.getId())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()); // NotFound because of tenant scoping
    }

    @Test
    void citizenCanUpdateOwnDraft() throws Exception {
        String token = jwtService.generateToken(citizenA.getId());
        String body = "{\"title\":\"Updated NOC Draft\"}";

        mockMvc.perform(patch("/api/v1/noc/applications/" + nocDraftA.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Updated NOC Draft")));

        verify(auditLogger).log(eq(com.firesphere.backend.common.audit.AuditEventType.NOC_UPDATED), eq(nocDraftA.getId()), anyString());
    }

    @Test
    void citizenCannotUpdateSubmittedNoc() throws Exception {
        String token = jwtService.generateToken(citizenA.getId());
        String body = "{\"title\":\"Attempt to Update Submitted NOC\"}";

        mockMvc.perform(patch("/api/v1/noc/applications/" + nocSubmittedA.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void citizenCanSubmitDraftNoc() throws Exception {
        String token = jwtService.generateToken(citizenA.getId());

        mockMvc.perform(post("/api/v1/noc/applications/" + nocDraftA.getId() + "/submit")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState", is("SUBMITTED")));

        verify(auditLogger).log(eq(com.firesphere.backend.common.audit.AuditEventType.NOC_WORKFLOW_TRANSITION), eq(nocDraftA.getId()), anyString());
    }

    @Test
    void citizenCannotUpdateOtherCitizenDraft() throws Exception {
        User citizenA2 = new User();
        citizenA2.setEmail("citizena2_update@test.com");
        citizenA2.setPassword("pass");
        citizenA2.setStatus("ACTIVE");
        citizenA2.setRole(Role.CITIZEN.name());
        citizenA2.setOrganizationId(orgA.getId());
        citizenA2.setCreatedAt(OffsetDateTime.now());
        citizenA2.setUpdatedAt(OffsetDateTime.now());
        citizenA2 = userRepository.save(citizenA2);

        String token = jwtService.generateToken(citizenA2.getId());
        String body = "{\"title\":\"Hijack\"}";

        mockMvc.perform(patch("/api/v1/noc/applications/" + nocDraftA.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void citizenCannotSubmitOtherCitizenDraft() throws Exception {
        User citizenA3 = new User();
        citizenA3.setEmail("citizena3_submit@test.com");
        citizenA3.setPassword("pass");
        citizenA3.setStatus("ACTIVE");
        citizenA3.setRole(Role.CITIZEN.name());
        citizenA3.setOrganizationId(orgA.getId());
        citizenA3.setCreatedAt(OffsetDateTime.now());
        citizenA3.setUpdatedAt(OffsetDateTime.now());
        citizenA3 = userRepository.save(citizenA3);

        String token = jwtService.generateToken(citizenA3.getId());

        mockMvc.perform(post("/api/v1/noc/applications/" + nocDraftA.getId() + "/submit")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSeesAllNocsInSameOrg() throws Exception {
        // Admin in org A
        User adminA = new User();
        adminA.setEmail("admina@test.com");
        adminA.setPassword("pass");
        adminA.setStatus("ACTIVE");
        adminA.setRole(Role.ADMIN.name());
        adminA.setOrganizationId(orgA.getId());
        adminA.setCreatedAt(OffsetDateTime.now());
        adminA.setUpdatedAt(OffsetDateTime.now());
        adminA = userRepository.save(adminA);

        String token = jwtService.generateToken(adminA.getId());

        mockMvc.perform(get("/api/v1/noc/applications")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }
}
