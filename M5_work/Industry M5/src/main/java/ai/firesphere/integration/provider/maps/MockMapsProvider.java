package ai.firesphere.integration.provider.maps;

import ai.firesphere.integration.api.maps.GeocodingRequest;
import ai.firesphere.integration.api.maps.GeocodingResult;
import ai.firesphere.integration.api.maps.ReverseGeocodingRequest;
import ai.firesphere.integration.api.maps.ReverseGeocodingResult;
import ai.firesphere.integration.api.maps.RouteMatrixRequest;
import ai.firesphere.integration.api.maps.RouteMatrixResult;
import ai.firesphere.integration.core.exception.IntegrationInvalidResponseException;
import ai.firesphere.integration.core.exception.IntegrationProviderException;
import ai.firesphere.integration.core.exception.IntegrationTimeoutException;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic mock Maps provider for unit and integration testing.
 */
@Component("integrationMockMapsProvider")
public class MockMapsProvider implements MapsProvider {

    private final AtomicInteger invocationCount = new AtomicInteger(0);
    private volatile boolean simulateTimeout = false;
    private volatile long timeoutDelayMs = 0;
    private volatile boolean simulateInvalidResponse = false;
    private volatile String invalidResponseMessage = "Simulated invalid Maps response";
    private volatile boolean simulateFailure = false;
    private volatile String failureCode = "ERR_MAPS_QUOTA_EXCEEDED";
    private volatile String failureMessage = "API rate limit or quota exceeded";
    private volatile boolean failureRetryable = false;
    private final AtomicInteger remainingFailures = new AtomicInteger(0);

    @Override
    public String getProviderName() {
        return "MOCK_MAPS_ENGINE";
    }

    @Override
    public GeocodingResult geocode(GeocodingRequest request) {
        checkSimulation();
        return new GeocodingResult(
                request.rawAddress() != null ? request.rawAddress() : "MG Road, Sector 14, Bengaluru, KA, India",
                12.9716,
                77.5946,
                "place_mock_" + UUID.randomUUID().toString().substring(0, 8),
                0.98
        );
    }

    @Override
    public ReverseGeocodingResult reverseGeocode(ReverseGeocodingRequest request) {
        checkSimulation();
        return new ReverseGeocodingResult(
                "Fire Station Road, Bengaluru, Karnataka, 560001, India",
                "Fire Station Road",
                "Bengaluru",
                "Karnataka",
                "560001",
                "India"
        );
    }

    @Override
    public RouteMatrixResult calculateRoute(RouteMatrixRequest request) {
        checkSimulation();
        return new RouteMatrixResult(
                4500.0,
                360L,
                "Fastest route via Main Arterial Road (Emergency Priority)",
                "MODERATE"
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
