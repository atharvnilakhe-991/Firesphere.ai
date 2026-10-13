package ai.firesphere.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Predictable error envelope optimized for mobile network clients.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String errorCode,
        String message,
        int status,
        Instant timestamp,
        String path,
        Map<String, String> fieldErrors,
        List<String> details
) {
    public static ErrorResponse of(String errorCode, String message, int status, String path) {
        return new ErrorResponse(errorCode, message, status, Instant.now(), path, null, null);
    }

    public static ErrorResponse withFieldErrors(String errorCode, String message, int status, String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(errorCode, message, status, Instant.now(), path, fieldErrors, null);
    }
}
