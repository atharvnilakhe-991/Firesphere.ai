package ai.firesphere.integration.api.whatsapp;

import java.time.Instant;

public record WhatsAppMessageResult(
        String externalMessageId,
        String recipientPhoneNumber,
        String deliveryStatus, // SENT, DELIVERED, READ, FAILED
        Instant timestamp
) {}
