package ai.firesphere.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AlertBroadcastRequest(
        @NotEmpty(message = "Target recipients or devices required")
        List<String> recipients,

        @NotBlank(message = "Alert type is required")
        String alertType, // TURNOUT_ALARM, EVACUATION_ORDER, STATUS_UPDATE, LOW_STOCK

        @NotBlank(message = "Title is required")
        String title,

        @NotBlank(message = "Message body is required")
        String message
) {}
