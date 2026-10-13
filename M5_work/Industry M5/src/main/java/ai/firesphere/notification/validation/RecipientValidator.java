package ai.firesphere.notification.validation;

import ai.firesphere.notification.channel.NotificationChannel;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Validates channel-specific recipient formats before attempting provider dispatch.
 */
@Component
public class RecipientValidator {

    // E.164 phone number pattern: optional +, leading 1-9, total 8 to 15 digits
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{7,14}$");

    // Standard RFC-compliant email address pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    // Device push token pattern: alphanumeric, dash, underscore, colon, dot, length 8 to 256
    private static final Pattern PUSH_TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_\\-:.]{8,256}$");

    public void validate(NotificationChannel channel, String recipient) {
        if (recipient == null || recipient.isBlank()) {
            throw new InvalidRecipientException(channel, recipient, "Recipient destination cannot be null or empty");
        }

        String trimmed = recipient.trim();

        switch (channel) {
            case PUSH -> {
                if (!PUSH_TOKEN_PATTERN.matcher(trimmed).matches()) {
                    throw new InvalidRecipientException(channel, recipient,
                            "Push token must be 8-256 alphanumeric characters without whitespace");
                }
            }
            case SMS -> {
                if (!PHONE_PATTERN.matcher(trimmed).matches()) {
                    throw new InvalidRecipientException(channel, recipient,
                            "Phone number must adhere to valid international E.164 format (e.g. +14155552671 or 919876543210)");
                }
            }
            case EMAIL -> {
                if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
                    throw new InvalidRecipientException(channel, recipient,
                            "Email address format must be valid (e.g. officer@firesphere.ai)");
                }
            }
            case WHATSAPP -> {
                if (!PHONE_PATTERN.matcher(trimmed).matches()) {
                    throw new InvalidRecipientException(channel, recipient,
                            "WhatsApp phone number must adhere to valid E.164 format (e.g. +14155552671)");
                }
            }
            default -> throw new IllegalArgumentException("Unknown channel: " + channel);
        }
    }
}
