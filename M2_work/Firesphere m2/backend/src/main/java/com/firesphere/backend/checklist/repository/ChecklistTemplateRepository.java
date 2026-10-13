package com.firesphere.backend.checklist.repository;

import com.firesphere.backend.checklist.entity.ChecklistTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChecklistTemplateRepository extends JpaRepository<ChecklistTemplate, Long> {
    Optional<ChecklistTemplate> findByUuid(UUID uuid);
    Optional<ChecklistTemplate> findByChecklistCodeAndVersion(String checklistCode, Integer version);
    List<ChecklistTemplate> findAllByIsActiveTrue();
    List<ChecklistTemplate> findByOccupancyTypeAndIsActiveTrue(String occupancyType);
    List<ChecklistTemplate> findByOccupancyTypeAndInspectionTypeAndIsActiveTrue(String occupancyType, String inspectionType);
}
