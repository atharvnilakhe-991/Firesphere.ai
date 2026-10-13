package ai.firesphere.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * Request payload for submitting a notification event to the notification dispatch API.
 */
public record NotificationSendRequest(
        @NotBlank(message = "Event type is required")
        String eventType,

        String referenceId,

        String priority, // LOW, NORMAL, HIGH, CRITICAL

        @NotEmpty(message = "At least one delivery channel must be specified")
        List<String> channels,

        @NotEmpty(message = "Recipient mappings must be specified")
        Map<String, String> recipients,

        Map<String, Object> payload,

        String idempotencyKey
) {}
