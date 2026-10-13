package ai.firesphere.integration.adapter.signature;

import ai.firesphere.integration.api.signature.DigitalSignatureIntegration;
import ai.firesphere.integration.api.signature.SignatureRequest;
import ai.firesphere.integration.api.signature.SignatureResult;
import ai.firesphere.integration.api.signature.SignatureVerificationRequest;
import ai.firesphere.integration.api.signature.SignatureVerificationResult;
import ai.firesphere.integration.core.configuration.IntegrationProperties;
import ai.firesphere.integration.core.correlation.CorrelationContext;
import ai.firesphere.integration.core.exception.IntegrationException;
import ai.firesphere.integration.core.idempotency.IdempotencyStore;
import ai.firesphere.integration.core.logging.IntegrationLogger;
import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;
import ai.firesphere.integration.core.retry.IntegrationRetryExecutor;
import ai.firesphere.integration.provider.signature.DigitalSignatureProvider;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Adapter implementing DigitalSignatureIntegration, mediating between domain code and PKI signing engines.
 */
@Service
public class DigitalSignatureIntegrationAdapter implements DigitalSignatureIntegration {

    private final DigitalSignatureProvider provider;
    private final IntegrationProperties properties;
    private final IntegrationRetryExecutor retryExecutor;
    private final IdempotencyStore idempotencyStore;
    private final IntegrationLogger logger;

    public DigitalSignatureIntegrationAdapter(
            DigitalSignatureProvider provider,
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
    public IntegrationResponse<SignatureResult> signDocument(IntegrationRequest<SignatureRequest> request) {
        String corrId = resolveCorrelationId(request);

        if (request != null && request.idempotencyKey() != null) {
            Optional<IntegrationResponse<SignatureResult>> cached =
                    idempotencyStore.get(request.idempotencyKey(), SignatureResult.class);
            if (cached.isPresent()) {
                IntegrationResponse<SignatureResult> hit = cached.get();
                return IntegrationResponse.duplicate(corrId, provider.getProviderName(),
                        "Idempotent signature response replayed", hit.data(), 0L);
            }
        }

        logger.logRequest(provider.getProviderName(), "signDocument", request);

        IntegrationResponse<SignatureResult> response = retryExecutor.executeWithRetry(
                "signDocument",
                provider.getProviderName(),
                properties.getDigitalSignature().toRetryPolicy(),
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        SignatureResult result = provider.sign(request.payload());
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.success(
                                corrId,
                                provider.getProviderName(),
                                result.signatureToken(),
                                result,
                                duration
                        );
                    } catch (IntegrationException ex) {
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.failure(corrId, provider.getProviderName(), ex.getError(), duration);
                    }
                }
        );

        logger.logResponse("signDocument", response);

        if (response.isSuccess() && request != null && request.idempotencyKey() != null) {
            idempotencyStore.put(request.idempotencyKey(), response);
        }

        return response;
    }

    @Override
    public IntegrationResponse<SignatureVerificationResult> verifySignature(IntegrationRequest<SignatureVerificationRequest> request) {
        String corrId = resolveCorrelationId(request);

        if (request != null && request.idempotencyKey() != null) {
            Optional<IntegrationResponse<SignatureVerificationResult>> cached =
                    idempotencyStore.get(request.idempotencyKey(), SignatureVerificationResult.class);
            if (cached.isPresent()) {
                IntegrationResponse<SignatureVerificationResult> hit = cached.get();
                return IntegrationResponse.duplicate(corrId, provider.getProviderName(),
                        "Idempotent signature verification response replayed", hit.data(), 0L);
            }
        }

        logger.logRequest(provider.getProviderName(), "verifySignature", request);

        IntegrationResponse<SignatureVerificationResult> response = retryExecutor.executeWithRetry(
                "verifySignature",
                provider.getProviderName(),
                properties.getDigitalSignature().toRetryPolicy(),
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        SignatureVerificationResult result = provider.verify(request.payload());
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.success(
                                corrId,
                                provider.getProviderName(),
                                "SIG-VERIF-OK",
                                result,
                                duration
                        );
                    } catch (IntegrationException ex) {
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.failure(corrId, provider.getProviderName(), ex.getError(), duration);
                    }
                }
        );

        logger.logResponse("verifySignature", response);

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
