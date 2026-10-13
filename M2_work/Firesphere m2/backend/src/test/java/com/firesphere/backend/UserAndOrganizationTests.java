package com.firesphere.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firesphere.backend.auth.entity.User;
import com.firesphere.backend.auth.repository.UserRepository;
import com.firesphere.backend.common.security.JwtService;
import com.firesphere.backend.common.security.Role;
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
public class UserAndOrganizationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @org.springframework.boot.test.mock.mockito.SpyBean
    private com.firesphere.backend.common.audit.SimpleAuditLogger auditLogger;

    private User citizenA;
    private User adminA;
    private User adminB;
    private Organization orgA;
    private Organization orgB;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        orgA = new Organization();
        orgA.setName("Org A");
        orgA = organizationRepository.save(orgA);

        orgB = new Organization();
        orgB.setName("Org B");
        orgB = organizationRepository.save(orgB);

        citizenA = new User();
        citizenA.setEmail("citizenA@test.com");
        citizenA.setPassword("pass");
        citizenA.setStatus("ACTIVE");
        citizenA.setRole(Role.CITIZEN.name());
        citizenA.setOrganization(orgA);
        citizenA.setOrganizationId(orgA.getId());
        citizenA.setCreatedAt(OffsetDateTime.now());
        citizenA.setUpdatedAt(OffsetDateTime.now());
        citizenA = userRepository.save(citizenA);

        adminA = new User();
        adminA.setEmail("adminA@test.com");
        adminA.setPassword("pass");
        adminA.setStatus("ACTIVE");
        adminA.setRole(Role.ADMIN.name());
        adminA.setOrganization(orgA);
        adminA.setOrganizationId(orgA.getId());
        adminA.setCreatedAt(OffsetDateTime.now());
        adminA.setUpdatedAt(OffsetDateTime.now());
        adminA = userRepository.save(adminA);

        adminB = new User();
        adminB.setEmail("adminB@test.com");
        adminB.setPassword("pass");
        adminB.setStatus("ACTIVE");
        adminB.setRole(Role.ADMIN.name());
        adminB.setOrganization(orgB);
        adminB.setOrganizationId(orgB.getId());
        adminB.setCreatedAt(OffsetDateTime.now());
        adminB.setUpdatedAt(OffsetDateTime.now());
        adminB = userRepository.save(adminB);
    }

    @Test
    void getCurrentUserReturnsProfile() throws Exception {
        String token = jwtService.generateToken(citizenA.getUuid());
        
        mockMvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("citizenA@test.com")))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void getMyOrganizationReturnsOrg() throws Exception {
        String token = jwtService.generateToken(adminA.getUuid());

        mockMvc.perform(get("/api/v1/organizations/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Org A")));
    }

    @Test
    void citizenCannotUpdateOrganization() throws Exception {
        String token = jwtService.generateToken(citizenA.getUuid());
        String body = "{\"name\":\"Hacked Name\"}";

        mockMvc.perform(patch("/api/v1/organizations/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanUpdateOrganization() throws Exception {
        String token = jwtService.generateToken(adminA.getUuid());
        String body = "{\"name\":\"New Org A Name\"}";

        mockMvc.perform(patch("/api/v1/organizations/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("New Org A Name")));

        org.mockito.Mockito.verify(auditLogger).log(
            org.mockito.ArgumentMatchers.eq(com.firesphere.backend.common.audit.AuditEventType.ORGANIZATION_UPDATED),
            org.mockito.ArgumentMatchers.eq(orgA.getUuid()),
            org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void adminCanListUsersInSameOrg() throws Exception {
        String token = jwtService.generateToken(adminA.getUuid());

        mockMvc.perform(get("/api/v1/users")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2))); // citizenA, adminA
    }

    @Test
    void adminCannotAccessUserInDifferentOrg() throws Exception {
        String token = jwtService.generateToken(adminA.getUuid());

        // Admin A trying to access Admin B who is in Org B
        mockMvc.perform(get("/api/v1/users/" + adminB.getUuid())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanCreateUserInSameOrg() throws Exception {
        String token = jwtService.generateToken(adminA.getUuid());
        String body = "{\"email\":\"newuser@test.com\",\"password\":\"password123\",\"role\":\"CITIZEN\"}";

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("newuser@test.com")));

        org.mockito.Mockito.verify(auditLogger).log(
            org.mockito.ArgumentMatchers.eq(com.firesphere.backend.common.audit.AuditEventType.USER_CREATED),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void adminCanUpdateUserRoleAndStatus() throws Exception {
        String token = jwtService.generateToken(adminA.getUuid());
        String body = "{\"role\":\"OFFICER\",\"status\":\"SUSPENDED\"}";

        mockMvc.perform(patch("/api/v1/users/" + citizenA.getUuid())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("OFFICER")))
                .andExpect(jsonPath("$.status", is("SUSPENDED")));

        org.mockito.Mockito.verify(auditLogger).log(
            org.mockito.ArgumentMatchers.eq(com.firesphere.backend.common.audit.AuditEventType.USER_ROLE_CHANGED),
            org.mockito.ArgumentMatchers.eq(citizenA.getUuid()),
            org.mockito.ArgumentMatchers.anyString()
        );

        org.mockito.Mockito.verify(auditLogger).log(
            org.mockito.ArgumentMatchers.eq(com.firesphere.backend.common.audit.AuditEventType.USER_STATUS_CHANGED),
            org.mockito.ArgumentMatchers.eq(citizenA.getUuid()),
            org.mockito.ArgumentMatchers.anyString()
        );
    }
}
