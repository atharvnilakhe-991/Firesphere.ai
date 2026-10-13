package ai.firesphere.integration.api.payment;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentVerificationResult(
        String paymentGatewayOrderId,
        String paymentTransactionId,
        boolean verified,
        String status, // SETTLED, FAILED, REFUNDED
        BigDecimal amountPaid,
        String currency,
        Instant verifiedAt
) {}
