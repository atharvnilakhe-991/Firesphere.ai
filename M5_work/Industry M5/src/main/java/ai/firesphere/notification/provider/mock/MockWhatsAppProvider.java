package ai.firesphere.notification.provider.mock;

import ai.firesphere.notification.provider.ProviderDeliveryResult;
import ai.firesphere.notification.provider.WhatsAppNotificationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic test mock provider for WhatsApp message notifications.
 */
@Component
@ConditionalOnMissingBean(name = "productionWhatsAppProvider")
public class MockWhatsAppProvider implements WhatsAppNotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(MockWhatsAppProvider.class);

    public record MockWhatsAppMessage(
            String phoneNumber,
            String message,
            Instant timestamp,
            String messageId
    ) {}

    private final List<MockWhatsAppMessage> deliveredWhatsApp = new CopyOnWriteArrayList<>();
    private volatile boolean simulateFailure = false;
    private final AtomicInteger failNextAttempts = new AtomicInteger(0);
    private volatile String failureReason = "Simulated WhatsApp Business API rate limit / endpoint error";

    @Override
    public ProviderDeliveryResult sendWhatsApp(String phoneNumber, String message) {
        if (simulateFailure || failNextAttempts.getAndDecrement() > 0) {
            log.warn("[MOCK WHATSAPP] Simulating failure for phone: {}", phoneNumber);
            return ProviderDeliveryResult.failure(failureReason);
        }

        String msgId = "WA-MSG-" + UUID.randomUUID().toString().substring(0, 8);
        MockWhatsAppMessage wa = new MockWhatsAppMessage(phoneNumber, message, Instant.now(), msgId);
        deliveredWhatsApp.add(wa);
        log.info("[MOCK WHATSAPP] Successfully delivered WhatsApp to: {}, msgId: {}", phoneNumber, msgId);
        return ProviderDeliveryResult.success(msgId);
    }

    public void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }

    public void setFailNextAttempts(int count) {
        this.failNextAttempts.set(count);
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public List<MockWhatsAppMessage> getDeliveredWhatsAppMessages() {
        return Collections.unmodifiableList(new ArrayList<>(deliveredWhatsApp));
    }

    public void clear() {
        deliveredWhatsApp.clear();
        simulateFailure = false;
        failNextAttempts.set(0);
    }
}
