package ai.firesphere.integration.adapter.sms;

import ai.firesphere.integration.api.sms.SmsIntegration;
import ai.firesphere.integration.api.sms.SmsMessageRequest;
import ai.firesphere.integration.api.sms.SmsMessageResult;
import ai.firesphere.integration.core.configuration.IntegrationProperties;
import ai.firesphere.integration.core.correlation.CorrelationContext;
import ai.firesphere.integration.core.exception.IntegrationException;
import ai.firesphere.integration.core.idempotency.IdempotencyStore;
import ai.firesphere.integration.core.logging.IntegrationLogger;
import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;
import ai.firesphere.integration.core.retry.IntegrationRetryExecutor;
import ai.firesphere.integration.provider.sms.SmsProvider;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Adapter implementing SmsIntegration, mediating between domain code and SMS providers.
 */
@Service
public class SmsIntegrationAdapter implements SmsIntegration {

    private final SmsProvider provider;
    private final IntegrationProperties properties;
    private final IntegrationRetryExecutor retryExecutor;
    private final IdempotencyStore idempotencyStore;
    private final IntegrationLogger logger;

    public SmsIntegrationAdapter(
            SmsProvider provider,
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
    public IntegrationResponse<SmsMessageResult> sendSms(IntegrationRequest<SmsMessageRequest> request) {
        String corrId = resolveCorrelationId(request);

        if (request != null && request.idempotencyKey() != null) {
            Optional<IntegrationResponse<SmsMessageResult>> cached =
                    idempotencyStore.get(request.idempotencyKey(), SmsMessageResult.class);
            if (cached.isPresent()) {
                IntegrationResponse<SmsMessageResult> hit = cached.get();
                return IntegrationResponse.duplicate(corrId, provider.getProviderName(),
                        "Idempotent SMS send response replayed", hit.data(), 0L);
            }
        }

        logger.logRequest(provider.getProviderName(), "sendSms", request);

        IntegrationResponse<SmsMessageResult> response = retryExecutor.executeWithRetry(
                "sendSms",
                provider.getProviderName(),
                properties.getSms().toRetryPolicy(),
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        SmsMessageResult result = provider.send(request.payload());
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

        logger.logResponse("sendSms", response);

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
