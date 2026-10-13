package ai.firesphere.integration.core.retry;

import ai.firesphere.integration.core.correlation.CorrelationContext;
import ai.firesphere.integration.core.exception.IntegrationException;
import ai.firesphere.integration.core.model.IntegrationError;
import ai.firesphere.integration.core.model.IntegrationErrorCategory;
import ai.firesphere.integration.core.model.IntegrationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Executes integration operations with bounded, exponential backoff retries.
 * Only operations with retryable errors or transient exceptions are retried.
 */
@Component
public class IntegrationRetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(IntegrationRetryExecutor.class);

    public <T> IntegrationResponse<T> executeWithRetry(
            String operationName,
            String providerName,
            RetryPolicy retryPolicy,
            Supplier<IntegrationResponse<T>> operation
    ) {
        String correlationId = CorrelationContext.getOrGenerate();
        RetryPolicy policy = retryPolicy != null ? retryPolicy : RetryPolicy.defaultPolicy();
        int attempts = 0;
        long currentDelay = policy.initialDelayMs();
        long startTime = System.currentTimeMillis();

        IntegrationResponse<T> lastResponse = null;
        Throwable lastThrowable = null;

        while (attempts < policy.maxAttempts()) {
            attempts++;
            try {
                log.debug("[INTEGRATION RETRY] [{}] Executing operation '{}' (attempt {}/{})",
                        correlationId, operationName, attempts, policy.maxAttempts());

                IntegrationResponse<T> response = operation.get();
                if (response == null) {
                    throw new IllegalStateException("Operation returned null response");
                }

                if (response.isSuccess()) {
                    return response;
                }

                // If response represents a non-retryable failure, return immediately
                lastResponse = response;
                if (response.getError().isPresent()) {
                    IntegrationError error = response.getError().get();
                    if (!error.retryable()) {
                        log.warn("[INTEGRATION RETRY] [{}] Non-retryable failure in '{}': {}. Aborting retries.",
                                correlationId, operationName, error.message());
                        return response;
                    }
                }

                log.warn("[INTEGRATION RETRY] [{}] Transient failure in '{}' on attempt {}/{}: {}",
                        correlationId, operationName, attempts, policy.maxAttempts(),
                        response.getError().map(IntegrationError::message).orElse("Unknown failure"));

            } catch (IntegrationException ex) {
                lastThrowable = ex;
                if (!ex.getError().retryable()) {
                    log.warn("[INTEGRATION RETRY] [{}] Non-retryable exception in '{}': {}. Aborting retries.",
                            correlationId, operationName, ex.getMessage());
                    long duration = System.currentTimeMillis() - startTime;
                    return IntegrationResponse.failure(correlationId, providerName, ex.getError(), duration);
                }
                log.warn("[INTEGRATION RETRY] [{}] Retryable exception in '{}' on attempt {}/{}: {}",
                        correlationId, operationName, attempts, policy.maxAttempts(), ex.getMessage());
            } catch (Exception ex) {
                lastThrowable = ex;
                log.warn("[INTEGRATION RETRY] [{}] Unexpected exception in '{}' on attempt {}/{}: {}",
                        correlationId, operationName, attempts, policy.maxAttempts(), ex.getMessage());
            }

            if (attempts < policy.maxAttempts() && currentDelay > 0) {
                try {
                    Thread.sleep(currentDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    long duration = System.currentTimeMillis() - startTime;
                    return IntegrationResponse.failure(correlationId, providerName,
                            new IntegrationError("ERR_INTERRUPTED", IntegrationErrorCategory.UNEXPECTED_ERROR, "Operation interrupted during retry backoff", false),
                            duration);
                }
                currentDelay = (long) (currentDelay * policy.backoffMultiplier());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        if (lastResponse != null) {
            return lastResponse;
        }

        String errorMsg = lastThrowable != null ? lastThrowable.getMessage() : "Retries exhausted without response";
        return IntegrationResponse.failure(
                correlationId,
                providerName,
                new IntegrationError("ERR_RETRIES_EXHAUSTED", IntegrationErrorCategory.PROVIDER_ERROR, errorMsg, false),
                duration
        );
    }
}
