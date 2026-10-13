package ai.firesphere.notification.service;

import ai.firesphere.notification.event.NotificationEvent;
import ai.firesphere.notification.model.NotificationRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core notification service contract for event processing, multi-channel dispatch, retry, and history.
 */
public interface NotificationService {

    List<NotificationRecord> processEvent(NotificationEvent event);

    NotificationRecord retryNotification(UUID notificationId);

    Optional<NotificationRecord> getNotificationById(UUID notificationId);

    List<NotificationRecord> getNotificationsByTenant(UUID tenantId);

    List<NotificationRecord> getNotificationsByReferenceId(String referenceId);
}
