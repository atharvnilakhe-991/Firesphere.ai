package ai.firesphere.notification.provider;

/**
 * Provider interface for email dispatch.
 */
public interface EmailNotificationProvider {

    ProviderDeliveryResult sendEmail(String emailAddress, String subject, String body);
}
