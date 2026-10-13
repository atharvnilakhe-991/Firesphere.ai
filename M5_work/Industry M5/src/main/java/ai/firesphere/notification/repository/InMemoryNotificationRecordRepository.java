package ai.firesphere.notification.repository;

import ai.firesphere.notification.channel.NotificationChannel;
import ai.firesphere.notification.model.NotificationRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory repository implementation for NotificationRecord.
 * Satisfies Phase 7 repository abstraction requirement without requiring unapproved schema changes.
 */
@Repository
@ConditionalOnMissingBean(name = "productionNotificationRecordRepository")
public class InMemoryNotificationRecordRepository implements NotificationRecordRepository {

    private final Map<UUID, NotificationRecord> storage = new ConcurrentHashMap<>();
    private final Map<String, UUID> idempotencyIndex = new ConcurrentHashMap<>();

    private String buildIdempotencyIndexKey(String idempotencyKey, NotificationChannel channel) {
        return idempotencyKey + ":" + channel.name();
    }

    @Override
    public NotificationRecord save(NotificationRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("Notification record cannot be null");
        }
        storage.put(record.getId(), record);
        if (record.getIdempotencyKey() != null && !record.getIdempotencyKey().isBlank()) {
            String indexKey = buildIdempotencyIndexKey(record.getIdempotencyKey(), record.getChannel());
            idempotencyIndex.putIfAbsent(indexKey, record.getId());
        }
        return record;
    }

    @Override
    public Optional<NotificationRecord> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public Optional<NotificationRecord> findByIdempotencyKeyAndChannel(String idempotencyKey, NotificationChannel channel) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || channel == null) {
            return Optional.empty();
        }
        String indexKey = buildIdempotencyIndexKey(idempotencyKey, channel);
        UUID id = idempotencyIndex.get(indexKey);
        if (id != null) {
            return Optional.ofNullable(storage.get(id));
        }
        return Optional.empty();
    }

    @Override
    public List<NotificationRecord> findAllByTenantId(UUID tenantId) {
        if (tenantId == null) {
            return new ArrayList<>(storage.values());
        }
        return storage.values().stream()
                .filter(r -> tenantId.equals(r.getTenantId()))
                .toList();
    }

    @Override
    public List<NotificationRecord> findAllByReferenceId(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            return List.of();
        }
        return storage.values().stream()
                .filter(r -> referenceId.equals(r.getEventReferenceId()))
                .toList();
    }

    @Override
    public long count() {
        return storage.size();
    }

    @Override
    public void clear() {
        storage.clear();
        idempotencyIndex.clear();
    }
}
