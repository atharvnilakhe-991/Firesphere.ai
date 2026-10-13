package ai.firesphere.notification.repository;

import ai.firesphere.notification.channel.NotificationChannel;
import ai.firesphere.notification.model.NotificationRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository contract for managing notification delivery records and history.
 */
public interface NotificationRecordRepository {

    NotificationRecord save(NotificationRecord record);

    Optional<NotificationRecord> findById(UUID id);

    Optional<NotificationRecord> findByIdempotencyKeyAndChannel(String idempotencyKey, NotificationChannel channel);

    List<NotificationRecord> findAllByTenantId(UUID tenantId);

    List<NotificationRecord> findAllByReferenceId(String referenceId);

    long count();

    void clear();
}
