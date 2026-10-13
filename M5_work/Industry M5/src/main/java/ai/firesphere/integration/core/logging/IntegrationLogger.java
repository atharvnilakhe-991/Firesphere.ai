package ai.firesphere.integration.core.logging;

import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Structured logger for external integration operations.
 * Enforces correlation ID traceability, latency tracking, and strict sanitization of credentials.
 */
@Component
public class IntegrationLogger {

    private static final Logger log = LoggerFactory.getLogger(IntegrationLogger.class);

    public <T> void logRequest(String providerName, String operationName, IntegrationRequest<T> request) {
        String corrId = request != null && request.correlationId() != null ? request.correlationId() : "NO-CORR-ID";
        String idempKey = request != null && request.idempotencyKey() != null ? request.idempotencyKey().value() : "NONE";

        log.info("[INTEGRATION REQ] [{}] provider='{}' op='{}' idempKey='{}'",
                corrId, sanitize(providerName), sanitize(operationName), idempKey);
    }

    public <T> void logResponse(String operationName, IntegrationResponse<T> response) {
        if (response == null) {
            log.warn("[INTEGRATION RESP] op='{}' received null response", sanitize(operationName));
            return;
        }

        String corrId = response.correlationId() != null ? response.correlationId() : "NO-CORR-ID";
        String provider = response.providerName() != null ? response.providerName() : "UNKNOWN";

        if (response.isSuccess()) {
            log.info("[INTEGRATION RESP] [{}] provider='{}' op='{}' status='{}' duration={}ms txId='{}'",
                    corrId, sanitize(provider), sanitize(operationName), response.status(),
                    response.durationMs(), response.providerTransactionId());
        } else {
            String errorMsg = response.getError().map(e -> e.code() + ": " + e.message()).orElse("Unknown error");
            log.error("[INTEGRATION RESP] [{}] provider='{}' op='{}' status='{}' duration={}ms error='{}'",
                    corrId, sanitize(provider), sanitize(operationName), response.status(),
                    response.durationMs(), sanitize(errorMsg));
        }
    }

    /**
     * Sanitizes inputs to prevent log injection and credential leakage.
     */
    public String sanitize(String text) {
        if (text == null) {
            return "";
        }
        // Mask any potential sensitive keys or tokens
        String sanitized = text.replaceAll("[\r\n]", "_");
        if (containsSensitiveKeyword(sanitized)) {
            return "[REDACTED_SENSITIVE]";
        }
        return sanitized;
    }

    private boolean containsSensitiveKeyword(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("bearer ") || lower.contains("authorization:") || lower.contains("password=")
                || lower.contains("secret=") || lower.contains("apikey=") || lower.contains("privatekey=");
    }
}
