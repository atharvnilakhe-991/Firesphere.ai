package ai.firesphere.notification.model;

import ai.firesphere.notification.channel.NotificationChannel;
import ai.firesphere.notification.event.NotificationEventType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Domain record tracking notification lifecycle, delivery state, and attempt history.
 */
public class NotificationRecord {

    private final UUID id;
    private final UUID tenantId;
    private final NotificationEventType eventType;
    private final String eventReferenceId;
    private final String recipient;
    private final NotificationChannel channel;
    private final NotificationPriority priority;
    private final String templateCode;
    private final String renderedSubject;
    private final String renderedContent;
    private final String idempotencyKey;
    private final int maxAttempts;
    private final Instant createdAt;

    private NotificationStatus status;
    private int attemptCount;
    private Instant sentAt;
    private String failureReason;
    private final List<DeliveryAttempt> attemptHistory;

    public NotificationRecord(
            UUID id,
            UUID tenantId,
            NotificationEventType eventType,
            String eventReferenceId,
            String recipient,
            NotificationChannel channel,
            NotificationPriority priority,
            String templateCode,
            String renderedSubject,
            String renderedContent,
            String idempotencyKey,
            int maxAttempts,
            Instant createdAt
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.eventType = eventType;
        this.eventReferenceId = eventReferenceId;
        this.recipient = recipient;
        this.channel = channel;
        this.priority = priority != null ? priority : NotificationPriority.NORMAL;
        this.templateCode = templateCode;
        this.renderedSubject = renderedSubject;
        this.renderedContent = renderedContent;
        this.idempotencyKey = idempotencyKey;
        this.maxAttempts = maxAttempts > 0 ? maxAttempts : 3;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.status = NotificationStatus.CREATED;
        this.attemptCount = 0;
        this.attemptHistory = new ArrayList<>();
    }

    public synchronized void recordAttempt(NotificationStatus attemptStatus, String errorMessage, String providerMessageId) {
        this.attemptCount++;
        Instant now = Instant.now();
        DeliveryAttempt attempt = new DeliveryAttempt(this.attemptCount, now, attemptStatus, errorMessage, providerMessageId);
        this.attemptHistory.add(attempt);

        if (attemptStatus == NotificationStatus.SENT) {
            this.status = NotificationStatus.SENT;
            this.sentAt = now;
            this.failureReason = null;
        } else {
            this.failureReason = errorMessage;
            if (this.attemptCount >= this.maxAttempts) {
                this.status = NotificationStatus.FAILED;
            } else {
                this.status = NotificationStatus.PENDING;
            }
        }
    }

    public synchronized void markFailedImmediately(String reason) {
        this.status = NotificationStatus.FAILED;
        this.failureReason = reason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public NotificationEventType getEventType() {
        return eventType;
    }

    public String getEventReferenceId() {
        return eventReferenceId;
    }

    public String getRecipient() {
        return recipient;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public NotificationPriority getPriority() {
        return priority;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public String getRenderedSubject() {
        return renderedSubject;
    }

    public String getRenderedContent() {
        return renderedContent;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public synchronized NotificationStatus getStatus() {
        return status;
    }

    public synchronized int getAttemptCount() {
        return attemptCount;
    }

    public synchronized Instant getSentAt() {
        return sentAt;
    }

    public synchronized String getFailureReason() {
        return failureReason;
    }

    public synchronized List<DeliveryAttempt> getAttemptHistory() {
        return Collections.unmodifiableList(new ArrayList<>(attemptHistory));
    }
}
