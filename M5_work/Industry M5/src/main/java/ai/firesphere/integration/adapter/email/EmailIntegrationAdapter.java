package ai.firesphere.integration.adapter.email;

import ai.firesphere.integration.api.email.EmailIntegration;
import ai.firesphere.integration.api.email.EmailMessageRequest;
import ai.firesphere.integration.api.email.EmailMessageResult;
import ai.firesphere.integration.core.configuration.IntegrationProperties;
import ai.firesphere.integration.core.correlation.CorrelationContext;
import ai.firesphere.integration.core.exception.IntegrationException;
import ai.firesphere.integration.core.idempotency.IdempotencyStore;
import ai.firesphere.integration.core.logging.IntegrationLogger;
import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;
import ai.firesphere.integration.core.retry.IntegrationRetryExecutor;
import ai.firesphere.integration.provider.email.EmailProvider;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Adapter implementing EmailIntegration, mediating between domain code and transactional Email providers.
 */
@Service
public class EmailIntegrationAdapter implements EmailIntegration {

    private final EmailProvider provider;
    private final IntegrationProperties properties;
    private final IntegrationRetryExecutor retryExecutor;
    private final IdempotencyStore idempotencyStore;
    private final IntegrationLogger logger;

    public EmailIntegrationAdapter(
            EmailProvider provider,
            IntegrationProperties properties,
            IntegrationRetryExecutor retryExecutor,
            IdempotencyStore idempotencyStore,
            IntegrationLogger logger
    ) {
        this.provider = provider;
        this.properties = properties;
        this.retryExecutor = retryExecutor;
        this.idempotencyStore = idempotencyStore;
        this.logger = logger;
    }

    @Override
    public IntegrationResponse<EmailMessageResult> sendEmail(IntegrationRequest<EmailMessageRequest> request) {
        String corrId = resolveCorrelationId(request);

        if (request != null && request.idempotencyKey() != null) {
            Optional<IntegrationResponse<EmailMessageResult>> cached =
                    idempotencyStore.get(request.idempotencyKey(), EmailMessageResult.class);
            if (cached.isPresent()) {
                IntegrationResponse<EmailMessageResult> hit = cached.get();
                return IntegrationResponse.duplicate(corrId, provider.getProviderName(),
                        "Idempotent Email send response replayed", hit.data(), 0L);
            }
        }

        logger.logRequest(provider.getProviderName(), "sendEmail", request);

        IntegrationResponse<EmailMessageResult> response = retryExecutor.executeWithRetry(
                "sendEmail",
                provider.getProviderName(),
                properties.getEmail().toRetryPolicy(),
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        EmailMessageResult result = provider.send(request.payload());
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.success(
                                corrId,
                                provider.getProviderName(),
                                result.externalMessageId(),
                                result,
                                duration
                        );
                    } catch (IntegrationException ex) {
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.failure(corrId, provider.getProviderName(), ex.getError(), duration);
                    }
                }
        );

        logger.logResponse("sendEmail", response);

        if (response.isSuccess() && request != null && request.idempotencyKey() != null) {
            idempotencyStore.put(request.idempotencyKey(), response);
        }

        return response;
    }

    private <T> String resolveCorrelationId(IntegrationRequest<T> request) {
        if (request != null && request.correlationId() != null && !request.correlationId().isBlank()) {
            CorrelationContext.set(request.correlationId());
            return request.correlationId();
        }
        return CorrelationContext.getOrGenerate();
    }
}
