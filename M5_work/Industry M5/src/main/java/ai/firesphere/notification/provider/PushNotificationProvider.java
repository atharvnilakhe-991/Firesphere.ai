package ai.firesphere.notification.provider;

import java.util.Map;

/**
 * Provider interface for mobile push notifications (FCM / APNs).
 */
public interface PushNotificationProvider {

    ProviderDeliveryResult sendPush(String targetToken, String title, String body, Map<String, String> data);
}
