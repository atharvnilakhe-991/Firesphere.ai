package ai.firesphere.integration.adapter.payment;

import ai.firesphere.integration.api.payment.PaymentInitiateRequest;
import ai.firesphere.integration.api.payment.PaymentInitiateResult;
import ai.firesphere.integration.api.payment.PaymentIntegration;
import ai.firesphere.integration.api.payment.PaymentReconciliationResult;
import ai.firesphere.integration.api.payment.PaymentStatusResult;
import ai.firesphere.integration.api.payment.PaymentVerificationRequest;
import ai.firesphere.integration.api.payment.PaymentVerificationResult;
import ai.firesphere.integration.api.payment.PaymentWebhookRequest;
import ai.firesphere.integration.api.payment.PaymentWebhookResult;
import ai.firesphere.integration.core.configuration.IntegrationProperties;
import ai.firesphere.integration.core.correlation.CorrelationContext;
import ai.firesphere.integration.core.exception.IntegrationException;
import ai.firesphere.integration.core.idempotency.IdempotencyStore;
import ai.firesphere.integration.core.logging.IntegrationLogger;
import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;
import ai.firesphere.integration.core.retry.IntegrationRetryExecutor;
import ai.firesphere.integration.provider.payment.PaymentProvider;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Adapter implementing PaymentIntegration, mediating between domain code and payment gateway providers.
 */
@Service
public class PaymentIntegrationAdapter implements PaymentIntegration {

    private final PaymentProvider provider;
    private final IntegrationProperties properties;
    private final IntegrationRetryExecutor retryExecutor;
    private final IdempotencyStore idempotencyStore;
    private final IntegrationLogger logger;

    public PaymentIntegrationAdapter(
            PaymentProvider provider,
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
    public IntegrationResponse<PaymentInitiateResult> initiatePayment(IntegrationRequest<PaymentInitiateRequest> request) {
        String corrId = resolveCorrelationId(request);

        // Check idempotency
        if (request != null && request.idempotencyKey() != null) {
            Optional<IntegrationResponse<PaymentInitiateResult>> cached =
                    idempotencyStore.get(request.idempotencyKey(), PaymentInitiateResult.class);
            if (cached.isPresent()) {
                IntegrationResponse<PaymentInitiateResult> hit = cached.get();
                return IntegrationResponse.duplicate(corrId, provider.getProviderName(),
                        "Idempotent payment initiation response replayed", hit.data(), 0L);
            }
        }

        logger.logRequest(provider.getProviderName(), "initiatePayment", request);

        IntegrationResponse<PaymentInitiateResult> response = retryExecutor.executeWithRetry(
                "initiatePayment",
                provider.getProviderName(),
                properties.getPayment().toRetryPolicy(),
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        PaymentInitiateResult result = provider.initiate(request.payload());
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.success(
                                corrId,
                                provider.getProviderName(),
                                result.paymentGatewayOrderId(),
                                result,
                                duration
                        );
                    } catch (IntegrationException ex) {
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.failure(corrId, provider.getProviderName(), ex.getError(), duration);
                    }
                }
        );

        logger.logResponse("initiatePayment", response);

        if (response.isSuccess() && request != null && request.idempotencyKey() != null) {
            idempotencyStore.put(request.idempotencyKey(), response);
        }

