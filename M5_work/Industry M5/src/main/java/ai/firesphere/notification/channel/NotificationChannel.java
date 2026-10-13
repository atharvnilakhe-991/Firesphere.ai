package ai.firesphere.notification.channel;

/**
 * Supported notification channels.
 */
public enum NotificationChannel {
    PUSH,
    SMS,
    EMAIL,
    WHATSAPP;

    public static NotificationChannel fromString(String channelStr) {
        if (channelStr == null || channelStr.isBlank()) {
            throw new IllegalArgumentException("Notification channel cannot be null or blank");
        }
        try {
            return NotificationChannel.valueOf(channelStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported notification channel: " + channelStr);
        }
    }
}
