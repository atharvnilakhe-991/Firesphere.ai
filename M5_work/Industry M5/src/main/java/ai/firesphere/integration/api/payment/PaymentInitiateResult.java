package ai.firesphere.integration.api.payment;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentInitiateResult(
        String paymentGatewayOrderId,
        String paymentUrl,
        BigDecimal amount,
        String currency,
        String status,
        Instant expiresAt
) {}
