package ai.firesphere.integration.core.retry;

/**
 * Immutable configuration for integration retry execution.
 */
public record RetryPolicy(
        int maxAttempts,
        long initialDelayMs,
        double backoffMultiplier
) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            maxAttempts = 1;
        }
        if (initialDelayMs < 0) {
            initialDelayMs = 0;
        }
        if (backoffMultiplier < 1.0) {
            backoffMultiplier = 1.0;
        }
    }

    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(3, 50, 1.5);
    }

    public static RetryPolicy noRetry() {
        return new RetryPolicy(1, 0, 1.0);
    }
}
