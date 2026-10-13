package ai.firesphere.integration.api.email;

import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;

/**
 * Internal business contract for transactional email integrations.
 */
public interface EmailIntegration {

    IntegrationResponse<EmailMessageResult> sendEmail(IntegrationRequest<EmailMessageRequest> request);
}
