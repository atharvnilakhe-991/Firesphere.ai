package ai.firesphere.notification.provider.mock;

import ai.firesphere.notification.provider.EmailNotificationProvider;
import ai.firesphere.notification.provider.ProviderDeliveryResult;
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
 * Deterministic test mock provider for Email notifications.
 */
@Component
@ConditionalOnMissingBean(name = "productionEmailProvider")
public class MockEmailProvider implements EmailNotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(MockEmailProvider.class);

    public record MockEmailMessage(
            String emailAddress,
            String subject,
            String body,
            Instant timestamp,
            String messageId
    ) {}

    private final List<MockEmailMessage> deliveredEmails = new CopyOnWriteArrayList<>();
    private volatile boolean simulateFailure = false;
    private final AtomicInteger failNextAttempts = new AtomicInteger(0);
    private volatile String failureReason = "Simulated SMTP connection error: relay rejected";

    @Override
    public ProviderDeliveryResult sendEmail(String emailAddress, String subject, String body) {
        if (simulateFailure || failNextAttempts.getAndDecrement() > 0) {
            log.warn("[MOCK EMAIL] Simulating failure for email: {}", emailAddress);
            return ProviderDeliveryResult.failure(failureReason);
        }

        String msgId = "EMAIL-MSG-" + UUID.randomUUID().toString().substring(0, 8);
        MockEmailMessage email = new MockEmailMessage(emailAddress, subject, body, Instant.now(), msgId);
        deliveredEmails.add(email);
        log.info("[MOCK EMAIL] Successfully delivered Email to: {}, subject: '{}', msgId: {}", emailAddress, subject, msgId);
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

    public List<MockEmailMessage> getDeliveredEmails() {
        return Collections.unmodifiableList(new ArrayList<>(deliveredEmails));
    }

    public void clear() {
        deliveredEmails.clear();
        simulateFailure = false;
        failNextAttempts.set(0);
    }
}