        return response;
    }

    @Override
    public IntegrationResponse<PaymentVerificationResult> verifyPayment(IntegrationRequest<PaymentVerificationRequest> request) {
        String corrId = resolveCorrelationId(request);

        if (request != null && request.idempotencyKey() != null) {
            Optional<IntegrationResponse<PaymentVerificationResult>> cached =
                    idempotencyStore.get(request.idempotencyKey(), PaymentVerificationResult.class);
            if (cached.isPresent()) {
                IntegrationResponse<PaymentVerificationResult> hit = cached.get();
                return IntegrationResponse.duplicate(corrId, provider.getProviderName(),
                        "Idempotent payment verification response replayed", hit.data(), 0L);
            }
        }

        logger.logRequest(provider.getProviderName(), "verifyPayment", request);

        IntegrationResponse<PaymentVerificationResult> response = retryExecutor.executeWithRetry(
                "verifyPayment",
                provider.getProviderName(),
                properties.getPayment().toRetryPolicy(),
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        PaymentVerificationResult result = provider.verify(request.payload());
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.success(
                                corrId,
                                provider.getProviderName(),
                                result.paymentTransactionId(),
                                result,
                                duration
                        );
                    } catch (IntegrationException ex) {
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.failure(corrId, provider.getProviderName(), ex.getError(), duration);
                    }
                }
        );

        logger.logResponse("verifyPayment", response);

        if (response.isSuccess() && request != null && request.idempotencyKey() != null) {
            idempotencyStore.put(request.idempotencyKey(), response);
        }

        return response;
    }

    @Override
    public IntegrationResponse<PaymentStatusResult> checkStatus(IntegrationRequest<String> transactionIdRequest) {
        String corrId = resolveCorrelationId(transactionIdRequest);
        logger.logRequest(provider.getProviderName(), "checkStatus", transactionIdRequest);

        IntegrationResponse<PaymentStatusResult> response = retryExecutor.executeWithRetry(
                "checkStatus",
                provider.getProviderName(),
                properties.getPayment().toRetryPolicy(),
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        PaymentStatusResult result = provider.status(transactionIdRequest.payload());
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.success(
                                corrId,
                                provider.getProviderName(),
                                result.paymentTransactionId(),
                                result,
                                duration
                        );
                    } catch (IntegrationException ex) {
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.failure(corrId, provider.getProviderName(), ex.getError(), duration);
                    }
                }
        );

        logger.logResponse("checkStatus", response);
        return response;
    }

    @Override
    public IntegrationResponse<PaymentWebhookResult> processWebhook(IntegrationRequest<PaymentWebhookRequest> request, String signature) {
        String corrId = resolveCorrelationId(request);

        String idempKey = request != null && request.idempotencyKey() != null
                ? request.idempotencyKey().value()
                : (request != null && request.payload() != null ? "webhook_" + request.payload().eventId() : null);

        if (idempKey != null) {
            Optional<IntegrationResponse<PaymentWebhookResult>> cached =
                    idempotencyStore.get(ai.firesphere.integration.core.idempotency.IdempotencyKey.of(idempKey), PaymentWebhookResult.class);
            if (cached.isPresent()) {
                IntegrationResponse<PaymentWebhookResult> hit = cached.get();
                return IntegrationResponse.duplicate(corrId, provider.getProviderName(),
                        "Idempotent webhook response replayed", hit.data(), 0L);
            }
        }

        logger.logRequest(provider.getProviderName(), "processWebhook", request);

        IntegrationResponse<PaymentWebhookResult> response = retryExecutor.executeWithRetry(
                "processWebhook",
                provider.getProviderName(),
                properties.getPayment().toRetryPolicy(),
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        PaymentWebhookResult result = provider.processWebhook(request != null ? request.payload() : null, signature);
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.success(
                                corrId,
                                provider.getProviderName(),
                                result.eventId(),
                                result,
                                duration
                        );
                    } catch (IntegrationException ex) {
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.failure(corrId, provider.getProviderName(), ex.getError(), duration);
                    }
                }
        );

        logger.logResponse("processWebhook", response);

        if (response.isSuccess() && idempKey != null) {
            idempotencyStore.put(ai.firesphere.integration.core.idempotency.IdempotencyKey.of(idempKey), response);
        }

        return response;
    }

    @Override
    public IntegrationResponse<PaymentReconciliationResult> reconcilePayment(IntegrationRequest<String> transactionIdRequest) {
        String corrId = resolveCorrelationId(transactionIdRequest);
        logger.logRequest(provider.getProviderName(), "reconcilePayment", transactionIdRequest);

        IntegrationResponse<PaymentReconciliationResult> response = retryExecutor.executeWithRetry(
                "reconcilePayment",
                provider.getProviderName(),
                properties.getPayment().toRetryPolicy(),
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        PaymentReconciliationResult result = provider.reconcile(transactionIdRequest != null ? transactionIdRequest.payload() : null);
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.success(
                                corrId,
                                provider.getProviderName(),
                                result.transactionId(),
                                result,
                                duration
                        );
                    } catch (IntegrationException ex) {
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.failure(corrId, provider.getProviderName(), ex.getError(), duration);
                    }
                }
        );

        logger.logResponse("reconcilePayment", response);
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
