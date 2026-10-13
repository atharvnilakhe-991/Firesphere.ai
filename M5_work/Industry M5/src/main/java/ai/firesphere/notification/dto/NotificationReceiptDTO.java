package ai.firesphere.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationReceiptDTO(
        UUID receiptId,
        String channel,
        String recipient,
        String deliveryStatus,
        Instant dispatchedAt
) {}
