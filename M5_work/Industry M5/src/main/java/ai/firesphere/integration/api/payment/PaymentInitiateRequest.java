package ai.firesphere.integration.api.payment;

import java.math.BigDecimal;

public record PaymentInitiateRequest(
        String orderReference,
        BigDecimal amount,
        String currency,
        String customerEmail,
        String customerPhone,
        String description
) {
    public PaymentInitiateRequest {
        if (currency == null || currency.isBlank()) {
            currency = "INR";
        }
    }
}
