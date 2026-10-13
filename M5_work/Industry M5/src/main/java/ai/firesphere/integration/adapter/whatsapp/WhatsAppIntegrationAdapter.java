package ai.firesphere.integration.adapter.whatsapp;

import ai.firesphere.integration.api.whatsapp.WhatsAppIntegration;
import ai.firesphere.integration.api.whatsapp.WhatsAppMessageRequest;
import ai.firesphere.integration.api.whatsapp.WhatsAppMessageResult;
import ai.firesphere.integration.core.configuration.IntegrationProperties;
import ai.firesphere.integration.core.correlation.CorrelationContext;
import ai.firesphere.integration.core.exception.IntegrationException;
import ai.firesphere.integration.core.idempotency.IdempotencyStore;
import ai.firesphere.integration.core.logging.IntegrationLogger;
import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;
import ai.firesphere.integration.core.retry.IntegrationRetryExecutor;
import ai.firesphere.integration.provider.whatsapp.WhatsAppProvider;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Adapter implementing WhatsAppIntegration, mediating between domain code and WhatsApp providers.
 */
@Service
public class WhatsAppIntegrationAdapter implements WhatsAppIntegration {

    private final WhatsAppProvider provider;
    private final IntegrationProperties properties;
    private final IntegrationRetryExecutor retryExecutor;
    private final IdempotencyStore idempotencyStore;
    private final IntegrationLogger logger;

    public WhatsAppIntegrationAdapter(
            WhatsAppProvider provider,
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
    public IntegrationResponse<WhatsAppMessageResult> sendWhatsApp(IntegrationRequest<WhatsAppMessageRequest> request) {
        String corrId = resolveCorrelationId(request);

        if (request != null && request.idempotencyKey() != null) {
            Optional<IntegrationResponse<WhatsAppMessageResult>> cached =
                    idempotencyStore.get(request.idempotencyKey(), WhatsAppMessageResult.class);
            if (cached.isPresent()) {
                IntegrationResponse<WhatsAppMessageResult> hit = cached.get();
                return IntegrationResponse.duplicate(corrId, provider.getProviderName(),
                        "Idempotent WhatsApp send response replayed", hit.data(), 0L);
            }
        }

        logger.logRequest(provider.getProviderName(), "sendWhatsApp", request);

        IntegrationResponse<WhatsAppMessageResult> response = retryExecutor.executeWithRetry(
                "sendWhatsApp",
                provider.getProviderName(),
                properties.getWhatsapp().toRetryPolicy(),
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        WhatsAppMessageResult result = provider.send(request.payload());
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

        logger.logResponse("sendWhatsApp", response);

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
