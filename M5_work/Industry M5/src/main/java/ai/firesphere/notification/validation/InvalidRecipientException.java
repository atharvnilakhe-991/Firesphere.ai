package ai.firesphere.notification.validation;

import ai.firesphere.notification.channel.NotificationChannel;

/**
 * Thrown when a destination recipient fails channel-specific validation rules.
 */
public class InvalidRecipientException extends RuntimeException {

    private final NotificationChannel channel;
    private final String recipient;

    public InvalidRecipientException(NotificationChannel channel, String recipient, String message) {
        super(String.format("Invalid recipient '%s' for channel %s: %s", recipient, channel, message));
        this.channel = channel;
        this.recipient = recipient;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public String getRecipient() {
        return recipient;
    }
}
