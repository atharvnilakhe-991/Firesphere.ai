package ai.firesphere.integration;

import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.integration.adapter.payment.PaymentIntegrationAdapter;
import ai.firesphere.integration.api.payment.PaymentReconciliationResult;
import ai.firesphere.integration.api.payment.PaymentWebhookRequest;
import ai.firesphere.integration.api.payment.PaymentWebhookResult;
import ai.firesphere.integration.core.configuration.IntegrationProperties;
import ai.firesphere.integration.core.idempotency.IdempotencyKey;
import ai.firesphere.integration.core.idempotency.InMemoryIdempotencyStore;
import ai.firesphere.integration.core.logging.IntegrationLogger;
import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;
import ai.firesphere.integration.core.retry.IntegrationRetryExecutor;
import ai.firesphere.integration.provider.payment.MockPaymentProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentWebhookAndReconciliationTest {

    private MockPaymentProvider mockProvider;
    private PaymentIntegrationAdapter adapter;
    private InMemoryIdempotencyStore idempotencyStore;

    @BeforeEach
    void setUp() {
        mockProvider = new MockPaymentProvider();
        idempotencyStore = new InMemoryIdempotencyStore();
        IntegrationProperties properties = new IntegrationProperties();
        IntegrationRetryExecutor retryExecutor = new IntegrationRetryExecutor();
        IntegrationLogger logger = new IntegrationLogger();
        adapter = new PaymentIntegrationAdapter(mockProvider, properties, retryExecutor, idempotencyStore, logger);

        TenantContext.setTenantId(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        mockProvider.reset();
    }

    @Test
    @DisplayName("Webhook with authentic HMAC-SHA256 signature is processed successfully")
    void testValidWebhookProcessedSuccessfully() {
        PaymentWebhookRequest webhook = new PaymentWebhookRequest(
                "payment.captured",
                "evt_test_001",
                "order_gateway_771",
                "tx_bank_991",
                new BigDecimal("2500.00"),
                "INR",
                "CAPTURED",
                Instant.now(),
                Map.of("method", "UPI", "vpa", "citizen@okhdfcbank")
        );

        String rawPayload = webhook.paymentGatewayOrderId() + "|" + webhook.paymentTransactionId() + "|" + webhook.amount() + "|" + webhook.status();
        String validSignature = mockProvider.generateWebhookSignature(rawPayload);

        IntegrationRequest<PaymentWebhookRequest> req = IntegrationRequest.of(
                "CORR-WEBHOOK-01",
                IdempotencyKey.of("IDEMP-EVT-001"),
                webhook
        );

        IntegrationResponse<PaymentWebhookResult> response = adapter.processWebhook(req, validSignature);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.data()).isNotNull();
        assertThat(response.data().eventId()).isEqualTo("evt_test_001");
        assertThat(response.data().status()).isEqualTo("CAPTURED");
        assertThat(response.data().processed()).isTrue();
        assertThat(mockProvider.getProcessedWebhookEvents()).contains("evt_test_001");
    }

    @Test
    @DisplayName("Webhook with invalid or forged HMAC-SHA256 signature is rejected")
    void testInvalidSignatureWebhookRejected() {
        PaymentWebhookRequest webhook = new PaymentWebhookRequest(
                "payment.captured",
                "evt_test_002",
                "order_gateway_772",
                "tx_bank_992",
                new BigDecimal("1500.00"),
                "INR",
                "CAPTURED",
                Instant.now(),
                Map.of()
        );

        String forgedSignature = "forged_invalid_signature_hex_digest_1234567890abcdef";

        IntegrationRequest<PaymentWebhookRequest> req = IntegrationRequest.of(
                "CORR-WEBHOOK-02",
                IdempotencyKey.of("IDEMP-EVT-002"),
                webhook
        );

        IntegrationResponse<PaymentWebhookResult> response = adapter.processWebhook(req, forgedSignature);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.error().code()).isEqualTo("ERR_PAYMENT_INVALID_SIGNATURE");
        assertThat(mockProvider.getProcessedWebhookEvents()).doesNotContain("evt_test_002");
    }

    @Test
    @DisplayName("Webhook with null or empty signature is rejected")
    void testMissingSignatureWebhookRejected() {
        PaymentWebhookRequest webhook = new PaymentWebhookRequest(
                "payment.failed",
                "evt_test_003",
                "order_gateway_773",
                "tx_bank_993",
                new BigDecimal("1500.00"),
                "INR",
                "FAILED",
                Instant.now(),
                Map.of()
        );

        IntegrationResponse<PaymentWebhookResult> response = adapter.processWebhook(
                IntegrationRequest.of(webhook),
                null
        );

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.error().code()).isEqualTo("ERR_PAYMENT_INVALID_SIGNATURE");
    }

    @Test
    @DisplayName("Duplicate webhook delivery is deduplicated via idempotency store")
    void testDuplicateWebhookDeduplicated() {
        PaymentWebhookRequest webhook = new PaymentWebhookRequest(
                "payment.captured",
                "evt_test_004",
                "order_gateway_774",
                "tx_bank_994",
                new BigDecimal("3000.00"),
                "INR",
                "CAPTURED",
                Instant.now(),
                Map.of()
        );

        String rawPayload = webhook.paymentGatewayOrderId() + "|" + webhook.paymentTransactionId() + "|" + webhook.amount() + "|" + webhook.status();
        String signature = mockProvider.generateWebhookSignature(rawPayload);
        IdempotencyKey key = IdempotencyKey.of("IDEMP-EVT-004");

        IntegrationResponse<PaymentWebhookResult> first = adapter.processWebhook(
                IntegrationRequest.of("CORR-W1", key, webhook),
                signature
        );
        assertThat(first.isSuccess()).isTrue();
        int invocations = mockProvider.getInvocationCount();

        IntegrationResponse<PaymentWebhookResult> second = adapter.processWebhook(
                IntegrationRequest.of("CORR-W2", key, webhook),
                signature
        );
        assertThat(second.status()).isEqualTo(ai.firesphere.integration.core.model.IntegrationStatus.DUPLICATE);
        assertThat(second.data().eventId()).isEqualTo("evt_test_004");
        assertThat(mockProvider.getInvocationCount()).isEqualTo(invocations); // Not re-invoked
    }

    @Test
    @DisplayName("Reconciliation successfully verifies and settles an uncertain transaction")
    void testReconciliationSettlesUncertainTransaction() {
        String transactionId = "TX-DISCONNECTED-999";

        IntegrationResponse<PaymentReconciliationResult> response = adapter.reconcilePayment(
                IntegrationRequest.of(transactionId)
        );

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.data()).isNotNull();
        assertThat(response.data().transactionId()).isEqualTo(transactionId);
        assertThat(response.data().reconciled()).isTrue();
        assertThat(response.data().status()).isEqualTo("SETTLED");
        assertThat(response.data().reconciliationNotes()).contains("ledger");
    }

    @Test
    @DisplayName("Reconciliation with null transaction ID fails gracefully")
    void testReconciliationWithNullIdFails() {
        IntegrationResponse<PaymentReconciliationResult> response = adapter.reconcilePayment(null);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.error().code()).isEqualTo("ERR_PAYMENT_INVALID_TX");
    }
}
