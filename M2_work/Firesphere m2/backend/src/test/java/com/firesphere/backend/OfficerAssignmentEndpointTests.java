package com.firesphere.backend;

import com.firesphere.backend.auth.entity.User;
import com.firesphere.backend.auth.repository.UserRepository;
import com.firesphere.backend.common.security.JwtService;
import com.firesphere.backend.common.security.Role;
import com.firesphere.backend.inspection.entity.NocInspection;
import com.firesphere.backend.inspection.repository.NocInspectionRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OfficerAssignmentEndpointTests {

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
    private NocInspectionRepository inspectionRepository;

    private Organization orgA;
    private User officer1;
    private User officer2;
    private User citizen1;
    private User admin1;
    private NocApplication nocApp;
    private NocInspection inspection1;
    private NocInspection inspection2;

    @BeforeEach
    void setup() {
        inspectionRepository.deleteAll();
        nocRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        orgA = new Organization();
        orgA.setName("Test Fire Service");
        orgA = organizationRepository.save(orgA);

        officer1 = createUser("officer1@demo.org", Role.OFFICER, orgA);
        officer2 = createUser("officer2@demo.org", Role.OFFICER, orgA);
        citizen1 = createUser("citizen1@demo.org", Role.CITIZEN, orgA);
        admin1 = createUser("admin1@demo.org", Role.ADMIN, orgA);

        nocApp = new NocApplication();
        nocApp.setOrgId(orgA.getId());
        nocApp.setOrganizationId(orgA.getUuid());
        nocApp.setApplicantId(citizen1.getUuid());
        nocApp.setTitle("City Center Tower");
        nocApp.setBuildingName("High-Rise Commercial");
        nocApp.setAddress("42 Market Street");
        nocApp.setStatus(NocStatus.INSPECTION_SCHEDULED);
        nocApp = nocRepository.save(nocApp);

        inspection1 = new NocInspection();
        inspection1.setNocApplicationId(nocApp.getId());
        inspection1.setApplicationId(nocApp.getUuid());
        inspection1.setOrgId(orgA.getId());
        inspection1.setOrganizationId(orgA.getUuid());
        inspection1.setInspectionType("FINAL");
        inspection1.setInspectionStatus("SCHEDULED");
        inspection1.setScheduledAt(OffsetDateTime.now().plusDays(1));
        inspection1.setAssignedOfficer(officer1.getUuid());
        inspection1 = inspectionRepository.save(inspection1);

        inspection2 = new NocInspection();
        inspection2.setNocApplicationId(nocApp.getId());
        inspection2.setApplicationId(nocApp.getUuid());
        inspection2.setOrgId(orgA.getId());
        inspection2.setOrganizationId(orgA.getUuid());
        inspection2.setInspectionType("ROUTINE");
        inspection2.setInspectionStatus("SCHEDULED");
        inspection2.setScheduledAt(OffsetDateTime.now().plusDays(2));
        inspection2.setAssignedOfficer(officer2.getUuid());
        inspection2 = inspectionRepository.save(inspection2);
    }

    private User createUser(String email, Role role, Organization org) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("secretHash");
        user.setStatus("ACTIVE");
        user.setRole(role.name());
        user.setOrganization(org);
        user.setOrganizationId(org.getId());
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return userRepository.save(user);
    }

    @Test
    void officerGetsOnlyAssignedInspections() throws Exception {
        String token = jwtService.generateToken(officer1.getUuid());

        mockMvc.perform(get("/api/v1/officer/assignments")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(inspection1.getUuid().toString())))
                .andExpect(jsonPath("$[0].title", is("City Center Tower")))
                .andExpect(jsonPath("$[0].address", is("Premises Address")))
                .andExpect(jsonPath("$[0].propertyType", is("Building-1")))
                .andExpect(jsonPath("$[0].inspectionType", is("FINAL")))
                .andExpect(jsonPath("$[0].syncState", is("SYNCED")));
    }

    @Test
    void unauthenticatedOfficerReceives401() throws Exception {
        mockMvc.perform(get("/api/v1/officer/assignments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void citizenReceives403ForOfficerAssignments() throws Exception {
        String token = jwtService.generateToken(citizen1.getUuid());

        mockMvc.perform(get("/api/v1/officer/assignments")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessInspectionsMonitoring() throws Exception {
        String token = jwtService.generateToken(admin1.getUuid());

        mockMvc.perform(get("/api/v1/inspections")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }
}
