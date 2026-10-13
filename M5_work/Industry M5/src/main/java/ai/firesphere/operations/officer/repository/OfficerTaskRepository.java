package ai.firesphere.operations.officer.repository;

import ai.firesphere.operations.officer.model.OfficerTask;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for officer operational assignments and tasks.
 */
public interface OfficerTaskRepository {

    OfficerTask save(OfficerTask task);

    Optional<OfficerTask> findById(UUID taskId, UUID tenantId);

    Optional<OfficerTask> findByReference(String taskReference, UUID tenantId);

    List<OfficerTask> findAll(UUID tenantId);

    List<OfficerTask> findByOfficer(String badge, UUID tenantId);

    boolean delete(UUID taskId, UUID tenantId);

    void clear();
}
