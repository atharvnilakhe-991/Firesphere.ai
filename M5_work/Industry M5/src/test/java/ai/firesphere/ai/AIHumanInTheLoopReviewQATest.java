package ai.firesphere.ai;

import ai.firesphere.contracts.integration.AIInspectionResultProviderContract;
import ai.firesphere.contracts.mock.MockAIInspectionResultProvider;
import ai.firesphere.integration.dto.AIFindingReviewRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI QA Suite: Human-in-the-Loop Regulatory Review Workflow.
 * Validates that AI detection findings cannot enact regulatory enforcement without
 * explicit human review by an authorized fire officer. Verifies allowed decisions
 * (APPROVED, DISMISSED, ESCALATED), rejection of unauthorized decisions,
 * and preservation of original detection evidence.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AIHumanInTheLoopReviewQATest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockAIInspectionResultProvider mockAiProvider;

    private UUID findingId;
    private UUID buildingId;

    @BeforeEach
    void setUp() {
        mockAiProvider.clear();
        findingId = UUID.randomUUID();
        buildingId = UUID.randomUUID();

        // Seed a valid AI finding in PENDING_REVIEW state
        AIInspectionResultProviderContract.AIFindingDTO finding = new AIInspectionResultProviderContract.AIFindingDTO(
                findingId,
                buildingId,
                "BLOCKED_EMERGENCY_EXIT",
                0.93,
                "HIGH",
                "https://storage.firesphere.ai/evidence/exit_block_sector2.jpg",
                "{\"x\": 50, \"y\": 100, \"w\": 200, \"h\": 300}",
                null,
                null,
                null,
                Instant.now(),
                null
        );
        mockAiProvider.ingestAIFinding(finding);
    }

    @Test
    @DisplayName("Human Review: Officer validates violation and approves finding (APPROVED)")
    void testHumanReviewDecision_Approved() throws Exception {
        AIFindingReviewRequest request = new AIFindingReviewRequest(
                "APPROVED",
                "Officer physically inspected site and confirmed fire door is padlocked shut",
                "OF-CHIEF-101"
        );

        mockMvc.perform(post("/api/v1/integrations/ai-findings/{id}/review", findingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findingId").value(findingId.toString()))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedBy").value("OF-CHIEF-101"))
                .andExpect(jsonPath("$.reviewerNotes").value(containsString("fire door is padlocked shut")))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty());
    }

    @Test
    @DisplayName("Human Review: Officer determines false positive and dismisses finding (DISMISSED)")
    void testHumanReviewDecision_Dismissed() throws Exception {
        AIFindingReviewRequest request = new AIFindingReviewRequest(
                "DISMISSED",
                "Reflective decorative banner misidentified as open flame by CV model",
                "OF-CAPT-202"
        );

        mockMvc.perform(post("/api/v1/integrations/ai-findings/{id}/review", findingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findingId").value(findingId.toString()))
                .andExpect(jsonPath("$.status").value("DISMISSED"))
                .andExpect(jsonPath("$.reviewedBy").value("OF-CAPT-202"))
                .andExpect(jsonPath("$.reviewerNotes").value(containsString("misidentified as open flame")));
    }

    @Test
    @DisplayName("Human Review: Officer escalates complex violation to fire marshal (ESCALATED)")
    void testHumanReviewDecision_Escalated() throws Exception {
        AIFindingReviewRequest request = new AIFindingReviewRequest(
                "ESCALATED",
                "Structural alteration without permit; escalating to Municipal Fire Marshal",
                "OF-INSP-303"
        );

        mockMvc.perform(post("/api/v1/integrations/ai-findings/{id}/review", findingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findingId").value(findingId.toString()))
                .andExpect(jsonPath("$.status").value("ESCALATED"))
                .andExpect(jsonPath("$.reviewedBy").value("OF-INSP-303"));
    }

    @Test
    @DisplayName("Human Review: Invalid decision keyword rejected with HTTP 400")
    void testInvalidReviewDecisionRejected() throws Exception {
        AIFindingReviewRequest request = new AIFindingReviewRequest(
                "AUTO_REVOKE_PERMIT",
                "Unauthorized autonomous enforcement action",
                "OF-101"
        );

        mockMvc.perform(post("/api/v1/integrations/ai-findings/{id}/review", findingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("Allowed values: APPROVED, DISMISSED, ESCALATED")));
    }

    @Test
    @DisplayName("Human Review: Blank reviewer badge/ID rejected with HTTP 400")
    void testMissingReviewerIdentityRejected() throws Exception {
        AIFindingReviewRequest request = new AIFindingReviewRequest(
                "APPROVED",
                "Officer notes without identity",
                "   "
        );

        mockMvc.perform(post("/api/v1/integrations/ai-findings/{id}/review", findingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Human Review: Reviewing non-existent finding returns HTTP 404 RESOURCE_NOT_FOUND")
    void testReviewNonExistentFinding_Returns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        AIFindingReviewRequest request = new AIFindingReviewRequest(
                "APPROVED",
                "Review attempt on phantom finding",
                "OF-101"
        );

        mockMvc.perform(post("/api/v1/integrations/ai-findings/{id}/review", unknownId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Human Review: Officer review preserves original detection evidence and confidence metadata")
    void testReviewPreservesOriginalDetectionMetadata() throws Exception {
        AIFindingReviewRequest request = new AIFindingReviewRequest(
                "APPROVED",
                "Exit cleared after notice",
                "OF-CHIEF-101"
        );

        mockMvc.perform(post("/api/v1/integrations/ai-findings/{id}/review", findingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Fetch back and assert original detection attributes were not mutated or lost
        mockMvc.perform(get("/api/v1/integrations/ai-findings/{id}", findingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findingId").value(findingId.toString()))
                .andExpect(jsonPath("$.incidentOrBuildingId").value(buildingId.toString()))
                .andExpect(jsonPath("$.findingType").value("BLOCKED_EMERGENCY_EXIT"))
                .andExpect(jsonPath("$.confidence").value(0.93))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.evidenceMediaUrl").value("https://storage.firesphere.ai/evidence/exit_block_sector2.jpg"))
                .andExpect(jsonPath("$.boundingBoxJson").value("{\"x\": 50, \"y\": 100, \"w\": 200, \"h\": 300}"))
                .andExpect(jsonPath("$.detectedAt").isNotEmpty())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }
}
