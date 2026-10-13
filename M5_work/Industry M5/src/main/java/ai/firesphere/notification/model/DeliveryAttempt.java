package ai.firesphere.notification.model;

import java.time.Instant;

/**
 * Immutable historical record of a single delivery attempt.
 */
public record DeliveryAttempt(
        int attemptNumber,
        Instant attemptedAt,
        NotificationStatus status,
        String errorMessage,
        String providerMessageId
) {}
