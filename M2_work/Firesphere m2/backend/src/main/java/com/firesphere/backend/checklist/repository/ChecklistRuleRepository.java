package com.firesphere.backend.checklist.repository;

import com.firesphere.backend.checklist.entity.ChecklistRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChecklistRuleRepository extends JpaRepository<ChecklistRule, UUID> {
    List<ChecklistRule> findByChecklistTypeAndIsActiveTrue(String checklistType);
}
