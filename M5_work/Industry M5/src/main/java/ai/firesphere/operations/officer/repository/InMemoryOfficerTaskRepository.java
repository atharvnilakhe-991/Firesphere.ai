package ai.firesphere.operations.officer.repository;

import ai.firesphere.operations.officer.model.OfficerTask;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, tenant-isolated in-memory implementation of OfficerTaskRepository.
 * Enables independent testing and operational state tracking without modifying database schema.
 */
@Component
public class InMemoryOfficerTaskRepository implements OfficerTaskRepository {

    // Key: tenantId -> Map<taskId, OfficerTask>
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, OfficerTask>> tenantTaskStore = new ConcurrentHashMap<>();

    @Override
    public OfficerTask save(OfficerTask task) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        tenantTaskStore.computeIfAbsent(task.tenantId(), k -> new ConcurrentHashMap<>())
                .put(task.taskId(), task);
        return task;
    }

    @Override
    public Optional<OfficerTask> findById(UUID taskId, UUID tenantId) {
        if (taskId == null || tenantId == null) {
            return Optional.empty();
        }
        var store = tenantTaskStore.get(tenantId);
        if (store == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(taskId));
    }

    @Override
    public Optional<OfficerTask> findByReference(String taskReference, UUID tenantId) {
        if (taskReference == null || tenantId == null) {
            return Optional.empty();
        }
        var store = tenantTaskStore.get(tenantId);
        if (store == null) {
            return Optional.empty();
        }
        return store.values().stream()
                .filter(t -> taskReference.equalsIgnoreCase(t.taskReference()))
                .findFirst();
    }

    @Override
    public List<OfficerTask> findAll(UUID tenantId) {
        if (tenantId == null) {
            return Collections.emptyList();
        }
        var store = tenantTaskStore.get(tenantId);
        if (store == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(store.values());
    }

    @Override
    public List<OfficerTask> findByOfficer(String badge, UUID tenantId) {
        if (badge == null || tenantId == null) {
            return Collections.emptyList();
        }
        var store = tenantTaskStore.get(tenantId);
        if (store == null) {
            return Collections.emptyList();
        }
        return store.values().stream()
                .filter(t -> badge.equalsIgnoreCase(t.assignedOfficerBadge()))
                .toList();
    }

    @Override
    public boolean delete(UUID taskId, UUID tenantId) {
        if (taskId == null || tenantId == null) {
            return false;
        }
        var store = tenantTaskStore.get(tenantId);
        if (store == null) {
            return false;
        }
        return store.remove(taskId) != null;
    }

    @Override
    public void clear() {
        tenantTaskStore.clear();
    }
}
