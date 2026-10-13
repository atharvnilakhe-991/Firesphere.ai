package com.firesphere.backend.checklist.repository;

import com.firesphere.backend.checklist.entity.ChecklistResponseEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChecklistResponseEvidenceRepository extends JpaRepository<ChecklistResponseEvidence, Long> {
    List<ChecklistResponseEvidence> findByResponseId(Long responseId);
    Optional<ChecklistResponseEvidence> findByResponseIdAndEvidenceId(Long responseId, Long evidenceId);
}
