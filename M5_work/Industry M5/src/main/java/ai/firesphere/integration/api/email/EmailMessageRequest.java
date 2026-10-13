package ai.firesphere.integration.api.email;

import java.util.Collections;
import java.util.List;

public record EmailMessageRequest(
        String recipientEmail,
        String subject,
        String htmlBody,
        String textBody,
        List<String> ccAddresses,
        List<String> bccAddresses
) {
    public EmailMessageRequest {
        if (ccAddresses == null) {
            ccAddresses = Collections.emptyList();
        }
        if (bccAddresses == null) {
            bccAddresses = Collections.emptyList();
        }
    }
}
