package ai.firesphere.integration.provider.sms;

import ai.firesphere.integration.api.sms.SmsMessageRequest;
import ai.firesphere.integration.api.sms.SmsMessageResult;

/**
 * Provider SPI interface for SMS gateways (e.g. Twilio/MSG91 mock).
 */
public interface SmsProvider {

    String getProviderName();

    SmsMessageResult send(SmsMessageRequest request);
}
