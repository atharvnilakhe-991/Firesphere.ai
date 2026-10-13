package com.firesphere.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firesphere.backend.auth.entity.User;
import com.firesphere.backend.auth.repository.UserRepository;
import com.firesphere.backend.common.security.JwtService;
import com.firesphere.backend.common.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {FiresphereBackendApplication.class, SecurityTests.TestController.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private User citizenUser;
    private User adminUser;
    private UUID orgA = UUID.randomUUID();
    private UUID orgB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        citizenUser = new User();
        citizenUser.setEmail("citizen@test.com");
        citizenUser.setPassword("pass");
        citizenUser.setStatus("ACTIVE");
        citizenUser.setRole(Role.CITIZEN.name());
        citizenUser.setOrganizationId(orgA);
        citizenUser.setCreatedAt(OffsetDateTime.now());
        citizenUser.setUpdatedAt(OffsetDateTime.now());
        citizenUser = userRepository.save(citizenUser);

        adminUser = new User();
        adminUser.setEmail("admin@test.com");
        adminUser.setPassword("pass");
        adminUser.setStatus("ACTIVE");
        adminUser.setRole(Role.ADMIN.name());
        adminUser.setOrganizationId(orgB);
        adminUser.setCreatedAt(OffsetDateTime.now());
        adminUser.setUpdatedAt(OffsetDateTime.now());
        adminUser = userRepository.save(adminUser);
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/test/citizen-only"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/test/citizen-only")
                .header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredJwtReturns401() throws Exception {
        String secret = (String) org.springframework.test.util.ReflectionTestUtils.getField(jwtService, "secretKey");
        // Create an expired JWT token manually for testing
        String expiredToken = io.jsonwebtoken.Jwts.builder()
                .subject(citizenUser.getId().toString())
                .issuer("firesphere.ai")
                .issuedAt(new java.util.Date(System.currentTimeMillis() - 10000))
                .expiration(new java.util.Date(System.currentTimeMillis() - 5000)) // Expired 5 seconds ago
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/v1/test/citizen-only")
                .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignatureJwtReturns401() throws Exception {
        // Sign with a different secret
        String invalidSigToken = io.jsonwebtoken.Jwts.builder()
                .subject(citizenUser.getId().toString())
                .issuer("firesphere.ai")
                .issuedAt(new java.util.Date(System.currentTimeMillis()))
                .expiration(new java.util.Date(System.currentTimeMillis() + 1000 * 60 * 60))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor("a-completely-different-and-wrong-jwt-secret-for-testing-only".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/v1/test/citizen-only")
                .header("Authorization", "Bearer " + invalidSigToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void citizenCanAccessCitizenEndpoint() throws Exception {
        String token = jwtService.generateToken(citizenUser.getId());

        mockMvc.perform(get("/api/v1/test/citizen-only")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void citizenCannotAccessAdminEndpoint() throws Exception {
        String token = jwtService.generateToken(citizenUser.getId());

        mockMvc.perform(get("/api/v1/test/admin-only")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessBothEndpoints() throws Exception {
        String token = jwtService.generateToken(adminUser.getId());

        mockMvc.perform(get("/api/v1/test/citizen-only")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/test/admin-only")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void citizenCanAccessOwnOrganizationResource() throws Exception {
        String token = jwtService.generateToken(citizenUser.getId());

        mockMvc.perform(get("/api/v1/test/org/" + orgA)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void citizenCannotAccessOtherOrganizationResource() throws Exception {
        String token = jwtService.generateToken(citizenUser.getId());

        mockMvc.perform(get("/api/v1/test/org/" + orgB)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestBodyTenantSubstitutionIsBlocked() throws Exception {
        String token = jwtService.generateToken(citizenUser.getId());
        String body = "{\"orgId\":\"" + orgB.toString() + "\"}";

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/test/org-body")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantHeaderOverrideIsIgnored() throws Exception {
        String token = jwtService.generateToken(citizenUser.getId());

        // Even if client tries to spoof tenant header, the backend resolves tenant from JWT/persistence (orgA).
        mockMvc.perform(get("/api/v1/test/current-tenant")
                .header("Authorization", "Bearer " + token)
                .header("X-Organization-Id", orgB.toString()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("\"" + orgA.toString() + "\""));
    }

    @Test
    void tenantContextIsCleanedUpBetweenRequests() throws Exception {
        String tokenA = jwtService.generateToken(citizenUser.getId());
        String tokenB = jwtService.generateToken(adminUser.getId());

        // First request as User A
        mockMvc.perform(get("/api/v1/test/current-tenant")
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("\"" + orgA.toString() + "\""));

        // Second request as User B (on same thread pooled mockmvc) must not inherit orgA
        mockMvc.perform(get("/api/v1/test/current-tenant")
                .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("\"" + orgB.toString() + "\""));
                
        // Third request unauthenticated must have null context (returns empty body)
        mockMvc.perform(get("/api/v1/test/current-tenant"))
                .andExpect(status().isUnauthorized());
    }

    @RestController
    public static class TestController {

        @PreAuthorize("hasAuthority('user:read')")
        @GetMapping("/api/v1/test/citizen-only")
        public String citizenEndpoint() {
            return "ok";
        }

        @PreAuthorize("hasAuthority('organization:write')")
        @GetMapping("/api/v1/test/admin-only")
        public String adminEndpoint() {
            return "ok";
        }

        @PreAuthorize("@tenantSecurity.isTenantMember(#orgId)")
        @GetMapping("/api/v1/test/org/{orgId}")
        public String orgEndpoint(@PathVariable UUID orgId) {
            return "ok";
        }

        @PreAuthorize("@tenantSecurity.isTenantMember(#request.orgId())")
        @org.springframework.web.bind.annotation.PostMapping("/api/v1/test/org-body")
        public String orgBodyEndpoint(@org.springframework.web.bind.annotation.RequestBody OrgRequest request) {
            return "ok";
        }

        @GetMapping("/api/v1/test/current-tenant")
        public UUID currentTenant() {
            return com.firesphere.backend.common.security.TenantContext.getCurrentOrganizationId();
        }

        public record OrgRequest(UUID orgId) {}
    }
}
