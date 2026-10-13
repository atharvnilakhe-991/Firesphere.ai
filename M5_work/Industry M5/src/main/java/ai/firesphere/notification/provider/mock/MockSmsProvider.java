package ai.firesphere.notification.provider.mock;

import ai.firesphere.notification.provider.ProviderDeliveryResult;
import ai.firesphere.notification.provider.SmsNotificationProvider;
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
 * Deterministic test mock provider for SMS notifications.
 */
@Component
@ConditionalOnMissingBean(name = "productionSmsProvider")
public class MockSmsProvider implements SmsNotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(MockSmsProvider.class);

    public record MockSmsMessage(
            String phoneNumber,
            String message,
            Instant timestamp,
            String messageId
    ) {}

    private final List<MockSmsMessage> deliveredSms = new CopyOnWriteArrayList<>();
    private volatile boolean simulateFailure = false;
    private final AtomicInteger failNextAttempts = new AtomicInteger(0);
    private volatile String failureReason = "Simulated SMS gateway failure: upstream provider timeout";

    @Override
    public ProviderDeliveryResult sendSms(String phoneNumber, String message) {
        if (simulateFailure || failNextAttempts.getAndDecrement() > 0) {
            log.warn("[MOCK SMS] Simulating failure for phone: {}", phoneNumber);
            return ProviderDeliveryResult.failure(failureReason);
        }

        String msgId = "SMS-MSG-" + UUID.randomUUID().toString().substring(0, 8);
        MockSmsMessage sms = new MockSmsMessage(phoneNumber, message, Instant.now(), msgId);
        deliveredSms.add(sms);
        log.info("[MOCK SMS] Successfully delivered SMS to: {}, msgId: {}", phoneNumber, msgId);
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

    public List<MockSmsMessage> getDeliveredSms() {
        return Collections.unmodifiableList(new ArrayList<>(deliveredSms));
    }

    public void clear() {
        deliveredSms.clear();
        simulateFailure = false;
        failNextAttempts.set(0);
    }
}
