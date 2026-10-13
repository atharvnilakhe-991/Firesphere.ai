package ai.firesphere.integration.core.correlation;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Thread-local context tracking request correlation IDs across integration layers.
 */
public final class CorrelationContext {

    private static final ThreadLocal<String> CURRENT_CORRELATION_ID = new ThreadLocal<>();

    private CorrelationContext() {}

    public static String get() {
        String id = CURRENT_CORRELATION_ID.get();
        if (id == null || id.isBlank()) {
            id = generate();
            CURRENT_CORRELATION_ID.set(id);
        }
        return id;
    }

    public static String getOrGenerate() {
        return get();
    }

    public static void set(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            CURRENT_CORRELATION_ID.set(correlationId.trim());
        } else {
            CURRENT_CORRELATION_ID.set(generate());
        }
    }

    public static String generate() {
        return "CORR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public static void clear() {
        CURRENT_CORRELATION_ID.remove();
    }

    public static <T> T executeWith(String correlationId, Supplier<T> action) {
        String previous = CURRENT_CORRELATION_ID.get();
        try {
            set(correlationId);
            return action.get();
        } finally {
            if (previous != null) {
                CURRENT_CORRELATION_ID.set(previous);
            } else {
                CURRENT_CORRELATION_ID.remove();
            }
        }
    }
}
