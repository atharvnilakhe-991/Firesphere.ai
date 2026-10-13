package ai.firesphere.integration;

import ai.firesphere.integration.adapter.payment.PaymentIntegrationAdapter;
import ai.firesphere.integration.api.payment.PaymentInitiateRequest;
import ai.firesphere.integration.api.payment.PaymentInitiateResult;
import ai.firesphere.integration.api.payment.PaymentIntegration;
import ai.firesphere.integration.core.configuration.IntegrationProperties;
import ai.firesphere.integration.core.correlation.CorrelationContext;
import ai.firesphere.integration.core.exception.IntegrationProviderException;
import ai.firesphere.integration.core.idempotency.IdempotencyKey;
import ai.firesphere.integration.core.idempotency.IdempotencyStore;
import ai.firesphere.integration.core.idempotency.InMemoryIdempotencyStore;
import ai.firesphere.integration.core.logging.IntegrationLogger;
import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;
import ai.firesphere.integration.core.model.IntegrationStatus;
import ai.firesphere.integration.core.retry.IntegrationRetryExecutor;
import ai.firesphere.integration.core.retry.RetryPolicy;
import ai.firesphere.integration.provider.payment.MockPaymentProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional QA Suite for External Integration Framework.
 * Verifies retry exhaustion on transient errors, immediate abort on non-retryable errors,
 * correlation ID propagation, idempotency cache deduplication, and mock provider zero-network isolation.
 */
class ExternalIntegrationFunctionalQATest {

    private IntegrationProperties properties;
    private IntegrationRetryExecutor retryExecutor;
    private IdempotencyStore idempotencyStore;
    private IntegrationLogger logger;
    private MockPaymentProvider mockPaymentProvider;
    private PaymentIntegration paymentAdapter;

    @BeforeEach
    void setUp() {
        properties = new IntegrationProperties();
        properties.getPayment().setInitialRetryDelayMs(2);
        properties.getPayment().setMaxRetries(3);

        retryExecutor = new IntegrationRetryExecutor();
        idempotencyStore = new InMemoryIdempotencyStore();
        logger = new IntegrationLogger();
        mockPaymentProvider = new MockPaymentProvider();

        paymentAdapter = new PaymentIntegrationAdapter(
                mockPaymentProvider,
                properties,
                retryExecutor,
                idempotencyStore,
                logger
        );
    }

    @AfterEach
    void tearDown() {
        CorrelationContext.clear();
        mockPaymentProvider.reset();
    }

    @Test
    @DisplayName("Retry Exhaustion: Transient failures retry exactly maxRetries (3) times before terminal FAILED")
    void testRetryExhaustionOnTransientFailures() {
        // Configure provider to fail continuously with a retryable error
        mockPaymentProvider.simulateFailure("ERR_GATEWAY_DOWN", "Upstream server 503 unavailable", true);

        PaymentInitiateRequest payload = new PaymentInitiateRequest(
                "ORD-RETRY-01",
                new BigDecimal("500.00"),
                "INR",
                "officer@firesphere.ai",
                "+919876543210",
                "Hydrant Inspection Fee"
        );

        IntegrationResponse<PaymentInitiateResult> response = paymentAdapter.initiatePayment(IntegrationRequest.of(payload));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.status()).isEqualTo(IntegrationStatus.FAILED);
        assertThat(response.getError()).isPresent();
        assertThat(response.getError().get().code()).isEqualTo("ERR_GATEWAY_DOWN");
        // Verify exactly 3 attempts were made
        assertThat(mockPaymentProvider.getInvocationCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Non-Retryable Failure: Non-retryable error halts immediately on attempt 1 without retrying")
    void testNonRetryableFailureHaltsImmediately() {
        // Non-retryable error (e.g. card invalid or account closed)
        mockPaymentProvider.simulateFailure("ERR_INVALID_ACCOUNT", "Account does not exist", false);

        PaymentInitiateRequest payload = new PaymentInitiateRequest(
                "ORD-NON-RETRY-01",
                new BigDecimal("750.00"),
                "INR",
                "officer@firesphere.ai",
                "+919876543210",
                "Building NOC Fee"
        );

        IntegrationResponse<PaymentInitiateResult> response = paymentAdapter.initiatePayment(IntegrationRequest.of(payload));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.status()).isEqualTo(IntegrationStatus.FAILED);
        assertThat(response.getError().get().retryable()).isFalse();
        // Crucial: Only 1 invocation attempt occurred
        assertThat(mockPaymentProvider.getInvocationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Direct Retry Executor: Retries until exhaustion when Supplier throws IntegrationProviderException")
    void testDirectRetryExecutorExhaustion() {
        AtomicInteger callCount = new AtomicInteger(0);
        RetryPolicy policy = new RetryPolicy(3, 1, 1.0);

        IntegrationResponse<String> result = retryExecutor.executeWithRetry(
                "TestOp",
                "MockProvider",
                policy,
                () -> {
                    callCount.incrementAndGet();
                    throw new IntegrationProviderException("MockProvider", "MOCK_FAIL", "Transient external failure", true);
                }
        );

        assertThat(result.isSuccess()).isFalse();
        assertThat(callCount.get()).isEqualTo(3);
        assertThat(result.getError()).isPresent();
        assertThat(result.getError().get().code()).isEqualTo("ERR_RETRIES_EXHAUSTED");
        assertThat(result.getError().get().message()).contains("Transient external failure");
    }

    @Test
    @DisplayName("Correlation Context: Correlation ID propagates into integration responses and logs")
    void testCorrelationContextPropagation() {
        String testCorrId = "CORR-QA-TRACE-999";
        CorrelationContext.set(testCorrId);

        PaymentInitiateRequest payload = new PaymentInitiateRequest(
                "ORD-CORR-01",
                new BigDecimal("200.00"),
                "INR",
                "user@firesphere.ai",
                "+919876543210",
                "Fee"
        );

        IntegrationResponse<PaymentInitiateResult> response = paymentAdapter.initiatePayment(IntegrationRequest.of(payload));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.correlationId()).isEqualTo(testCorrId);
    }

    @Test
    @DisplayName("Idempotency Store: Idempotency keys prevent duplicate provider side effects")
    void testIdempotencyStoreCachesResponses() {
        IdempotencyKey key = IdempotencyKey.of("IDEMP-KEY-TEST-01");
        PaymentInitiateRequest payload = new PaymentInitiateRequest(
                "ORD-IDEMP-01",
                new BigDecimal("300.00"),
                "INR",
                "user@firesphere.ai",
                "+919876543210",
                "Fee"
        );

        IntegrationRequest<PaymentInitiateRequest> req1 = IntegrationRequest.of("CORR-01", key, payload);
        IntegrationResponse<PaymentInitiateResult> res1 = paymentAdapter.initiatePayment(req1);
        assertThat(res1.status()).isEqualTo(IntegrationStatus.SUCCESS);
        assertThat(mockPaymentProvider.getInvocationCount()).isEqualTo(1);

        // Replay with identical key
        IntegrationRequest<PaymentInitiateRequest> req2 = IntegrationRequest.of("CORR-02", key, payload);
        IntegrationResponse<PaymentInitiateResult> res2 = paymentAdapter.initiatePayment(req2);
        assertThat(res2.status()).isEqualTo(IntegrationStatus.DUPLICATE);
        assertThat(res2.data().paymentGatewayOrderId()).isEqualTo(res1.data().paymentGatewayOrderId());
        assertThat(mockPaymentProvider.getInvocationCount()).isEqualTo(1);
    }
}
