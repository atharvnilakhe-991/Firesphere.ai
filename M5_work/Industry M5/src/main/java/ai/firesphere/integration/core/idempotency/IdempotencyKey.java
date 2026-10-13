package ai.firesphere.integration.core.idempotency;

import java.util.Objects;
import java.util.UUID;

public record IdempotencyKey(String value) {

    public IdempotencyKey {
        Objects.requireNonNull(value, "Idempotency key value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Idempotency key value must not be blank");
        }
    }

    public static IdempotencyKey of(String value) {
        return new IdempotencyKey(value);
    }

    public static IdempotencyKey generate() {
        return new IdempotencyKey("IDEMP-" + UUID.randomUUID().toString());
    }
}
