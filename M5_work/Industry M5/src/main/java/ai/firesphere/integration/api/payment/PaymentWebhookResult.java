package ai.firesphere.integration.api.payment;

import java.time.Instant;

/**
 * Result of processing an inbound payment gateway webhook.
 */
public record PaymentWebhookResult(
        String eventId,
        String paymentGatewayOrderId,
        String paymentTransactionId,
        String status,
        boolean processed,
        String message,
        Instant processedAt
) {}
