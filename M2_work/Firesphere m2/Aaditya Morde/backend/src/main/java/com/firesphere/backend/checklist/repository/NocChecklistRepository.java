package com.firesphere.backend.checklist.repository;

import com.firesphere.backend.checklist.entity.NocChecklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NocChecklistRepository extends JpaRepository<NocChecklist, UUID> {
    List<NocChecklist> findAllByApplicationIdAndOrganizationId(UUID applicationId, UUID organizationId);
    Optional<NocChecklist> findByIdAndApplicationIdAndOrganizationId(UUID id, UUID applicationId, UUID organizationId);
}
