package ai.firesphere.integration.provider.email;

import ai.firesphere.integration.api.email.EmailMessageRequest;
import ai.firesphere.integration.api.email.EmailMessageResult;

/**
 * Provider SPI interface for transactional Email services (e.g. SES/SendGrid mock).
 */
public interface EmailProvider {

    String getProviderName();

    EmailMessageResult send(EmailMessageRequest request);
}
