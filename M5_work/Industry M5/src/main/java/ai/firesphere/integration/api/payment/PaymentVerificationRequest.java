package ai.firesphere.integration.api.payment;

import java.math.BigDecimal;

public record PaymentVerificationRequest(
        String paymentGatewayOrderId,
        String paymentTransactionId,
        String paymentSignature,
        BigDecimal amountPaid
) {}
