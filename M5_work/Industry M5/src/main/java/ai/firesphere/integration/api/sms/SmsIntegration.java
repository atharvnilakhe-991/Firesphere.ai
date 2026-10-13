package ai.firesphere.integration.api.sms;

import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;

/**
 * Internal business contract for SMS gateway integrations.
 */
public interface SmsIntegration {

    IntegrationResponse<SmsMessageResult> sendSms(IntegrationRequest<SmsMessageRequest> request);
}
