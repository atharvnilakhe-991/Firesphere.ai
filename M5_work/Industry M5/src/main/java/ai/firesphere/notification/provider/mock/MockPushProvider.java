package ai.firesphere.notification.provider.mock;

import ai.firesphere.notification.provider.ProviderDeliveryResult;
import ai.firesphere.notification.provider.PushNotificationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic test mock provider for Push notifications (FCM / APNs).
 */
@Component
@ConditionalOnMissingBean(name = "productionPushProvider")
public class MockPushProvider implements PushNotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(MockPushProvider.class);

    public record MockPushMessage(
            String targetToken,
            String title,
            String body,
            Map<String, String> data,
            Instant timestamp,
            String messageId
    ) {}

    private final List<MockPushMessage> deliveredPushes = new CopyOnWriteArrayList<>();
    private volatile boolean simulateFailure = false;
    private final AtomicInteger failNextAttempts = new AtomicInteger(0);
    private volatile String failureReason = "Simulated Push delivery network timeout";

    @Override
    public ProviderDeliveryResult sendPush(String targetToken, String title, String body, Map<String, String> data) {
        if (simulateFailure || failNextAttempts.getAndDecrement() > 0) {
            log.warn("[MOCK PUSH] Simulating failure for targetToken: {}", targetToken);
            return ProviderDeliveryResult.failure(failureReason);
        }

        String msgId = "PUSH-MSG-" + UUID.randomUUID().toString().substring(0, 8);
        MockPushMessage message = new MockPushMessage(targetToken, title, body, data, Instant.now(), msgId);
        deliveredPushes.add(message);
        log.info("[MOCK PUSH] Successfully delivered push to: {}, msgId: {}", targetToken, msgId);
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

    public List<MockPushMessage> getDeliveredPushes() {
        return Collections.unmodifiableList(new ArrayList<>(deliveredPushes));
    }

    public void clear() {
        deliveredPushes.clear();
        simulateFailure = false;
        failNextAttempts.set(0);
    }
}
