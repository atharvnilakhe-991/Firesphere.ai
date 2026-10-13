package com.firesphere.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firesphere.backend.auth.entity.User;
import com.firesphere.backend.auth.repository.UserRepository;
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
public class SecurityAuditTests {

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

    private User citizenA;
    private User officerA;
    private User citizenB;
    private Organization orgA;
    private Organization orgB;

    private NocApplication nocDraftA;
    private NocApplication nocDraftB;

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
        officerA = createUser("officera@test.com", Role.OFFICER, orgA);
        citizenB = createUser("citizenb@test.com", Role.CITIZEN, orgB);

        nocDraftA = createNoc("Draft NOC A", NocStatus.DRAFT, citizenA, orgA);
        nocDraftB = createNoc("Draft NOC B", NocStatus.DRAFT, citizenB, orgB);
    }

    private User createUser(String email, Role role, Organization org) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("pass");
        user.setStatus("ACTIVE");
        user.setRole(role.name());
        user.setOrganizationId(org.getId());
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return userRepository.save(user);
    }

    private NocApplication createNoc(String title, NocStatus status, User user, Organization org) {
        NocApplication noc = new NocApplication();
        noc.setApplicantId(user.getId());
        noc.setOrganizationId(org.getId());
        noc.setTitle(title);
        noc.setBuildingName("B1");
        noc.setAddress("A1");
        noc.setStatus(status);
        return nocRepository.save(noc);
    }

    @Test
    void missingTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/noc/applications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/noc/applications")
                .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crossTenantNocAccessDenied() throws Exception {
        String tokenA = jwtService.generateToken(citizenA.getId());

        mockMvc.perform(get("/api/v1/noc/applications/" + nocDraftB.getId())
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound()); // Or Forbidden depending on implementation, but typically not found since it doesn't match org
    }

    @Test
    void massAssignmentIgnored() throws Exception {
        String token = jwtService.generateToken(citizenA.getId());
        
        // Attempt to update draft and inject a new status or organizationId
        String body = """
            {
              "title": "Updated Title",
              "status": "APPROVED",
              "organizationId": "123e4567-e89b-12d3-a456-426614174000"
            }
        """;

        mockMvc.perform(patch("/api/v1/noc/applications/" + nocDraftA.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Updated Title")))
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.organizationId", is(orgA.getId().toString())));
    }
}
