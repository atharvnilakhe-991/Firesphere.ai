package ai.firesphere.integration.provider.whatsapp;

import ai.firesphere.integration.api.whatsapp.WhatsAppMessageRequest;
import ai.firesphere.integration.api.whatsapp.WhatsAppMessageResult;
import ai.firesphere.integration.core.exception.IntegrationInvalidResponseException;
import ai.firesphere.integration.core.exception.IntegrationProviderException;
import ai.firesphere.integration.core.exception.IntegrationTimeoutException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic mock WhatsApp provider for unit and integration testing.
 */
@Component("integrationMockWhatsAppProvider")
public class MockWhatsAppProvider implements WhatsAppProvider {

    private final AtomicInteger invocationCount = new AtomicInteger(0);
    private volatile boolean simulateTimeout = false;
    private volatile long timeoutDelayMs = 0;
    private volatile boolean simulateInvalidResponse = false;
    private volatile String invalidResponseMessage = "Simulated invalid WhatsApp response";
    private volatile boolean simulateFailure = false;
    private volatile String failureCode = "ERR_WHATSAPP_TEMPLATE_MISMATCH";
    private volatile String failureMessage = "Template parameters do not match approved template";
    private volatile boolean failureRetryable = false;
    private final AtomicInteger remainingFailures = new AtomicInteger(0);

    @Override
    public String getProviderName() {
        return "MOCK_WHATSAPP_API";
    }

    @Override
    public WhatsAppMessageResult send(WhatsAppMessageRequest request) {
        checkSimulation();
        return new WhatsAppMessageResult(
                "WA-MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                request.recipientPhoneNumber(),
                "DELIVERED",
                Instant.now()
        );
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

    public int getInvocationCount() {
        return invocationCount.get();
    }

    public void reset() {
        simulateSuccess();
        this.invocationCount.set(0);
    }
}
