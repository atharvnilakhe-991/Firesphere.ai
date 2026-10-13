package ai.firesphere.integration.core.exception;

import ai.firesphere.integration.core.model.IntegrationError;
import ai.firesphere.integration.core.model.IntegrationErrorCategory;

/**
 * Exception thrown when integration configuration or provider credentials are missing/invalid.
 * Non-retryable.
 */
public class IntegrationConfigurationException extends IntegrationException {

    public IntegrationConfigurationException(String providerName, String message) {
        super(new IntegrationError(
                "ERR_CONFIGURATION",
                IntegrationErrorCategory.CONFIGURATION_ERROR,
                "Invalid configuration for provider [" + providerName + "]: " + message,
                false
        ));
    }
}
