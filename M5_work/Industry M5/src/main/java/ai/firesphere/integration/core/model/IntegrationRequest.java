package ai.firesphere.integration.core.model;

import ai.firesphere.integration.core.idempotency.IdempotencyKey;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Normalized container for outbound integration requests.
 * Decouples domain logic from provider-specific transmission details.
 */
public record IntegrationRequest<T>(
        String correlationId,
        IdempotencyKey idempotencyKey,
        T payload,
        Map<String, String> metadata,
        Instant timestamp
) {

    public IntegrationRequest {
        Objects.requireNonNull(payload, "Payload must not be null");
        if (metadata == null) {
            metadata = Collections.emptyMap();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public static <T> IntegrationRequest<T> of(T payload) {
        return new IntegrationRequest<>(null, null, payload, Collections.emptyMap(), Instant.now());
    }

    public static <T> IntegrationRequest<T> of(String correlationId, IdempotencyKey idempotencyKey, T payload) {
        return new IntegrationRequest<>(correlationId, idempotencyKey, payload, Collections.emptyMap(), Instant.now());
    }

    public static <T> IntegrationRequest<T> of(String correlationId, IdempotencyKey idempotencyKey, T payload, Map<String, String> metadata) {
        return new IntegrationRequest<>(correlationId, idempotencyKey, payload, metadata, Instant.now());
    }
}
