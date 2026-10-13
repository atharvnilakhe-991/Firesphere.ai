package com.firesphere.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firesphere.backend.common.security.JwtService;
import com.firesphere.backend.common.security.Role;
import com.firesphere.backend.inspection.dto.ScheduleInspectionRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InspectionAssignmentTests {

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
    private Organization orgB;
    
    private User officerA;
    private User inactiveOfficerA;
    private User citizenA;
    private User officerB;

    private NocApplication nocA;

    @BeforeEach
    void setup() {
        nocRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        orgA = new Organization();
        orgA.setName("Org A");
        orgA = organizationRepository.save(orgA);

        orgB = new Organization();
        orgB.setName("Org B");
        orgB = organizationRepository.save(orgB);

        officerA = createUser("officerA@example.com", Role.OFFICER, orgA, true);
        inactiveOfficerA = createUser("inactiveA@example.com", Role.OFFICER, orgA, false);
        citizenA = createUser("citizenA@example.com", Role.CITIZEN, orgA, true);
        officerB = createUser("officerB@example.com", Role.OFFICER, orgB, true);

        nocA = new NocApplication();
        nocA.setOrgId(orgA.getId());
        nocA.setOrganizationId(orgA.getUuid());
        nocA.setApplicantId(citizenA.getUuid());
        nocA.setTitle("NOC App");
        nocA.setBuildingName("RESIDENTIAL");
        nocA.setAddress("A1");
        nocA.setStatus(NocStatus.INSPECTION_SCHEDULED);
        nocA = nocRepository.save(nocA);
    }

    private User createUser(String email, Role role, Organization org, boolean active) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("hash");
        user.setStatus(active ? "ACTIVE" : "INACTIVE");
        user.setRole(role.name());
        user.setOrganization(org);
        user.setOrganizationId(org.getId());
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return userRepository.save(user);
    }

    @Test
    void canAssignSameTenantActiveOfficer() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());
        ScheduleInspectionRequest req = new ScheduleInspectionRequest("FINAL", OffsetDateTime.now().plusDays(1), officerA.getUuid());

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/inspections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void cannotAssignNonexistentOfficer() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());
        ScheduleInspectionRequest req = new ScheduleInspectionRequest("FINAL", OffsetDateTime.now().plusDays(1), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/inspections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void citizenCannotAssignOfficer() throws Exception {
        String token = jwtService.generateToken(citizenA.getUuid());
        ScheduleInspectionRequest req = new ScheduleInspectionRequest("FINAL", OffsetDateTime.now().plusDays(1), officerA.getUuid());

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/inspections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotAssignNonOfficerUser() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());
        ScheduleInspectionRequest req = new ScheduleInspectionRequest("FINAL", OffsetDateTime.now().plusDays(1), citizenA.getUuid());

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/inspections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotAssignInactiveOfficer() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());
        ScheduleInspectionRequest req = new ScheduleInspectionRequest("FINAL", OffsetDateTime.now().plusDays(1), inactiveOfficerA.getUuid());

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/inspections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotAssignCrossTenantOfficer() throws Exception {
        String token = jwtService.generateToken(officerA.getUuid());
        ScheduleInspectionRequest req = new ScheduleInspectionRequest("FINAL", OffsetDateTime.now().plusDays(1), officerB.getUuid());

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/inspections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crossTenantOfficerCannotSchedule() throws Exception {
        String token = jwtService.generateToken(officerB.getUuid());
        ScheduleInspectionRequest req = new ScheduleInspectionRequest("FINAL", OffsetDateTime.now().plusDays(1), officerA.getUuid());

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/inspections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()); // noc is not found for tenant B
    }
}
