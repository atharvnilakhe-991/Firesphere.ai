package com.firesphere.backend.ai.client;

import com.firesphere.backend.ai.dto.ExternalAiRequest;
import com.firesphere.backend.ai.dto.ExternalAiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class HttpAiAnalysisClient implements AiAnalysisClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String apiKey;

    public HttpAiAnalysisClient(
            @Value("${ai.service.base-url}") String baseUrl,
            @Value("${ai.service.api-key}") String apiKey,
            @Value("${ai.service.connect-timeout-ms:5000}") int connectTimeout,
            @Value("${ai.service.read-timeout-ms:30000}") int readTimeout) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public ExternalAiResponse analyze(ExternalAiRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-KEY", apiKey);

        HttpEntity<ExternalAiRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<ExternalAiResponse> response = restTemplate.exchange(
                    baseUrl + "/api/v1/analyze",
                    HttpMethod.POST,
                    entity,
                    ExternalAiResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                return new ExternalAiResponse("FAILED", null, null, null, null, "AI Service returned error: " + response.getStatusCode());
            }
        } catch (RestClientException e) {
            return new ExternalAiResponse("FAILED", null, null, null, null, "Error communicating with AI service: " + e.getMessage());
        }
    }
}
