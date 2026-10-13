package ai.firesphere.integration.api.payment;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Result of reconciling an uncertain, pending, or disconnected payment transaction.
 */
public record PaymentReconciliationResult(
        String transactionId,
        String paymentGatewayOrderId,
        BigDecimal amount,
        String currency,
        String status,
        boolean reconciled,
        String reconciliationNotes,
        Instant reconciledAt
) {}
