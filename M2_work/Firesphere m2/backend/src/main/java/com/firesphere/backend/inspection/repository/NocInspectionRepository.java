package com.firesphere.backend.inspection.repository;

import com.firesphere.backend.inspection.entity.NocInspection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NocInspectionRepository extends JpaRepository<NocInspection, Long> {
    Optional<NocInspection> findByUuid(UUID uuid);
    Optional<NocInspection> findByUuidAndOrgId(UUID uuid, Long orgId);
    List<NocInspection> findAllByNocApplicationId(Long nocApplicationId);
    List<NocInspection> findAllByNocApplicationIdAndOrgId(Long nocApplicationId, Long orgId);
    List<NocInspection> findAllByOrgId(Long orgId);
    List<NocInspection> findAllByAssignedOfficerAndOrgId(UUID assignedOfficer, Long orgId);
    List<NocInspection> findAllByAssignedOfficer(UUID assignedOfficer);
}
