package ai.firesphere.contracts.notification;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service contract defining multi-channel notification dispatch (FCM, SMS, Email, WhatsApp).
 */
public interface NotificationProviderContract {

    NotificationDispatchReceipt sendPushNotification(String targetToken, String title, String body, Map<String, String> data);

    NotificationDispatchReceipt sendSmsNotification(String phoneNumber, String message);

    NotificationDispatchReceipt broadcastAlert(List<String> targetTokens, String alertType, String title, String message);

    record NotificationDispatchReceipt(
            UUID messageId,
            String channel,
            String recipient,
            String deliveryStatus,
            Instant dispatchedAt
    ) {}
}
