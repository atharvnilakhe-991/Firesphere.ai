package ai.firesphere.integration.api.payment;

import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;

/**
 * Internal business contract for payment gateway operations.
 * Business code interacts exclusively with this interface.
 */
public interface PaymentIntegration {

    IntegrationResponse<PaymentInitiateResult> initiatePayment(IntegrationRequest<PaymentInitiateRequest> request);

    IntegrationResponse<PaymentVerificationResult> verifyPayment(IntegrationRequest<PaymentVerificationRequest> request);

    IntegrationResponse<PaymentStatusResult> checkStatus(IntegrationRequest<String> transactionIdRequest);

    IntegrationResponse<PaymentWebhookResult> processWebhook(IntegrationRequest<PaymentWebhookRequest> request, String signature);

    IntegrationResponse<PaymentReconciliationResult> reconcilePayment(IntegrationRequest<String> transactionIdRequest);
}
