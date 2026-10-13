package ai.firesphere.integration.core.idempotency;

import ai.firesphere.integration.core.model.IntegrationResponse;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory implementation of IdempotencyStore.
 * Enables duplicate detection and replay of prior responses without requiring database writes.
 */
@Component
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private final ConcurrentHashMap<String, IntegrationResponse<?>> cache = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<IntegrationResponse<T>> get(IdempotencyKey key, Class<T> responseType) {
        if (key == null) {
            return Optional.empty();
        }
        IntegrationResponse<?> cached = cache.get(key.value());
        if (cached == null) {
            return Optional.empty();
        }
        return Optional.of((IntegrationResponse<T>) cached);
    }

    @Override
    public <T> void put(IdempotencyKey key, IntegrationResponse<T> response) {
        if (key != null && response != null) {
            cache.put(key.value(), response);
        }
    }

    @Override
    public boolean contains(IdempotencyKey key) {
        if (key == null) {
            return false;
        }
        return cache.containsKey(key.value());
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
