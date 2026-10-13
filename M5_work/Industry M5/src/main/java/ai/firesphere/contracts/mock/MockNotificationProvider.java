package ai.firesphere.contracts.mock;

import ai.firesphere.contracts.notification.NotificationProviderContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Clean development and testing mock implementation for NotificationProviderContract.
 */
@Service
@ConditionalOnMissingBean(name = "productionNotificationProvider")
public class MockNotificationProvider implements NotificationProviderContract {

    private static final Logger log = LoggerFactory.getLogger(MockNotificationProvider.class);

    @Override
    public NotificationDispatchReceipt sendPushNotification(String targetToken, String title, String body, Map<String, String> data) {
        log.info("[MOCK FCM PUSH] Target: {}, Title: '{}', Body: '{}', Data: {}", targetToken, title, body, data);
        return new NotificationDispatchReceipt(
                UUID.randomUUID(),
                "FCM_PUSH",
                targetToken,
                "DELIVERED",
                Instant.now()
        );
    }

    @Override
    public NotificationDispatchReceipt sendSmsNotification(String phoneNumber, String message) {
        log.info("[MOCK SMS] Phone: {}, Message: '{}'", phoneNumber, message);
        return new NotificationDispatchReceipt(
                UUID.randomUUID(),
                "SMS",
                phoneNumber,
                "DELIVERED",
                Instant.now()
        );
    }

    @Override
    public NotificationDispatchReceipt broadcastAlert(List<String> targetTokens, String alertType, String title, String message) {
        log.info("[MOCK BROADCAST] Type: {}, Target Count: {}, Title: '{}', Message: '{}'",
                alertType, targetTokens.size(), title, message);
        return new NotificationDispatchReceipt(
                UUID.randomUUID(),
                "BROADCAST",
                String.format("BROADCAST_%d_DEVICES", targetTokens.size()),
                "DELIVERED",
                Instant.now()
        );
    }
}
