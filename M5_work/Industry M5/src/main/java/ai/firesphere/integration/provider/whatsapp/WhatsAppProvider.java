package ai.firesphere.integration.provider.whatsapp;

import ai.firesphere.integration.api.whatsapp.WhatsAppMessageRequest;
import ai.firesphere.integration.api.whatsapp.WhatsAppMessageResult;

/**
 * Provider SPI interface for WhatsApp Business API (e.g. Meta Cloud API / Infobip mock).
 */
public interface WhatsAppProvider {

    String getProviderName();

    WhatsAppMessageResult send(WhatsAppMessageRequest request);
}
