package com.firesphere.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firesphere.backend.auth.entity.User;
import com.firesphere.backend.auth.repository.UserRepository;
import com.firesphere.backend.certificate.entity.NocCertificate;
import com.firesphere.backend.certificate.repository.NocCertificateRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CertificateTests {

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
    private NocCertificateRepository certificateRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Organization orgA;
    private Organization orgB;
    
    private User citizenA;
    private User officerA;
    private User authorityA;
    private User adminA;
    private User citizenB;
    
    private NocApplication nocA;
    private NocApplication nocB;

    @BeforeEach
    void setup() {
        certificateRepository.deleteAll();
        nocRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        orgA = new Organization();
        orgA.setName("Org A");
        orgA = organizationRepository.save(orgA);

        orgB = new Organization();
        orgB.setName("Org B");
        orgB = organizationRepository.save(orgB);

        citizenA = createUser("citizena@example.com", Role.CITIZEN, orgA);
        officerA = createUser("officera@example.com", Role.OFFICER, orgA);
        authorityA = createUser("authoritya@example.com", Role.APPROVAL_AUTHORITY, orgA);
        adminA = createUser("admina@example.com", Role.ADMIN, orgA);
        citizenB = createUser("citizenb@example.com", Role.CITIZEN, orgB);

        nocA = new NocApplication();
        nocA.setOrgId(orgA.getId());
        nocA.setOrganizationId(orgA.getUuid());
        nocA.setApplicantId(citizenA.getUuid());
        nocA.setTitle("NOC A");
        nocA.setBuildingName("RES");
        nocA.setAddress("Add");
        nocA.setStatus(NocStatus.APPROVED);
        nocA = nocRepository.save(nocA);

        nocB = new NocApplication();
        nocB.setOrgId(orgB.getId());
        nocB.setOrganizationId(orgB.getUuid());
        nocB.setApplicantId(citizenB.getUuid());
        nocB.setTitle("NOC B");
        nocB.setBuildingName("RES");
        nocB.setAddress("Add");
        nocB.setStatus(NocStatus.APPROVED);
        nocB = nocRepository.save(nocB);
    }

    private User createUser(String email, Role role, Organization org) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("hash");
        user.setStatus("ACTIVE");
        user.setRole(role.name());
        user.setOrganization(org);
        user.setOrganizationId(org.getId());
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return userRepository.save(user);
    }

    @Test
    void authorityCanGenerateCertificate() throws Exception {
        String token = jwtService.generateToken(authorityA.getUuid());
        
        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/certificate")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(certificateRepository.findAll()).hasSize(1);
    }

    @Test
    void adminCanGenerateCertificate() throws Exception {
        String token = jwtService.generateToken(adminA.getUuid());
        
        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/certificate")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(certificateRepository.findAll()).hasSize(1);
    }

    @Test
    void officerCannotGenerateCertificate() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());
        
        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/certificate")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void generationIsIdempotent() throws Exception {
        String token = jwtService.generateToken(authorityA.getUuid());
        
        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/certificate")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Call again
        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/certificate")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Should still be 1 certificate
        assertThat(certificateRepository.findAll()).hasSize(1);
    }

    @Test
    void cannotGenerateForUnapprovedApplication() throws Exception {
        nocA.setStatus(NocStatus.SUBMITTED);
        nocRepository.save(nocA);

        String token = jwtService.generateToken(authorityA.getUuid());
        
        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/certificate")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crossTenantGenerationBlocked() throws Exception {
        String token = jwtService.generateToken(authorityA.getUuid());
        
        mockMvc.perform(post("/api/v1/noc/applications/" + nocB.getUuid() + "/certificate")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotGenerateForDraftApplication() throws Exception {
        nocA.setStatus(NocStatus.DRAFT);
        nocRepository.save(nocA);

        String token = jwtService.generateToken(authorityA.getUuid());
        
        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/certificate")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void citizenCannotGenerateCertificate() throws Exception {
        String token = jwtService.generateToken(citizenA.getUuid());
        
        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/certificate")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotRetrieveCertificate() throws Exception {
        mockMvc.perform(get("/api/v1/noc/applications/" + nocA.getUuid() + "/certificate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void citizenCanRetrieveOwnCertificate() throws Exception {
        // Pre-create cert
        NocCertificate cert = new NocCertificate();
        cert.setApplicationId(nocA.getId());
        cert.setApplicationUuid(nocA.getUuid());
        cert.setOrgId(orgA.getId());
        cert.setOrganizationId(orgA.getUuid());
        cert.setStatus("ACTIVE");
        certificateRepository.save(cert);

        String token = jwtService.generateToken(citizenA.getUuid());
        
        mockMvc.perform(get("/api/v1/noc/applications/" + nocA.getUuid() + "/certificate")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void citizenCannotRetrieveOtherCertificate() throws Exception {
        NocCertificate cert = new NocCertificate();
        cert.setApplicationId(nocA.getId());
        cert.setApplicationUuid(nocA.getUuid());
        cert.setOrgId(orgA.getId());
        cert.setOrganizationId(orgA.getUuid());
        cert.setStatus("ACTIVE");
        certificateRepository.save(cert);

        User citizenA2 = createUser("citizena2@example.com", Role.CITIZEN, orgA);
        String token = jwtService.generateToken(citizenA2.getUuid());
        
        mockMvc.perform(get("/api/v1/noc/applications/" + nocA.getUuid() + "/certificate")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
