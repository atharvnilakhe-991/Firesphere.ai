package ai.firesphere.integration.adapter.maps;

import ai.firesphere.integration.api.maps.GeocodingRequest;
import ai.firesphere.integration.api.maps.GeocodingResult;
import ai.firesphere.integration.api.maps.MapsIntegration;
import ai.firesphere.integration.api.maps.ReverseGeocodingRequest;
import ai.firesphere.integration.api.maps.ReverseGeocodingResult;
import ai.firesphere.integration.api.maps.RouteMatrixRequest;
import ai.firesphere.integration.api.maps.RouteMatrixResult;
import ai.firesphere.integration.core.configuration.IntegrationProperties;
import ai.firesphere.integration.core.correlation.CorrelationContext;
import ai.firesphere.integration.core.exception.IntegrationException;
import ai.firesphere.integration.core.idempotency.IdempotencyStore;
import ai.firesphere.integration.core.logging.IntegrationLogger;
import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;
import ai.firesphere.integration.core.retry.IntegrationRetryExecutor;
import ai.firesphere.integration.provider.maps.MapsProvider;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Adapter implementing MapsIntegration, mediating between domain code and Maps / Routing providers.
 */
@Service
public class MapsIntegrationAdapter implements MapsIntegration {

    private final MapsProvider provider;
    private final IntegrationProperties properties;
    private final IntegrationRetryExecutor retryExecutor;
    private final IdempotencyStore idempotencyStore;
    private final IntegrationLogger logger;

    public MapsIntegrationAdapter(
            MapsProvider provider,
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
    public IntegrationResponse<GeocodingResult> geocode(IntegrationRequest<GeocodingRequest> request) {
        String corrId = resolveCorrelationId(request);

        if (request != null && request.idempotencyKey() != null) {
            Optional<IntegrationResponse<GeocodingResult>> cached =
                    idempotencyStore.get(request.idempotencyKey(), GeocodingResult.class);
            if (cached.isPresent()) {
                IntegrationResponse<GeocodingResult> hit = cached.get();
                return IntegrationResponse.duplicate(corrId, provider.getProviderName(),
                        "Idempotent geocode response replayed", hit.data(), 0L);
            }
        }

        logger.logRequest(provider.getProviderName(), "geocode", request);

        IntegrationResponse<GeocodingResult> response = retryExecutor.executeWithRetry(
                "geocode",
                provider.getProviderName(),
                properties.getMaps().toRetryPolicy(),
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        GeocodingResult result = provider.geocode(request.payload());
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.success(
                                corrId,
                                provider.getProviderName(),
                                result.placeId(),
                                result,
                                duration
                        );
                    } catch (IntegrationException ex) {
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.failure(corrId, provider.getProviderName(), ex.getError(), duration);
                    }
                }
        );

        logger.logResponse("geocode", response);

        if (response.isSuccess() && request != null && request.idempotencyKey() != null) {
            idempotencyStore.put(request.idempotencyKey(), response);
        }

        return response;
    }

    @Override
    public IntegrationResponse<ReverseGeocodingResult> reverseGeocode(IntegrationRequest<ReverseGeocodingRequest> request) {
        String corrId = resolveCorrelationId(request);

        if (request != null && request.idempotencyKey() != null) {
            Optional<IntegrationResponse<ReverseGeocodingResult>> cached =
                    idempotencyStore.get(request.idempotencyKey(), ReverseGeocodingResult.class);
            if (cached.isPresent()) {
                IntegrationResponse<ReverseGeocodingResult> hit = cached.get();
                return IntegrationResponse.duplicate(corrId, provider.getProviderName(),
                        "Idempotent reverse geocode response replayed", hit.data(), 0L);
            }
        }

        logger.logRequest(provider.getProviderName(), "reverseGeocode", request);

        IntegrationResponse<ReverseGeocodingResult> response = retryExecutor.executeWithRetry(
                "reverseGeocode",
                provider.getProviderName(),
                properties.getMaps().toRetryPolicy(),
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        ReverseGeocodingResult result = provider.reverseGeocode(request.payload());
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.success(
                                corrId,
                                provider.getProviderName(),
                                "GEO-REV-OK",
                                result,
                                duration
                        );
                    } catch (IntegrationException ex) {
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.failure(corrId, provider.getProviderName(), ex.getError(), duration);
                    }
                }
        );

        logger.logResponse("reverseGeocode", response);

        if (response.isSuccess() && request != null && request.idempotencyKey() != null) {
            idempotencyStore.put(request.idempotencyKey(), response);
        }

        return response;
    }

    @Override
    public IntegrationResponse<RouteMatrixResult> calculateRoute(IntegrationRequest<RouteMatrixRequest> request) {
        String corrId = resolveCorrelationId(request);

        if (request != null && request.idempotencyKey() != null) {
            Optional<IntegrationResponse<RouteMatrixResult>> cached =
                    idempotencyStore.get(request.idempotencyKey(), RouteMatrixResult.class);
            if (cached.isPresent()) {
                IntegrationResponse<RouteMatrixResult> hit = cached.get();
                return IntegrationResponse.duplicate(corrId, provider.getProviderName(),
                        "Idempotent route calculation response replayed", hit.data(), 0L);
            }
        }

        logger.logRequest(provider.getProviderName(), "calculateRoute", request);

        IntegrationResponse<RouteMatrixResult> response = retryExecutor.executeWithRetry(
                "calculateRoute",
                provider.getProviderName(),
                properties.getMaps().toRetryPolicy(),
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        RouteMatrixResult result = provider.calculateRoute(request.payload());
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.success(
                                corrId,
                                provider.getProviderName(),
                                "ROUTE-OK",
                                result,
                                duration
                        );
                    } catch (IntegrationException ex) {
                        long duration = System.currentTimeMillis() - start;
                        return IntegrationResponse.failure(corrId, provider.getProviderName(), ex.getError(), duration);
                    }
                }
        );

        logger.logResponse("calculateRoute", response);

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
