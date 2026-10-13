package ai.firesphere.notification.template;

import ai.firesphere.notification.channel.NotificationChannel;
import ai.firesphere.notification.event.NotificationEventType;

/**
 * Thrown when no registered template matches an event type and channel combination.
 */
public class TemplateNotFoundException extends RuntimeException {

    public TemplateNotFoundException(NotificationEventType eventType, NotificationChannel channel) {
        super(String.format("No notification template found for event '%s' on channel '%s'",
                eventType != null ? eventType.getCode() : "null", channel));
    }
}
