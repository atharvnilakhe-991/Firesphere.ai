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
import com.firesphere.backend.workflow.model.WorkflowAction;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowApprovalTests {

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
    private User officerA;
    private User authorityA;
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

        citizenA = createUser("citizena@example.com", Role.CITIZEN, orgA.getId());
        officerA = createUser("officera@example.com", Role.OFFICER, orgA.getId());
        authorityA = createUser("authoritya@example.com", Role.APPROVAL_AUTHORITY, orgA.getId());

        nocA = new NocApplication();
        nocA.setOrganizationId(orgA.getId());
        nocA.setApplicantId(citizenA.getId());
        nocA.setTitle("NOC A");
        nocA.setBuildingName("RES");
        nocA.setAddress("Add");
        nocA.setStatus(NocStatus.INSPECTION_COMPLETED);
        nocA = nocRepository.save(nocA);
    }

    private User createUser(String email, Role role, UUID orgId) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("hash");
        user.setStatus("ACTIVE");
        user.setRole(role.name());
        user.setOrganizationId(orgId);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return userRepository.save(user);
    }

    @Test
    void officerCanSubmitForApproval() throws Exception {
        String token = jwtService.generateToken(officerA.getId());
        WorkflowActionRequest req = new WorkflowActionRequest(WorkflowAction.SUBMIT_FOR_APPROVAL, null);

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getId() + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        NocApplication saved = nocRepository.findById(nocA.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(NocStatus.PENDING_APPROVAL);
    }

    @Test
    void authorityCanApprove() throws Exception {
        nocA.setStatus(NocStatus.PENDING_APPROVAL);
        nocRepository.save(nocA);

        String token = jwtService.generateToken(authorityA.getId());
        WorkflowActionRequest req = new WorkflowActionRequest(WorkflowAction.APPROVE, null);

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getId() + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        NocApplication saved = nocRepository.findById(nocA.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(NocStatus.APPROVED);
    }

    @Test
    void officerCannotApprove() throws Exception {
        nocA.setStatus(NocStatus.PENDING_APPROVAL);
        nocRepository.save(nocA);

        String token = jwtService.generateToken(officerA.getId());
        WorkflowActionRequest req = new WorkflowActionRequest(WorkflowAction.APPROVE, null);

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getId() + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorityCanReject() throws Exception {
        nocA.setStatus(NocStatus.PENDING_APPROVAL);
        nocRepository.save(nocA);

        String token = jwtService.generateToken(authorityA.getId());
        WorkflowActionRequest req = new WorkflowActionRequest(WorkflowAction.REJECT, "Missing compliance");

        mockMvc.perform(post("/api/v1/noc/applications/" + nocA.getId() + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        NocApplication saved = nocRepository.findById(nocA.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(NocStatus.CORRECTION_REQUIRED);
    }
}
