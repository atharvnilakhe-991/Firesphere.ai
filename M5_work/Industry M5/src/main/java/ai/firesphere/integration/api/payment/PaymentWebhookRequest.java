package ai.firesphere.integration.api.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Inbound webhook notification from an external payment gateway.
 */
public record PaymentWebhookRequest(
        @NotBlank(message = "Event type is required")
        String eventType,

        @NotBlank(message = "Event ID is required")
        String eventId,

        @NotBlank(message = "Payment gateway order ID is required")
        String paymentGatewayOrderId,

        String paymentTransactionId,

        @NotNull(message = "Amount is required")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        String currency,

        @NotBlank(message = "Payment status is required")
        String status,

        Instant eventTimestamp,

        Map<String, Object> payload
) {
    public PaymentWebhookRequest {
        if (eventTimestamp == null) {
            eventTimestamp = Instant.now();
        }
        if (payload == null) {
            payload = Map.of();
        }
    }
}
