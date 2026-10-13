package ai.firesphere.integration.core.exception;

import ai.firesphere.integration.core.model.IntegrationError;
import ai.firesphere.integration.core.model.IntegrationErrorCategory;

/**
 * Exception thrown when an external provider call exceeds the configured timeout threshold.
 * Marked as retryable.
 */
public class IntegrationTimeoutException extends IntegrationException {

    public IntegrationTimeoutException(String providerName, long timeoutMs) {
        super(new IntegrationError(
                "ERR_TIMEOUT",
                IntegrationErrorCategory.TIMEOUT,
                "Integration call to provider [" + providerName + "] timed out after " + timeoutMs + " ms",
                true
        ));
    }

    public IntegrationTimeoutException(String message, Throwable cause) {
        super(new IntegrationError("ERR_TIMEOUT", IntegrationErrorCategory.TIMEOUT, message, true), cause);
    }
}
