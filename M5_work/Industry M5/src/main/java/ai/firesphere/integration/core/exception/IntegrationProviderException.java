package ai.firesphere.integration.core.exception;

import ai.firesphere.integration.core.model.IntegrationError;
import ai.firesphere.integration.core.model.IntegrationErrorCategory;

/**
 * Exception thrown when an external provider returns an operational or network failure.
 */
public class IntegrationProviderException extends IntegrationException {

    public IntegrationProviderException(String providerName, String providerErrorCode, String message, boolean retryable) {
        super(new IntegrationError(
                providerErrorCode != null ? providerErrorCode : "ERR_PROVIDER_FAILURE",
                IntegrationErrorCategory.PROVIDER_ERROR,
                "Provider [" + providerName + "] error: " + message,
                retryable
        ));
    }

    public IntegrationProviderException(String providerName, String message, Throwable cause, boolean retryable) {
        super(new IntegrationError(
                "ERR_PROVIDER_COMMUNICATION",
                IntegrationErrorCategory.PROVIDER_ERROR,
                "Failed to communicate with provider [" + providerName + "]: " + message,
                retryable
        ), cause);
    }
}
