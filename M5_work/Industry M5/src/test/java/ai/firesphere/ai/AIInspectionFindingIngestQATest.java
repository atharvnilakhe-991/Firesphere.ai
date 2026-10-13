package ai.firesphere.ai;

import ai.firesphere.contracts.integration.AIInspectionResultProviderContract;
import ai.firesphere.contracts.mock.MockAIInspectionResultProvider;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI QA Suite: AI Inspection Finding Ingestion & Untrusted Input Guardrails.
 * Validates intake of computer vision inspection findings from Member 4's models,
 * confidence score boundary enforcement, rejection of malformed/oversized inputs,
 * and strict Human-in-the-Loop protection ensuring AI cannot auto-approve violations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AIInspectionFindingIngestQATest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockAIInspectionResultProvider mockAiProvider;

    @BeforeEach
    void setUp() {
        mockAiProvider.clear();
    }

    @Test
    @DisplayName("AI Intake: Ingest valid computer vision finding with valid confidence and metadata")
    void testNormalSuccessfulAIFindingIngest() throws Exception {
        UUID findingId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();

        AIInspectionResultProviderContract.AIFindingDTO finding = new AIInspectionResultProviderContract.AIFindingDTO(
                findingId,
                buildingId,
                "BLOCKED_EMERGENCY_EXIT",
                0.94,
                "HIGH",
                "https://storage.firesphere.ai/evidence/exit_block_101.jpg",
                "{\"x\": 120, \"y\": 80, \"width\": 300, \"height\": 450}",
                null,
                null,
                null,
                Instant.now(),
                null
        );

        // Act: Submit finding to integration ingest endpoint
        mockMvc.perform(post("/api/v1/integrations/ai-findings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(finding)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.findingId").value(findingId.toString()))
                .andExpect(jsonPath("$.message").value(containsString("queued for fire officer review")));

        // Verify finding stored with initial status PENDING_REVIEW
        mockMvc.perform(get("/api/v1/integrations/ai-findings/{id}", findingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findingId").value(findingId.toString()))
                .andExpect(jsonPath("$.findingType").value("BLOCKED_EMERGENCY_EXIT"))
                .andExpect(jsonPath("$.confidence").value(0.94))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.reviewedBy").doesNotExist())
                .andExpect(jsonPath("$.reviewedAt").doesNotExist());
    }

    @Test
    @DisplayName("AI Safety Guardrail: Untrusted AI finding cannot inject APPROVED status or bypass human review")
    void testUntrustedDataGuardrail_CannotInjectApprovedStatusAtIngest() throws Exception {
        UUID findingId = UUID.randomUUID();

        // Malicious or misconfigured external AI model attempts to self-approve and inject reviewer notes
        AIInspectionResultProviderContract.AIFindingDTO bypassAttempt = new AIInspectionResultProviderContract.AIFindingDTO(
                findingId,
                UUID.randomUUID(),
                "EXPIRED_FIRE_EXTINGUISHER",
                0.98,
                "CRITICAL",
                "https://storage.firesphere.ai/evidence/ext_expired.jpg",
                "{\"box\": [10, 20, 30, 40]}",
                "APPROVED", // Attempt to bypass human review
                "Auto-approved by AI Model YOLOv8",
                "AUTONOMOUS_AI_AGENT",
                Instant.now(),
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/integrations/ai-findings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bypassAttempt)))
                .andExpect(status().isAccepted());

        // Verify that the backend sanitized the intake: status is forced to PENDING_REVIEW, notes/reviewer wiped
        mockMvc.perform(get("/api/v1/integrations/ai-findings/{id}", findingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.reviewerNotes").doesNotExist())
                .andExpect(jsonPath("$.reviewedBy").doesNotExist())
                .andExpect(jsonPath("$.reviewedAt").doesNotExist());
    }

    @Test
    @DisplayName("AI Boundary: Confidence score 0.0 and 1.0 are valid; out-of-bounds rejected")
    void testConfidenceBoundaryValidation_ZeroToOneRange() throws Exception {
        UUID buildingId = UUID.randomUUID();

        // 1. Boundary: 0.0 is valid minimum
        AIInspectionResultProviderContract.AIFindingDTO minBoundary = new AIInspectionResultProviderContract.AIFindingDTO(
                UUID.randomUUID(), buildingId, "THERMAL_ANOMALY", 0.0, "LOW",
                null, null, null, null, null, Instant.now(), null
        );
        mockMvc.perform(post("/api/v1/integrations/ai-findings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(minBoundary)))
                .andExpect(status().isAccepted());

        // 2. Boundary: 1.0 is valid maximum
        AIInspectionResultProviderContract.AIFindingDTO maxBoundary = new AIInspectionResultProviderContract.AIFindingDTO(
                UUID.randomUUID(), buildingId, "THERMAL_ANOMALY", 1.0, "CRITICAL",
                null, null, null, null, null, Instant.now(), null
        );
        mockMvc.perform(post("/api/v1/integrations/ai-findings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maxBoundary)))
                .andExpect(status().isAccepted());

        // 3. Negative confidence rejected with HTTP 400
        AIInspectionResultProviderContract.AIFindingDTO negativeConfidence = new AIInspectionResultProviderContract.AIFindingDTO(
                UUID.randomUUID(), buildingId, "THERMAL_ANOMALY", -0.05, "LOW",
                null, null, null, null, null, Instant.now(), null
        );
        mockMvc.perform(post("/api/v1/integrations/ai-findings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negativeConfidence)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("confidence score must be between 0.0 and 1.0")));

        // 4. Over-unity confidence (> 1.0) rejected with HTTP 400
        AIInspectionResultProviderContract.AIFindingDTO excessiveConfidence = new AIInspectionResultProviderContract.AIFindingDTO(
                UUID.randomUUID(), buildingId, "THERMAL_ANOMALY", 1.05, "CRITICAL",
                null, null, null, null, null, Instant.now(), null
        );
        mockMvc.perform(post("/api/v1/integrations/ai-findings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(excessiveConfidence)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("confidence score must be between 0.0 and 1.0")));
    }

    @Test
    @DisplayName("AI Input Validation: Blank or missing findingType rejected with HTTP 400")
    void testMissingRequiredFields_Rejection() throws Exception {
        AIInspectionResultProviderContract.AIFindingDTO missingType = new AIInspectionResultProviderContract.AIFindingDTO(
                UUID.randomUUID(), UUID.randomUUID(), "   ", 0.85, "HIGH",
                null, null, null, null, null, Instant.now(), null
        );

        mockMvc.perform(post("/api/v1/integrations/ai-findings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingType)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("findingType is mandatory")));
    }

    @Test
    @DisplayName("AI Lookup: Querying non-existent finding ID returns HTTP 404 RESOURCE_NOT_FOUND")
    void testFindingNotFound_Returns404ResourceNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/integrations/ai-findings/{id}", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("AI Format Resilience: Malformed JSON and invalid UUID path parameter return HTTP 400")
    void testMalformedJsonAndInvalidUuid() throws Exception {
        // 1. Truncated JSON payload
        mockMvc.perform(post("/api/v1/integrations/ai-findings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"findingType\": \"CORRUPTED_AI_OUTPUT\", \"confidence\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.status").value(400));

        // 2. Corrupted UUID path variable
        mockMvc.perform(get("/api/v1/integrations/ai-findings/{id}", "not-a-valid-finding-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("AI Security: Error responses disclose zero stack traces, filesystem paths, or credentials")
    void testInformationDisclosureDefense() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/integrations/ai-findings/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andReturn();

        String body = result.getResponse().getContentAsString();

        assertThat(body).doesNotContain("Exception:");
        assertThat(body).doesNotContain("at ai.firesphere");
        assertThat(body).doesNotContain(".java:");
        assertThat(body).doesNotContain("C:\\");
        assertThat(body).doesNotContain("/home/");
        assertThat(body).doesNotContain("secret");
        assertThat(body).doesNotContain("key");
    }
}
