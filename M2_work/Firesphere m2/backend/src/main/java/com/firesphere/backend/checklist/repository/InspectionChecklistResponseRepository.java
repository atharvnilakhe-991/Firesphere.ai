package com.firesphere.backend.checklist.repository;

import com.firesphere.backend.checklist.entity.InspectionChecklistResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InspectionChecklistResponseRepository extends JpaRepository<InspectionChecklistResponse, Long> {
    Optional<InspectionChecklistResponse> findByUuid(UUID uuid);
    Optional<InspectionChecklistResponse> findByChecklistInstanceIdAndTemplateItemId(Long checklistInstanceId, Long templateItemId);
    Optional<InspectionChecklistResponse> findByChecklistInstanceIdAndItemCode(Long checklistInstanceId, String itemCode);
    List<InspectionChecklistResponse> findByChecklistInstanceId(Long checklistInstanceId);
}
