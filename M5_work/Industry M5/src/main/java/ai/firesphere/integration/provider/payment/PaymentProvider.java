package ai.firesphere.integration.provider.payment;

import ai.firesphere.integration.api.payment.PaymentInitiateRequest;
import ai.firesphere.integration.api.payment.PaymentInitiateResult;
import ai.firesphere.integration.api.payment.PaymentReconciliationResult;
import ai.firesphere.integration.api.payment.PaymentStatusResult;
import ai.firesphere.integration.api.payment.PaymentVerificationRequest;
import ai.firesphere.integration.api.payment.PaymentVerificationResult;
import ai.firesphere.integration.api.payment.PaymentWebhookRequest;
import ai.firesphere.integration.api.payment.PaymentWebhookResult;

/**
 * Provider SPI interface for payment gateways (e.g. Razorpay/Stripe mock).
 */
public interface PaymentProvider {

    String getProviderName();

    PaymentInitiateResult initiate(PaymentInitiateRequest request);

    PaymentVerificationResult verify(PaymentVerificationRequest request);

    PaymentStatusResult status(String transactionId);

    PaymentWebhookResult processWebhook(PaymentWebhookRequest request, String signature);

    PaymentReconciliationResult reconcile(String transactionId);

    boolean verifyWebhookSignature(String payload, String signature);

    String generateWebhookSignature(String payload);
}
