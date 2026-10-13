package com.firesphere.backend.ai.repository;

import com.firesphere.backend.ai.entity.AiOfficerReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiOfficerReviewRepository extends JpaRepository<AiOfficerReview, Long> {
    Optional<AiOfficerReview> findByUuid(UUID uuid);

    @Query("SELECT r FROM AiOfficerReview r WHERE r.finding.id = :findingId")
    List<AiOfficerReview> findAllByFindingId(Long findingId);

    List<AiOfficerReview> findAllByOfficerId(Long officerId);

    @Query("SELECT r FROM AiOfficerReview r WHERE r.finding.id = :findingId ORDER BY r.reviewedAt DESC")
    List<AiOfficerReview> findAllByFindingOrderByReviewedAtDesc(Long findingId);

    default Optional<AiOfficerReview> findTopByFindingIdOrderByReviewedAtDesc(Long findingId) {
        List<AiOfficerReview> list = findAllByFindingOrderByReviewedAtDesc(findingId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
