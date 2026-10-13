package com.firesphere.backend.ai.repository;

import com.firesphere.backend.ai.entity.AiAnalysisFinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface AiFindingRepository extends JpaRepository<AiAnalysisFinding, Long> {
    Optional<AiAnalysisFinding> findByUuid(UUID uuid);
    List<AiAnalysisFinding> findAllByRunId(Long runId);
    List<AiAnalysisFinding> findAllByEvidenceId(Long evidenceId);

    @Query("SELECT f FROM AiAnalysisFinding f WHERE f.run.inspectionId = :inspectionId AND f.run.orgId = :orgId")
    List<AiAnalysisFinding> findAllByInspectionIdAndOrgId(@Param("inspectionId") Long inspectionId, @Param("orgId") Long orgId);

    @Query("SELECT f FROM AiAnalysisFinding f WHERE f.run.inspectionId = :inspectionId")
    List<AiAnalysisFinding> findAllByInspectionId(@Param("inspectionId") Long inspectionId);

    default Optional<AiAnalysisFinding> findByIdAndRunId(UUID id, UUID runId) {
        return findByUuid(id);
    }
}
