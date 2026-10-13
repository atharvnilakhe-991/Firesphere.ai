package ai.firesphere.integration.provider.payment;

import ai.firesphere.integration.api.payment.PaymentInitiateRequest;
import ai.firesphere.integration.api.payment.PaymentInitiateResult;
import ai.firesphere.integration.api.payment.PaymentReconciliationResult;
import ai.firesphere.integration.api.payment.PaymentStatusResult;
import ai.firesphere.integration.api.payment.PaymentVerificationRequest;
import ai.firesphere.integration.api.payment.PaymentVerificationResult;
import ai.firesphere.integration.api.payment.PaymentWebhookRequest;
import ai.firesphere.integration.api.payment.PaymentWebhookResult;
import ai.firesphere.integration.core.exception.IntegrationInvalidResponseException;
import ai.firesphere.integration.core.exception.IntegrationProviderException;
import ai.firesphere.integration.core.exception.IntegrationTimeoutException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic mock payment provider for unit and integration testing.
 */
@Component("integrationMockPaymentProvider")
public class MockPaymentProvider implements PaymentProvider {

    public static final String DEFAULT_WEBHOOK_SECRET = "mock_webhook_secret_key_firesphere_12345";

    private final AtomicInteger invocationCount = new AtomicInteger(0);
    private final Set<String> processedWebhookEvents = ConcurrentHashMap.newKeySet();
    private final Map<String, String> transactionStatuses = new ConcurrentHashMap<>();

    private volatile String webhookSecret = DEFAULT_WEBHOOK_SECRET;
    private volatile boolean simulateTimeout = false;
    private volatile long timeoutDelayMs = 0;
    private volatile boolean simulateInvalidResponse = false;
    private volatile String invalidResponseMessage = "Simulated invalid response";
    private volatile boolean simulateFailure = false;
    private volatile String failureCode = "ERR_PAYMENT_DECLINED";
    private volatile String failureMessage = "Payment declined by issuing bank";
    private volatile boolean failureRetryable = false;
    private final AtomicInteger remainingFailures = new AtomicInteger(0);

    @Override
    public String getProviderName() {
        return "MOCK_PAYMENT_GATEWAY";
    }

    @Override
    public PaymentInitiateResult initiate(PaymentInitiateRequest request) {
        checkSimulation();
        String orderId = "order_mock_" + UUID.randomUUID().toString().substring(0, 8);
        return new PaymentInitiateResult(
                orderId,
                "https://mock-gateway.firesphere.ai/checkout/" + orderId,
                request.amount(),
                request.currency(),
                "CREATED",
                Instant.now().plusSeconds(1800)
        );
    }

    @Override
    public PaymentVerificationResult verify(PaymentVerificationRequest request) {
        checkSimulation();
        String txId = request.paymentTransactionId();
        return new PaymentVerificationResult(
                request.paymentGatewayOrderId(),
                txId,
                true,
                "SETTLED",
                request.amountPaid(),
                "INR",
                Instant.now()
        );
    }

    @Override
    public PaymentStatusResult status(String transactionId) {
        checkSimulation();
        String currentStatus = transactionStatuses.getOrDefault(transactionId, "CAPTURED");
        return new PaymentStatusResult(
                "order_mock_ref",
                transactionId,
                currentStatus,
                new BigDecimal("1500.00"),
                "INR",
                Instant.now()
        );
    }

    @Override
    public PaymentWebhookResult processWebhook(PaymentWebhookRequest request, String signature) {
        checkSimulation();

        if (request == null) {
            throw new IntegrationProviderException(getProviderName(), "ERR_PAYMENT_INVALID_PAYLOAD", "Webhook request payload is required", false);
        }

        String rawData = request.paymentGatewayOrderId() + "|" + request.paymentTransactionId() + "|" + request.amount() + "|" + request.status();
        if (signature == null || !verifyWebhookSignature(rawData, signature)) {
            throw new IntegrationProviderException(
                    getProviderName(),
                    "ERR_PAYMENT_INVALID_SIGNATURE",
                    "Webhook HMAC-SHA256 signature verification failed",
                    false
            );
        }

        if (request.paymentTransactionId() != null && request.status() != null) {
            transactionStatuses.put(request.paymentTransactionId(), request.status());
        }

        processedWebhookEvents.add(request.eventId());

        return new PaymentWebhookResult(
                request.eventId(),
                request.paymentGatewayOrderId(),
                request.paymentTransactionId(),
                request.status(),
                true,
                "Webhook processed successfully for event " + request.eventType(),
                Instant.now()
        );
    }

    @Override
    public PaymentReconciliationResult reconcile(String transactionId) {
        checkSimulation();

        if (transactionId == null || transactionId.isBlank()) {
            throw new IntegrationProviderException(getProviderName(), "ERR_PAYMENT_INVALID_TX", "Transaction ID is required for reconciliation", false);
        }

        String currentStatus = transactionStatuses.getOrDefault(transactionId, "SETTLED");
        transactionStatuses.put(transactionId, currentStatus);

        return new PaymentReconciliationResult(
                transactionId,
                "order_recon_" + transactionId,
                new BigDecimal("1500.00"),
                "INR",
                currentStatus,
                true,
                "Payment transaction reconciled successfully against gateway ledger",
                Instant.now()
        );
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        if (payload == null || signature == null) {
            return false;
        }
        String expected = generateWebhookSignature(payload);
        return MessageDigest.isEqual(
                expected.toLowerCase().getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase().getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public String generateWebhookSignature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate HMAC-SHA256 signature", e);
        }
    }

    private void checkSimulation() {
        invocationCount.incrementAndGet();

        if (simulateTimeout) {
            if (timeoutDelayMs > 0) {
                try {
                    Thread.sleep(timeoutDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            throw new IntegrationTimeoutException(getProviderName(), timeoutDelayMs);
        }

        if (simulateInvalidResponse) {
            throw new IntegrationInvalidResponseException(getProviderName(), invalidResponseMessage);
        }

        if (remainingFailures.get() > 0) {
            remainingFailures.decrementAndGet();
            throw new IntegrationProviderException(getProviderName(), failureCode, failureMessage, failureRetryable);
        }

        if (simulateFailure) {
            throw new IntegrationProviderException(getProviderName(), failureCode, failureMessage, failureRetryable);
        }
    }

    // --- Control methods for deterministic testing ---

    public void simulateSuccess() {
        this.simulateTimeout = false;
        this.simulateInvalidResponse = false;
        this.simulateFailure = false;
        this.remainingFailures.set(0);
    }

    public void simulateFailure(String code, String message, boolean retryable) {
        this.simulateFailure = true;
        this.failureCode = code;
        this.failureMessage = message;
        this.failureRetryable = retryable;
    }

    public void simulateTimeout(long delayMs) {
        this.simulateTimeout = true;
        this.timeoutDelayMs = delayMs;
    }

    public void simulateInvalidResponse(String reason) {
        this.simulateInvalidResponse = true;
        this.invalidResponseMessage = reason;
    }

    public void failNextAttempts(int count, String code, String message, boolean retryable) {
        this.remainingFailures.set(count);
        this.failureCode = code;
        this.failureMessage = message;
        this.failureRetryable = retryable;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public int getInvocationCount() {
        return invocationCount.get();
    }

    public Set<String> getProcessedWebhookEvents() {
        return Set.copyOf(processedWebhookEvents);
    }

    public void reset() {
        simulateSuccess();
        this.invocationCount.set(0);
        this.webhookSecret = DEFAULT_WEBHOOK_SECRET;
        this.processedWebhookEvents.clear();
        this.transactionStatuses.clear();
    }
}
