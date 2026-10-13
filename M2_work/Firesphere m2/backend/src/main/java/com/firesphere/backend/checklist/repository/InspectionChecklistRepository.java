package com.firesphere.backend.checklist.repository;

import com.firesphere.backend.checklist.entity.InspectionChecklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InspectionChecklistRepository extends JpaRepository<InspectionChecklist, Long> {
    Optional<InspectionChecklist> findByUuid(UUID uuid);
    Optional<InspectionChecklist> findByInspectionIdAndOrgId(Long inspectionId, Long orgId);
    Optional<InspectionChecklist> findByUuidAndOrgId(UUID uuid, Long orgId);
    List<InspectionChecklist> findAllByNocApplicationIdAndOrgId(Long nocApplicationId, Long orgId);
}
