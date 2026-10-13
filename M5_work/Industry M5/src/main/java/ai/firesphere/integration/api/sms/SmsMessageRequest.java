package ai.firesphere.integration.api.sms;

public record SmsMessageRequest(
        String recipientPhoneNumber,
        String messageBody,
        String templateId,
        String senderId
) {}
