package com.firesphere.backend.checklist.repository;

import com.firesphere.backend.checklist.entity.ChecklistTemplateItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChecklistTemplateItemRepository extends JpaRepository<ChecklistTemplateItem, Long> {
    Optional<ChecklistTemplateItem> findByUuid(UUID uuid);
    List<ChecklistTemplateItem> findByTemplateIdOrderByItemSequenceAsc(Long templateId);
    Optional<ChecklistTemplateItem> findByTemplateIdAndItemCode(Long templateId, String itemCode);
}
