package ai.firesphere.notification.dto;

import ai.firesphere.notification.model.DeliveryAttempt;
import ai.firesphere.notification.model.NotificationRecord;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public response DTO representing a notification delivery record.
 */
public record NotificationRecordResponseDTO(
        UUID id,
        UUID tenantId,
        String eventType,
        String eventReferenceId,
        String recipient,
        String channel,
        String priority,
        String templateCode,
        String renderedSubject,
        String renderedContent,
        String status,
        int attemptCount,
        int maxAttempts,
        Instant createdAt,
        Instant sentAt,
        String failureReason,
        String idempotencyKey,
        List<DeliveryAttemptDTO> attemptHistory
) {
    public record DeliveryAttemptDTO(
            int attemptNumber,
            Instant attemptedAt,
            String status,
            String errorMessage,
            String providerMessageId
    ) {
        public static DeliveryAttemptDTO fromAttempt(DeliveryAttempt attempt) {
            return new DeliveryAttemptDTO(
                    attempt.attemptNumber(),
                    attempt.attemptedAt(),
                    attempt.status().name(),
                    attempt.errorMessage(),
                    attempt.providerMessageId()
            );
        }
    }

    public static NotificationRecordResponseDTO fromRecord(NotificationRecord record) {
        if (record == null) return null;
        List<DeliveryAttemptDTO> history = record.getAttemptHistory().stream()
                .map(DeliveryAttemptDTO::fromAttempt)
                .toList();

        return new NotificationRecordResponseDTO(
                record.getId(),
                record.getTenantId(),
                record.getEventType() != null ? record.getEventType().getCode() : null,
                record.getEventReferenceId(),
                record.getRecipient(),
                record.getChannel() != null ? record.getChannel().name() : null,
                record.getPriority() != null ? record.getPriority().name() : null,
                record.getTemplateCode(),
                record.getRenderedSubject(),
                record.getRenderedContent(),
                record.getStatus() != null ? record.getStatus().name() : null,
                record.getAttemptCount(),
                record.getMaxAttempts(),
                record.getCreatedAt(),
                record.getSentAt(),
                record.getFailureReason(),
                record.getIdempotencyKey(),
                history
        );
    }
}
