package ai.firesphere.notification.event;

import ai.firesphere.notification.channel.NotificationChannel;
import ai.firesphere.notification.model.NotificationPriority;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Generic notification event model decoupled from any specific domain subsystem.
 */
public record NotificationEvent(
        NotificationEventType eventType,
        String referenceId,
        UUID tenantId,
        Map<NotificationChannel, String> recipients,
        Map<String, Object> payload,
        Instant timestamp,
        String idempotencyKey,
        NotificationPriority priority,
        Set<NotificationChannel> channels
) {
    public NotificationEvent {
        if (eventType == null) {
            throw new IllegalArgumentException("Notification event type is required");
        }
        if (channels == null || channels.isEmpty()) {
            throw new IllegalArgumentException("At least one notification channel must be specified");
        }
        recipients = recipients != null ? Collections.unmodifiableMap(recipients) : Collections.emptyMap();
        payload = payload != null ? Collections.unmodifiableMap(payload) : Collections.emptyMap();
        channels = Collections.unmodifiableSet(channels);
        timestamp = timestamp != null ? timestamp : Instant.now();
        priority = priority != null ? priority : NotificationPriority.NORMAL;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private NotificationEventType eventType;
        private String referenceId;
        private UUID tenantId;
        private Map<NotificationChannel, String> recipients = Collections.emptyMap();
        private Map<String, Object> payload = Collections.emptyMap();
        private Instant timestamp = Instant.now();
        private String idempotencyKey;
        private NotificationPriority priority = NotificationPriority.NORMAL;
        private Set<NotificationChannel> channels = Collections.emptySet();

        public Builder eventType(NotificationEventType eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder referenceId(String referenceId) {
            this.referenceId = referenceId;
            return this;
        }

        public Builder tenantId(UUID tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder recipients(Map<NotificationChannel, String> recipients) {
            this.recipients = recipients;
            return this;
        }

        public Builder payload(Map<String, Object> payload) {
            this.payload = payload;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder priority(NotificationPriority priority) {
            this.priority = priority;
            return this;
        }

        public Builder channels(Set<NotificationChannel> channels) {
            this.channels = channels;
            return this;
        }

        public NotificationEvent build() {
            return new NotificationEvent(eventType, referenceId, tenantId, recipients, payload, timestamp, idempotencyKey, priority, channels);
        }
    }
}
