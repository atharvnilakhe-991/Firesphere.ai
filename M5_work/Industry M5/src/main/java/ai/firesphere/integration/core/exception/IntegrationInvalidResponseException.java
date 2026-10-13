package ai.firesphere.integration.core.exception;

import ai.firesphere.integration.core.model.IntegrationError;
import ai.firesphere.integration.core.model.IntegrationErrorCategory;

/**
 * Exception thrown when an external provider returns an unparseable or invalid response.
 * Non-retryable.
 */
public class IntegrationInvalidResponseException extends IntegrationException {

    public IntegrationInvalidResponseException(String providerName, String message) {
        super(new IntegrationError(
                "ERR_INVALID_RESPONSE",
                IntegrationErrorCategory.INVALID_RESPONSE,
                "Provider [" + providerName + "] returned invalid or malformed response: " + message,
                false
        ));
    }

    public IntegrationInvalidResponseException(String providerName, String message, Throwable cause) {
        super(new IntegrationError(
                "ERR_INVALID_RESPONSE",
                IntegrationErrorCategory.INVALID_RESPONSE,
                "Provider [" + providerName + "] returned invalid or malformed response: " + message,
                false
        ), cause);
    }
}
