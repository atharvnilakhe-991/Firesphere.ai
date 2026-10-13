package ai.firesphere.integration.api.whatsapp;

import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;

/**
 * Internal business contract for WhatsApp Business API messaging.
 */
public interface WhatsAppIntegration {

    IntegrationResponse<WhatsAppMessageResult> sendWhatsApp(IntegrationRequest<WhatsAppMessageRequest> request);
}
