package com.firesphere.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.firesphere.backend.ai.client.HttpAiAnalysisClient;
import com.firesphere.backend.ai.dto.ExternalAiRequest;
import com.firesphere.backend.ai.dto.ExternalAiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpAiAnalysisClientTests {

    private HttpAiAnalysisClient client;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        client = new HttpAiAnalysisClient("http://mock-ai:8000", "test-secret-token", 1000, 1000);
        
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    void successfulAnalysisWithRichFindings() throws Exception {
        UUID inspectionId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        ExternalAiRequest req = new ExternalAiRequest(
                inspectionId,
                List.of(evidenceId),
                false,
                "COMMERCIAL_MERCANTILE",
                "UNAVAILABLE",
                "tenant-42"
        );

        ExternalAiResponse.BoundingBoxDto box = new ExternalAiResponse.BoundingBoxDto(0.15, 0.25, 0.45, 0.65);
        ExternalAiResponse.ExternalAiFinding finding = new ExternalAiResponse.ExternalAiFinding(
                UUID.randomUUID(),
                evidenceId,
                "FIRE_EXTINGUISHER_MISSING",
                0.94,
                "VERY_HIGH",
                "HIGH",
                "Mandatory safety equipment absent",
                85,
                "HIGH",
                "No fire extinguisher detected in designated wall bracket location.",
                List.of("Verify extinguisher presence on-site"),
                box,
                "GOOD",
                "REQUIRES_OFFICER_VERIFICATION",
                "firesphere-inspection-cv",
                "0.3.0-phase3",
                false,
                "NOT_APPROVED",
                "NOT_CALIBRATED",
                "VALID",
                inspectionId,
                null
        );

        ExternalAiResponse.ExternalAiSummary summary = new ExternalAiResponse.ExternalAiSummary(
                1,
                1,
                "HIGH",
                "HIGH",
                85,
                1,
                true,
                1,
                Map.of("averageConfidence", 0.94),
                Map.of("GOOD", 1),
                "NOT_CALIBRATED",
                "All AI findings are advisory visual observations."
        );

        ExternalAiResponse expectedRes = new ExternalAiResponse(
                UUID.randomUUID(),
                "COMPLETED",
                List.of(finding),
                summary,
                "firesphere-inspection-cv",
                "0.3.0-phase3",
                84.2,
                "tenant-42",
                "NOT_APPROVED",
                "NOT_CALIBRATED",
                null,
                null
        );

        mockServer.expect(MockRestRequestMatchers.requestTo("http://mock-ai:8000/internal/ai/inspection-analysis"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                .andExpect(MockRestRequestMatchers.header("X-AI-Service-Token", "test-secret-token"))
                .andRespond(MockRestResponseCreators.withSuccess(objectMapper.writeValueAsString(expectedRes), MediaType.APPLICATION_JSON));

        ExternalAiResponse res = client.analyze(req);

        mockServer.verify();
        assertThat(res.status()).isEqualTo("COMPLETED");
        assertThat(res.findings()).hasSize(1);
        assertThat(res.findings().get(0).findingType()).isEqualTo("FIRE_EXTINGUISHER_MISSING");
        assertThat(res.findings().get(0).confidence()).isEqualTo(0.94);
        assertThat(res.findings().get(0).boundingBox()).isNotNull();
        assertThat(res.findings().get(0).boundingBox().xMin()).isEqualTo(0.15);
        assertThat(res.findings().get(0).complianceStatus()).isEqualTo("REQUIRES_OFFICER_VERIFICATION");
        assertThat(res.modelApprovalStatus()).isEqualTo("NOT_APPROVED");
        assertThat(res.calibrationStatus()).isEqualTo("NOT_CALIBRATED");
        assertThat(res.summary().requiresOfficerReview()).isTrue();
    }

    @Test
    void handles401UnauthorizedWithoutSecretLeakage() {
        ExternalAiRequest req = new ExternalAiRequest(UUID.randomUUID(), List.of(UUID.randomUUID()));

        mockServer.expect(MockRestRequestMatchers.requestTo("http://mock-ai:8000/internal/ai/inspection-analysis"))
                .andRespond(MockRestResponseCreators.withUnauthorizedRequest());

        ExternalAiResponse res = client.analyze(req);

        mockServer.verify();
        assertThat(res.status()).isEqualTo("FAILED");
        assertThat(res.errorCode()).isEqualTo("AI_UNAUTHORIZED");
        assertThat(res.errorDetail()).contains("Missing or invalid X-AI-Service-Token");
        assertThat(res.errorDetail()).doesNotContain("test-secret-token");
    }

    @Test
    void handles422ValidationError() {
        ExternalAiRequest req = new ExternalAiRequest(UUID.randomUUID(), List.of(UUID.randomUUID()));

        mockServer.expect(MockRestRequestMatchers.requestTo("http://mock-ai:8000/internal/ai/inspection-analysis"))
                .andRespond(MockRestResponseCreators.withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body("{\"status\":\"FAILED\",\"errorCode\":\"VALIDATION_ERROR\",\"message\":\"Invalid payload\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        ExternalAiResponse res = client.analyze(req);

        mockServer.verify();
        assertThat(res.status()).isEqualTo("FAILED");
        assertThat(res.errorCode()).isEqualTo("AI_VALIDATION_ERROR");
        assertThat(res.errorDetail()).contains("Request validation failed");
    }

    @Test
    void handles500ServerError() {
        ExternalAiRequest req = new ExternalAiRequest(UUID.randomUUID(), List.of(UUID.randomUUID()));

        mockServer.expect(MockRestRequestMatchers.requestTo("http://mock-ai:8000/internal/ai/inspection-analysis"))
                .andRespond(MockRestResponseCreators.withServerError());

        ExternalAiResponse res = client.analyze(req);

        mockServer.verify();
        assertThat(res.status()).isEqualTo("FAILED");
        assertThat(res.errorCode()).isEqualTo("AI_SERVER_ERROR");
    }

    @Test
    void requestValidationEnforcesNonNullAndNonEmptyIdentifiers() {
        UUID validId = UUID.randomUUID();
        assertThatThrownBy(() -> new ExternalAiRequest(null, List.of(validId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inspectionId must not be null");

        assertThatThrownBy(() -> new ExternalAiRequest(validId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidenceIds must not be null or empty");

        assertThatThrownBy(() -> new ExternalAiRequest(validId, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidenceIds must not be null or empty");
    }
}
