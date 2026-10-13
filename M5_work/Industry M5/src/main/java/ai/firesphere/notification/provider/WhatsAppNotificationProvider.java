package ai.firesphere.notification.provider;

/**
 * Provider interface for WhatsApp message dispatch.
 */
public interface WhatsAppNotificationProvider {

    ProviderDeliveryResult sendWhatsApp(String phoneNumber, String message);
}
