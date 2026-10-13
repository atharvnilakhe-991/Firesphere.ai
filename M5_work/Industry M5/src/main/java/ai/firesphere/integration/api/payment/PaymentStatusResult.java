package ai.firesphere.integration.api.payment;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentStatusResult(
        String paymentGatewayOrderId,
        String paymentTransactionId,
        String status, // CREATED, AUTHORIZED, CAPTURED, FAILED, REFUNDED
        BigDecimal amount,
        String currency,
        Instant lastUpdatedAt
) {}
