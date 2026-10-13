package com.firesphere.backend.inspection.repository;

import com.firesphere.backend.inspection.entity.NocInspection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NocInspectionRepository extends JpaRepository<NocInspection, UUID> {
    List<NocInspection> findAllByApplicationIdAndOrganizationId(UUID applicationId, UUID organizationId);
    Optional<NocInspection> findByIdAndApplicationIdAndOrganizationId(UUID id, UUID applicationId, UUID organizationId);
}
