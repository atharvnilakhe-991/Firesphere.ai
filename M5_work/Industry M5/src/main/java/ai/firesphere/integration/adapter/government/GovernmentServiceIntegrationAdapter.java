package ai.firesphere.integration.adapter.government;

import ai.firesphere.integration.api.government.CadStatusQueryRequest;
import ai.firesphere.integration.api.government.CadStatusQueryResult;
import ai.firesphere.integration.api.government.CadTransmitRequest;
import ai.firesphere.integration.api.government.CadTransmitResult;
import ai.firesphere.integration.api.government.ComplianceLookupRequest;
import ai.firesphere.integration.api.government.ComplianceLookupResult;
import ai.firesphere.integration.api.government.GovernmentServiceIntegration;
import ai.firesphere.integration.core.configuration.IntegrationProperties;
import ai.firesphere.integration.core.correlation.CorrelationContext;
import ai.firesphere.integration.core.exception.IntegrationException;
import ai.firesphere.integration.core.idempotency.IdempotencyStore;
import ai.firesphere.integration.core.logging.IntegrationLogger;
import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;
import ai.firesphere.integration.core.retry.IntegrationRetryExecutor;
import ai.firesphere.integration.provider.government.GovernmentServiceProvider;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Adapter implementing GovernmentServiceIntegration, mediating between domain code and external CAD / Compliance systems.
 */
@Service
public class GovernmentServiceIntegrationAdapter implements GovernmentServiceIntegration {

    private final GovernmentServiceProvider provider;
    private final IntegrationProperties properties;
    private final IntegrationRetryExecutor retryExecutor;
    private final IdempotencyStore idempotencyStore;
    private final IntegrationLogger logger;

    public GovernmentServiceIntegrationAdapter(
            GovernmentServiceProvider provider,
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
    public IntegrationResponse<CadTransmitResult> transmitCadIncident(IntegrationRequest<CadTransmitRequest> request) {
        String corrId = resolveCorrelationId(request);

        if (request != null && request.idempotencyKey() != null) {
            Optional<IntegrationResponse<CadTransmitResult>> cached =
                    idempotencyStore.get(request.idempotencyKey(), CadTransmitResult.class);
            if (cached.isPresent()) {
                IntegrationResponse<CadTransmitResult> hit = cached.get();
                return IntegrationResponse.duplicate(corrId, provider.getProviderName(),
                        "Idempotent CAD transmission response replayed", hit.data(), 0L);
            }
        }

        logger.logRequest(provider.getProviderName(), "transmitCadIncident", request);

        IntegrationResponse<CadTransmitResult> response = retryExecutor.executeWithRetry(
                "transmitCadIncident",
                provider.getProviderName(),
                properties.getCad().toRetryPolicy(),
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        CadTransmitResult result = provider.transmitCad(request.payload());
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.success(
                                corrId,
                                provider.getProviderName(),
                                result.externalCadIncidentId(),
                                result,
                                duration
                        );
                    } catch (IntegrationException ex) {
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.failure(corrId, provider.getProviderName(), ex.getError(), duration);
                    }
                }
        );

        logger.logResponse("transmitCadIncident", response);

        if (response.isSuccess() && request != null && request.idempotencyKey() != null) {
            idempotencyStore.put(request.idempotencyKey(), response);
        }

        return response;
    }

    @Override
    public IntegrationResponse<CadStatusQueryResult> queryCadStatus(IntegrationRequest<CadStatusQueryRequest> request) {
        String corrId = resolveCorrelationId(request);
        logger.logRequest(provider.getProviderName(), "queryCadStatus", request);

        IntegrationResponse<CadStatusQueryResult> response = retryExecutor.executeWithRetry(
                "queryCadStatus",
                provider.getProviderName(),
                properties.getCad().toRetryPolicy(),
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        CadStatusQueryResult result = provider.queryStatus(request.payload());
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.success(
                                corrId,
                                provider.getProviderName(),
                                result.externalCadIncidentId(),
                                result,
                                duration
                        );
                    } catch (IntegrationException ex) {
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.failure(corrId, provider.getProviderName(), ex.getError(), duration);
                    }
                }
        );

        logger.logResponse("queryCadStatus", response);
        return response;
    }

    @Override
    public IntegrationResponse<ComplianceLookupResult> verifyCompliance(IntegrationRequest<ComplianceLookupRequest> request) {
        String corrId = resolveCorrelationId(request);
        logger.logRequest(provider.getProviderName(), "verifyCompliance", request);

        IntegrationResponse<ComplianceLookupResult> response = retryExecutor.executeWithRetry(
                "verifyCompliance",
                provider.getProviderName(),
                properties.getCad().toRetryPolicy(),
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        ComplianceLookupResult result = provider.lookupCompliance(request.payload());
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.success(
                                corrId,
                                provider.getProviderName(),
                                result.buildingTaxId(),
                                result,
                                duration
                        );
                    } catch (IntegrationException ex) {
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.failure(corrId, provider.getProviderName(), ex.getError(), duration);
                    }
                }
        );

        logger.logResponse("verifyCompliance", response);
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
