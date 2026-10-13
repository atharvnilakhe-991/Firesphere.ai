package com.firesphere.backend.ai.repository;

import com.firesphere.backend.ai.entity.AiAnalysisRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiAnalysisRunRepository extends JpaRepository<AiAnalysisRun, UUID> {
    List<AiAnalysisRun> findAllByApplicationIdAndOrganizationId(UUID applicationId, UUID organizationId);
    Optional<AiAnalysisRun> findByIdAndApplicationIdAndOrganizationId(UUID id, UUID applicationId, UUID organizationId);
    
    // For idempotency check
    Optional<AiAnalysisRun> findByApplicationIdAndDocumentIdAndStatus(UUID applicationId, UUID documentId, com.firesphere.backend.ai.entity.AiAnalysisStatus status);
}
