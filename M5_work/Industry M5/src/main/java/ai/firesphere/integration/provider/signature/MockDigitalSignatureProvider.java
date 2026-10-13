package ai.firesphere.integration.provider.signature;

import ai.firesphere.integration.api.signature.SignatureRequest;
import ai.firesphere.integration.api.signature.SignatureResult;
import ai.firesphere.integration.api.signature.SignatureVerificationRequest;
import ai.firesphere.integration.api.signature.SignatureVerificationResult;
import ai.firesphere.integration.core.exception.IntegrationInvalidResponseException;
import ai.firesphere.integration.core.exception.IntegrationProviderException;
import ai.firesphere.integration.core.exception.IntegrationTimeoutException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic mock Digital Signature provider for unit and integration testing.
 */
@Component("integrationMockDigitalSignatureProvider")
public class MockDigitalSignatureProvider implements DigitalSignatureProvider {

    private final AtomicInteger invocationCount = new AtomicInteger(0);
    private volatile boolean simulateTimeout = false;
    private volatile long timeoutDelayMs = 0;
    private volatile boolean simulateInvalidResponse = false;
    private volatile String invalidResponseMessage = "Simulated invalid Digital Signature response";
    private volatile boolean simulateFailure = false;
    private volatile String failureCode = "ERR_SIGNATURE_HSM_UNAVAILABLE";
    private volatile String failureMessage = "Hardware Security Module token communication failed";
    private volatile boolean failureRetryable = false;
    private final AtomicInteger remainingFailures = new AtomicInteger(0);

    @Override
    public String getProviderName() {
        return "MOCK_PKI_SIGNING_SERVICE";
    }

    @Override
    public SignatureResult sign(SignatureRequest request) {
        checkSimulation();
        return new SignatureResult(
                "SIG-TOKEN-" + UUID.randomUUID().toString().toUpperCase(),
                request.documentReferenceId(),
                "SHA256withRSA",
                "SHA256:7B:3A:9F:C2:55:E1:4A:8D:B3:90:CA:6E:F1",
                Instant.now()
        );
    }

    @Override
    public SignatureVerificationResult verify(SignatureVerificationRequest request) {
        checkSimulation();
        return new SignatureVerificationResult(
                request.documentReferenceId(),
                true,
                "CHIEF_FIRE_OFFICER_SIGNER",
                "Signature cryptographically valid and certified by Fire Administration CA",
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
