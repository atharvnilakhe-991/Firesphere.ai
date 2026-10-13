package com.firesphere.backend.ai.repository;

import com.firesphere.backend.ai.entity.AiFindingAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiFindingAuditRepository extends JpaRepository<AiFindingAudit, Long> {
    Optional<AiFindingAudit> findByUuid(UUID uuid);

    @Query("SELECT a FROM AiFindingAudit a WHERE a.finding.id = :findingId ORDER BY a.performedAt ASC")
    List<AiFindingAudit> findAllByFindingIdOrderByPerformedAtAsc(Long findingId);

    List<AiFindingAudit> findAllByCorrelationId(UUID correlationId);
}
