package com.firesphere.backend.inspection.repository;

import com.firesphere.backend.inspection.entity.NocInspectionEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NocInspectionEvidenceRepository extends JpaRepository<NocInspectionEvidence, Long> {
    Optional<NocInspectionEvidence> findByUuid(UUID uuid);
    Optional<NocInspectionEvidence> findByUuidAndOrgId(UUID uuid, Long orgId);
    List<NocInspectionEvidence> findAllByInspectionId(Long inspectionId);
    List<NocInspectionEvidence> findAllByInspectionIdAndOrgId(Long inspectionId, Long orgId);
    boolean existsByOrgIdAndSha256Hash(Long orgId, String sha256Hash);
    Optional<NocInspectionEvidence> findByOrgIdAndSha256Hash(Long orgId, String sha256Hash);
}
