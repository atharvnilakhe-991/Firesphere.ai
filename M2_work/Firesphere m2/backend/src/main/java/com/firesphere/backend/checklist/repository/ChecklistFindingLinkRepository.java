package com.firesphere.backend.checklist.repository;

import com.firesphere.backend.checklist.entity.ChecklistFindingLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface ChecklistFindingLinkRepository extends JpaRepository<ChecklistFindingLink, Long> {
    Optional<ChecklistFindingLink> findByUuid(UUID uuid);
    List<ChecklistFindingLink> findByChecklistResponseId(Long checklistResponseId);
    Optional<ChecklistFindingLink> findByChecklistResponseIdAndFindingId(Long checklistResponseId, Long findingId);

    @Query("SELECT l FROM ChecklistFindingLink l WHERE l.checklistResponse.checklistInstance.id = :checklistId")
    List<ChecklistFindingLink> findAllByChecklistId(@Param("checklistId") Long checklistId);

    @Query("SELECT l FROM ChecklistFindingLink l WHERE l.checklistResponse.checklistInstance.inspectionId = :inspectionId AND l.checklistResponse.checklistInstance.orgId = :orgId")
    List<ChecklistFindingLink> findAllByInspectionIdAndOrgId(@Param("inspectionId") Long inspectionId, @Param("orgId") Long orgId);
}
