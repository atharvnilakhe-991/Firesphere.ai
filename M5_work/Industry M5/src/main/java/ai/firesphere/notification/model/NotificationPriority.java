package ai.firesphere.notification.model;

/**
 * Notification delivery priority levels.
 */
public enum NotificationPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL;

    public static NotificationPriority fromString(String priorityStr) {
        if (priorityStr == null || priorityStr.isBlank()) {
            return NORMAL;
        }
        try {
            return NotificationPriority.valueOf(priorityStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
