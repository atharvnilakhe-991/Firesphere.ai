package ai.firesphere.notification.provider;

/**
 * Provider interface for SMS text notifications.
 */
public interface SmsNotificationProvider {

    ProviderDeliveryResult sendSms(String phoneNumber, String message);
}
