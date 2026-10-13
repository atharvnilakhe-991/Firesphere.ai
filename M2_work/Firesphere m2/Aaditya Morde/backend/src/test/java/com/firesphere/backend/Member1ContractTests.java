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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Member1ContractTests {

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

    private Organization orgA;
    private User citizenA;
    private User officerA;
    private User adminA;
    private NocApplication nocA;

    @BeforeEach
    void setup() {
        nocRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        orgA = new Organization();
        orgA.setName("Org A");
        orgA = organizationRepository.save(orgA);

        citizenA = createUser("citizen@example.com", Role.CITIZEN, orgA);
        officerA = createUser("officer@example.com", Role.OFFICER, orgA);
        adminA = createUser("admin@example.com", Role.ADMIN, orgA);

        nocA = new NocApplication();
        nocA.setOrganizationId(orgA.getId());
        nocA.setApplicantId(citizenA.getId());
        nocA.setTitle("NOC A");
        nocA.setBuildingName("RES");
        nocA.setAddress("Add");
        nocA.setStatus(NocStatus.DRAFT);
        nocA = nocRepository.save(nocA);
    }

    private User createUser(String email, Role role, Organization org) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("hash");
        user.setStatus("ACTIVE");
        user.setRole(role.name());
        user.setOrganizationId(org.getId());
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return userRepository.save(user);
    }

    @Test
    void citizenCanRetrieveOwnNoc() throws Exception {
        String token = jwtService.generateToken(citizenA.getId());

        mockMvc.perform(get("/api/v1/noc/applications/" + nocA.getId())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(nocA.getId().toString()))
                .andExpect(jsonPath("$.title").value("NOC A"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void officerCanAccessInspectionEndpoints() throws Exception {
        String token = jwtService.generateToken(officerA.getId());

        // This verifies that the endpoint exists and officer is authorized (will return 200 or 404 for empty list, but not 401/403)
        mockMvc.perform(get("/api/v1/noc/applications/" + nocA.getId() + "/inspections")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanAccessOrganizationEndpoints() throws Exception {
        String token = jwtService.generateToken(adminA.getId());

        mockMvc.perform(get("/api/v1/organizations/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Org A"));
    }
}
