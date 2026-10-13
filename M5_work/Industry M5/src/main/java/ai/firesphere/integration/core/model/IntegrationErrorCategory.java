package ai.firesphere.integration.core.model;

/**
 * Categorization of external integration errors.
 */
public enum IntegrationErrorCategory {
    TIMEOUT,
    PROVIDER_ERROR,
    INVALID_RESPONSE,
    CONFIGURATION_ERROR,
    DUPLICATE_REQUEST,
    UNEXPECTED_ERROR
}
