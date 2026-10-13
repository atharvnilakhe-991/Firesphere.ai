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
import com.firesphere.backend.workflow.dto.WorkflowActionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RetryContractTests {

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
    private NocApplication nocA;

    @BeforeEach
    void setup() {
        nocRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        orgA = new Organization();
        orgA.setName("Org A");
        orgA = organizationRepository.save(orgA);

        citizenA = new User();
        citizenA.setEmail("citizena@example.com");
        citizenA.setPassword("hash");
        citizenA.setStatus("ACTIVE");
        citizenA.setRole(Role.CITIZEN.name());
        citizenA.setOrganization(orgA);
        citizenA.setOrganizationId(orgA.getId());
        citizenA.setCreatedAt(OffsetDateTime.now());
        citizenA.setUpdatedAt(OffsetDateTime.now());
        citizenA = userRepository.save(citizenA);

        nocA = new NocApplication();
        nocA.setOrgId(orgA.getId());
        nocA.setOrganizationId(orgA.getUuid());
        nocA.setApplicantId(citizenA.getUuid());
        nocA.setTitle("NOC A");
        nocA.setBuildingName("RES");
        nocA.setAddress("Add");
        nocA.setStatus(NocStatus.DRAFT);
        nocA = nocRepository.save(nocA);
    }

    @Test
    void duplicateWorkflowTransitionReturnsError() throws Exception {
        String token = jwtService.generateToken(citizenA.getUuid());

        WorkflowActionRequest req = new WorkflowActionRequest(com.firesphere.backend.workflow.model.WorkflowAction.SUBMIT, "Submitting NOC");

        // First transition should succeed
        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/transitions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Second duplicate transition should fail with a 400 Bad Request
        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getUuid() + "/transitions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
