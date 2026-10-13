package ai.firesphere.notification.model;

/**
 * Lifecycle states of a notification delivery attempt.
 */
public enum NotificationStatus {
    CREATED,
    PENDING,
    SENT,
    FAILED
}
