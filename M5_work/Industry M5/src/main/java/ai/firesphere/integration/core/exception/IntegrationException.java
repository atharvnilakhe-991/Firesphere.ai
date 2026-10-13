package ai.firesphere.integration.core.exception;

import ai.firesphere.integration.core.model.IntegrationError;
import ai.firesphere.integration.core.model.IntegrationErrorCategory;

/**
 * Base runtime exception for all integration-related failures in FireSphere.ai.
 */
public class IntegrationException extends RuntimeException {

    private final IntegrationError error;

    public IntegrationException(String message) {
        super(message);
        this.error = new IntegrationError("ERR_INTEGRATION_GENERAL", IntegrationErrorCategory.UNEXPECTED_ERROR, message, false);
    }

    public IntegrationException(String message, Throwable cause) {
        super(message, cause);
        this.error = new IntegrationError("ERR_INTEGRATION_GENERAL", IntegrationErrorCategory.UNEXPECTED_ERROR, message, false);
    }

    public IntegrationException(IntegrationError error) {
        super(error != null ? error.message() : "Integration failure");
        this.error = error;
    }

    public IntegrationException(IntegrationError error, Throwable cause) {
        super(error != null ? error.message() : "Integration failure", cause);
        this.error = error;
    }

    public IntegrationError getError() {
        return error;
    }
}
