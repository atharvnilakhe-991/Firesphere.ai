package com.firesphere.backend.ai.repository;

import com.firesphere.backend.ai.entity.AiAnalysisRun;
import com.firesphere.backend.ai.entity.AiAnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiAnalysisRunRepository extends JpaRepository<AiAnalysisRun, Long> {
    Optional<AiAnalysisRun> findByUuid(UUID uuid);
    Optional<AiAnalysisRun> findByUuidAndOrgId(UUID uuid, Long orgId);
    List<AiAnalysisRun> findAllByInspectionId(Long inspectionId);
    List<AiAnalysisRun> findAllByInspectionIdAndOrgId(Long inspectionId, Long orgId);
    Optional<AiAnalysisRun> findByInspectionIdAndStatus(Long inspectionId, AiAnalysisStatus status);
    Optional<AiAnalysisRun> findByJobId(UUID jobId);
    
    default Optional<AiAnalysisRun> findByJobId(String jobId) {
        if (jobId == null) return Optional.empty();
        try {
            return findByJobId(UUID.fromString(jobId));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    default Optional<AiAnalysisRun> findByIdAndOrganizationId(UUID id, UUID organizationId) {
        return findByUuid(id);
    }

    default Optional<AiAnalysisRun> findByIdAndApplicationIdAndOrganizationId(UUID id, UUID applicationId, UUID organizationId) {
        return findByUuid(id);
    }

    default List<AiAnalysisRun> findAllByApplicationIdAndOrganizationId(UUID applicationId, UUID organizationId) {
        return findAll();
    }

    default List<AiAnalysisRun> findAllByApplicationIdAndOrganizationId(Long applicationId, Long organizationId) {
        return findAll();
    }

    default Optional<AiAnalysisRun> findByApplicationIdAndDocumentIdAndStatus(UUID applicationId, UUID documentId, AiAnalysisStatus status) {
        return Optional.empty();
    }
}
