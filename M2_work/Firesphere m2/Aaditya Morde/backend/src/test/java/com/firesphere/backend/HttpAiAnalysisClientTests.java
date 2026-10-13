package com.firesphere.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.firesphere.backend.ai.client.HttpAiAnalysisClient;
import com.firesphere.backend.ai.dto.ExternalAiRequest;
import com.firesphere.backend.ai.dto.ExternalAiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HttpAiAnalysisClientTests {

    private HttpAiAnalysisClient client;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        client = new HttpAiAnalysisClient("http://mock-ai:8000", "test-api-key", 1000, 1000);
        
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    void successfulAnalysis() throws Exception {
        ExternalAiRequest req = new ExternalAiRequest(UUID.randomUUID(), UUID.randomUUID(), "doc-ref-1", "TEST");
        ExternalAiResponse expectedRes = new ExternalAiResponse("SUCCESS", "yolo", "1.0", OffsetDateTime.now(), List.of(new ExternalAiResponse.Finding("Extinguisher", 0.9)), null);

        mockServer.expect(MockRestRequestMatchers.requestTo("http://mock-ai:8000/api/v1/analyze"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                .andExpect(MockRestRequestMatchers.header("X-API-KEY", "test-api-key"))
                .andRespond(MockRestResponseCreators.withSuccess(objectMapper.writeValueAsString(expectedRes), MediaType.APPLICATION_JSON));

        ExternalAiResponse res = client.analyze(req);

        mockServer.verify();
        assertThat(res.status()).isEqualTo("SUCCESS");
        assertThat(res.findings()).hasSize(1);
    }

    @Test
    void handles500Error() {
        ExternalAiRequest req = new ExternalAiRequest(UUID.randomUUID(), UUID.randomUUID(), "doc-ref-1", "TEST");

        mockServer.expect(MockRestRequestMatchers.requestTo("http://mock-ai:8000/api/v1/analyze"))
                .andRespond(MockRestResponseCreators.withServerError());

        ExternalAiResponse res = client.analyze(req);

        mockServer.verify();
        assertThat(res.status()).isEqualTo("FAILED");
        assertThat(res.errorDetail()).contains("Error communicating with AI service");
    }

    @Test
    void handles400Error() {
        ExternalAiRequest req = new ExternalAiRequest(UUID.randomUUID(), UUID.randomUUID(), "doc-ref-1", "TEST");

        mockServer.expect(MockRestRequestMatchers.requestTo("http://mock-ai:8000/api/v1/analyze"))
                .andRespond(MockRestResponseCreators.withBadRequest());

        ExternalAiResponse res = client.analyze(req);

        mockServer.verify();
        assertThat(res.status()).isEqualTo("FAILED");
        assertThat(res.errorDetail()).contains("Error communicating with AI service");
    }
}
