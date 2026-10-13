package com.firesphere.backend.ai.client;

import com.firesphere.backend.ai.dto.ExternalAiRequest;
import com.firesphere.backend.ai.dto.ExternalAiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class HttpAiAnalysisClient implements AiAnalysisClient {

    private static final Logger log = LoggerFactory.getLogger(HttpAiAnalysisClient.class);
    private static final String ANALYSIS_ENDPOINT = "/internal/ai/inspection-analysis";

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
        headers.set("X-AI-Service-Token", apiKey);

        HttpEntity<ExternalAiRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<ExternalAiResponse> response = restTemplate.exchange(
                    baseUrl + ANALYSIS_ENDPOINT,
                    HttpMethod.POST,
                    entity,
                    ExternalAiResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                return ExternalAiResponse.failed(
                        "EMPTY_RESPONSE",
                        "AI service returned empty response payload"
                );
            }
        } catch (HttpClientErrorException.Unauthorized e) {
            log.error("Authentication rejected by AI service (HTTP 401). Verify configured service credentials.");
            return ExternalAiResponse.failed(
                    "AI_UNAUTHORIZED",
                    "Service authentication failed: Missing or invalid X-AI-Service-Token credential."
            );
        } catch (HttpClientErrorException.UnprocessableEntity e) {
            log.warn("AI service rejected request payload validation (HTTP 422).");
            return ExternalAiResponse.failed(
                    "AI_VALIDATION_ERROR",
                    "Request validation failed: " + e.getResponseBodyAsString()
            );
        } catch (HttpClientErrorException e) {
            log.warn("Client error returned by AI service: status={}", e.getStatusCode());
            return ExternalAiResponse.failed(
                    "AI_CLIENT_ERROR",
                    "AI service rejected request: " + e.getStatusCode()
            );
        } catch (HttpServerErrorException e) {
            log.error("Server error returned by AI service: status={}", e.getStatusCode());
            return ExternalAiResponse.failed(
                    "AI_SERVER_ERROR",
                    "AI service encountered internal processing failure: " + e.getStatusCode()
            );
        } catch (ResourceAccessException e) {
            log.error("Network or timeout error communicating with AI service: {}", e.getMessage());
            return ExternalAiResponse.failed(
                    "AI_TIMEOUT_OR_UNAVAILABLE",
                    "Error communicating with AI service: " + e.getMessage()
            );
        } catch (RestClientException e) {
            log.error("Unexpected REST client exception while calling AI service: {}", e.getMessage());
            return ExternalAiResponse.failed(
                    "AI_COMMUNICATION_ERROR",
                    "Error communicating with AI service: " + e.getMessage()
            );
        }
    }
}
