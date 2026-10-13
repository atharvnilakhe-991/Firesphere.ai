package ai.firesphere.notification.event;

/**
 * Standard supported notification event types.
 */
public enum NotificationEventType {
    INCIDENT_ASSIGNMENT("incident_assignment"),
    DISPATCH("dispatch"),
    VEHICLE_ALERT("vehicle_alert"),
    LOW_INVENTORY("low_inventory"),
    MAINTENANCE_REMINDER("maintenance_reminder"),
    SYSTEM_ALERT("system_alert"),
    INSPECTION_NOTIFICATION("inspection_notification"),
    CERTIFICATE_NOTIFICATION("certificate_notification"),
    APPLICATION_UPDATE("application_update"),
    INSPECTION_REMINDER("inspection_reminder"),
    OFFICER_ASSIGNMENT("officer_assignment"),
    AI_ANALYSIS_COMPLETION("ai_analysis_completion"),
    INSPECTION_REPORT_READY("inspection_report_ready"),
    RENEWAL_REMINDER("renewal_reminder");

    private final String code;

    NotificationEventType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static NotificationEventType fromString(String eventStr) {
        if (eventStr == null || eventStr.isBlank()) {
            throw new IllegalArgumentException("Event type cannot be null or blank");
        }
        String normalized = eventStr.trim().toLowerCase();
        for (NotificationEventType type : values()) {
            if (type.code.equals(normalized) || type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported event type: " + eventStr);
    }
}
