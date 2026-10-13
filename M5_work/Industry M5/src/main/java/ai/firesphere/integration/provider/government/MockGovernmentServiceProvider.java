package ai.firesphere.integration.provider.government;

import ai.firesphere.integration.api.government.CadStatusQueryRequest;
import ai.firesphere.integration.api.government.CadStatusQueryResult;
import ai.firesphere.integration.api.government.CadTransmitRequest;
import ai.firesphere.integration.api.government.CadTransmitResult;
import ai.firesphere.integration.api.government.ComplianceLookupRequest;
import ai.firesphere.integration.api.government.ComplianceLookupResult;
import ai.firesphere.integration.core.exception.IntegrationInvalidResponseException;
import ai.firesphere.integration.core.exception.IntegrationProviderException;
import ai.firesphere.integration.core.exception.IntegrationTimeoutException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic mock Government & CAD provider for unit and integration testing.
 */
@Component("integrationMockGovernmentServiceProvider")
public class MockGovernmentServiceProvider implements GovernmentServiceProvider {

    private final AtomicInteger invocationCount = new AtomicInteger(0);
    private volatile boolean simulateTimeout = false;
    private volatile long timeoutDelayMs = 0;
    private volatile boolean simulateInvalidResponse = false;
    private volatile String invalidResponseMessage = "Simulated invalid CAD response";
    private volatile boolean simulateFailure = false;
    private volatile String failureCode = "ERR_CAD_GATEWAY_OFFLINE";
    private volatile String failureMessage = "Municipal emergency dispatch exchange is offline";
    private volatile boolean failureRetryable = false;
    private final AtomicInteger remainingFailures = new AtomicInteger(0);

    @Override
    public String getProviderName() {
        return "MOCK_MUNICIPAL_112_CAD";
    }

    @Override
    public CadTransmitResult transmitCad(CadTransmitRequest request) {
        checkSimulation();
        String externalCadId = "CAD-112-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new CadTransmitResult(
                externalCadId,
                request.internalIncidentId(),
                "TRANSMITTED",
                "CENTRAL_FIRE_CONTROL_ROOM",
                Instant.now()
        );
    }

    @Override
    public CadStatusQueryResult queryStatus(CadStatusQueryRequest request) {
        checkSimulation();
        return new CadStatusQueryResult(
                request.externalCadIncidentId(),
                "CENTRAL_FIRE_CONTROL_ROOM",
                "ON_SCENE",
                "First responding units reported water on fire",
                Instant.now()
        );
    }

    @Override
    public ComplianceLookupResult lookupCompliance(ComplianceLookupRequest request) {
        checkSimulation();
        return new ComplianceLookupResult(
                request.buildingTaxId(),
                true,
                "ACTIVE",
                "NOC-BBMP-2026-9921",
                Instant.now().plusSeconds(86400 * 180),
                Collections.emptyList()
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
