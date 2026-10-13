package ai.firesphere.integration.core.model;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Normalized response returned by all integration adapters to business services.
 * Ensures consistent handling of results, errors, timeouts, duplicates, and timing.
 */
public record IntegrationResponse<T>(
        String correlationId,
        IntegrationStatus status,
        String providerName,
        String providerTransactionId,
        T data,
        IntegrationError error,
        Map<String, String> metadata,
        long durationMs,
        Instant timestamp
) {

    public IntegrationResponse {
        if (metadata == null) {
            metadata = Collections.emptyMap();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public boolean isSuccess() {
        return status == IntegrationStatus.SUCCESS;
    }

    public Optional<T> getData() {
        return Optional.ofNullable(data);
    }

    public Optional<IntegrationError> getError() {
        return Optional.ofNullable(error);
    }

    public static <T> IntegrationResponse<T> success(String correlationId, String providerName, String providerTransactionId, T data, long durationMs) {
        return new IntegrationResponse<>(
                correlationId,
                IntegrationStatus.SUCCESS,
                providerName,
                providerTransactionId,
                data,
                null,
                Collections.emptyMap(),
                durationMs,
                Instant.now()
        );
    }

    public static <T> IntegrationResponse<T> success(String correlationId, String providerName, String providerTransactionId, T data, Map<String, String> metadata, long durationMs) {
        return new IntegrationResponse<>(
                correlationId,
                IntegrationStatus.SUCCESS,
                providerName,
                providerTransactionId,
                data,
                null,
                metadata,
                durationMs,
                Instant.now()
        );
    }

    public static <T> IntegrationResponse<T> failure(String correlationId, String providerName, IntegrationError error, long durationMs) {
        return new IntegrationResponse<>(
                correlationId,
                IntegrationStatus.FAILED,
                providerName,
                null,
                null,
                error,
                Collections.emptyMap(),
                durationMs,
                Instant.now()
        );
    }

    public static <T> IntegrationResponse<T> timeout(String correlationId, String providerName, String message, long durationMs) {
        return new IntegrationResponse<>(
                correlationId,
                IntegrationStatus.TIMEOUT,
                providerName,
                null,
                null,
                new IntegrationError("ERR_TIMEOUT", IntegrationErrorCategory.TIMEOUT, message, true),
                Collections.emptyMap(),
                durationMs,
                Instant.now()
        );
    }

    public static <T> IntegrationResponse<T> duplicate(String correlationId, String providerName, String message, T cachedData, long durationMs) {
        return new IntegrationResponse<>(
                correlationId,
                IntegrationStatus.DUPLICATE,
                providerName,
                null,
                cachedData,
                new IntegrationError("ERR_DUPLICATE_REQUEST", IntegrationErrorCategory.DUPLICATE_REQUEST, message, false),
                Collections.emptyMap(),
                durationMs,
                Instant.now()
        );
    }
}
