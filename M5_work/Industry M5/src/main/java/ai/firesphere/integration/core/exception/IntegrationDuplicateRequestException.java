package ai.firesphere.integration.core.exception;

import ai.firesphere.integration.core.model.IntegrationError;
import ai.firesphere.integration.core.model.IntegrationErrorCategory;

/**
 * Exception thrown when a duplicate request is detected and the operation policy forbids reprocessing.
 * Non-retryable.
 */
public class IntegrationDuplicateRequestException extends IntegrationException {

    public IntegrationDuplicateRequestException(String idempotencyKey, String message) {
        super(new IntegrationError(
                "ERR_DUPLICATE_REQUEST",
                IntegrationErrorCategory.DUPLICATE_REQUEST,
                "Duplicate request detected for idempotency key [" + idempotencyKey + "]: " + message,
                false
        ));
    }
}
