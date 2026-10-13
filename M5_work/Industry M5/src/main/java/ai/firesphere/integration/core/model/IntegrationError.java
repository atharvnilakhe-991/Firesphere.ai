package ai.firesphere.integration.core.model;

/**
 * Standard error representation for integration failures.
 */
public record IntegrationError(
        IntegrationErrorCategory category,
        String code,
        String message,
        boolean retryable
) {
    public IntegrationError(String code, IntegrationErrorCategory category, String message, boolean retryable) {
        this(category, code, message, retryable);
    }

    public static IntegrationError timeout(String message) {
        return new IntegrationError(IntegrationErrorCategory.TIMEOUT, "TIMEOUT", message, true);
    }

    public static IntegrationError providerError(String code, String message, boolean retryable) {
        return new IntegrationError(IntegrationErrorCategory.PROVIDER_ERROR, code, message, retryable);
    }

    public static IntegrationError invalidResponse(String message) {
        return new IntegrationError(IntegrationErrorCategory.INVALID_RESPONSE, "INVALID_RESPONSE", message, false);
    }

    public static IntegrationError configurationError(String message) {
        return new IntegrationError(IntegrationErrorCategory.CONFIGURATION_ERROR, "CONFIG_ERROR", message, false);
    }

    public static IntegrationError duplicateRequest(String message) {
        return new IntegrationError(IntegrationErrorCategory.DUPLICATE_REQUEST, "DUPLICATE_REQUEST", message, false);
    }

    public static IntegrationError unexpected(String message) {
        return new IntegrationError(IntegrationErrorCategory.UNEXPECTED_ERROR, "UNEXPECTED_ERROR", message, true);
    }
}
