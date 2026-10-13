package ai.firesphere.integration.api.whatsapp;

import java.util.Collections;
import java.util.Map;

public record WhatsAppMessageRequest(
        String recipientPhoneNumber,
        String templateName,
        String languageCode,
        Map<String, String> templateParameters,
        String fallbackText
) {
    public WhatsAppMessageRequest {
        if (languageCode == null || languageCode.isBlank()) {
            languageCode = "en";
        }
        if (templateParameters == null) {
            templateParameters = Collections.emptyMap();
        }
    }
}
