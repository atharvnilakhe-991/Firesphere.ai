package ai.firesphere.integration.api.sms;

import java.time.Instant;

public record SmsMessageResult(
        String externalMessageId,
        String recipientPhoneNumber,
        String deliveryStatus, // SENT, DELIVERED, QUEUED, FAILED
        int partsCount,
        Instant dispatchedAt
) {}
