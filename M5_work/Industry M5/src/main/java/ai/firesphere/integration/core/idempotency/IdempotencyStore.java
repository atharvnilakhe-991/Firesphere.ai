package ai.firesphere.integration.core.idempotency;

import ai.firesphere.integration.core.model.IntegrationResponse;

import java.util.Optional;

/**
 * Contract for storing and retrieving responses by idempotency key.
 * Prevents duplicate processing of side-effectful external integrations.
 */
public interface IdempotencyStore {

    <T> Optional<IntegrationResponse<T>> get(IdempotencyKey key, Class<T> responseType);

    <T> void put(IdempotencyKey key, IntegrationResponse<T> response);

    boolean contains(IdempotencyKey key);

    void clear();
}
