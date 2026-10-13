package com.firesphere.backend.common.idempotency.repository;

import com.firesphere.backend.common.idempotency.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, java.util.UUID> {
    
    java.util.Optional<IdempotencyRecord> findByOrganizationIdAndUserIdAndIdempotencyKey(java.util.UUID organizationId, java.util.UUID userId, String idempotencyKey);
    
    @Modifying
    @Query(value = "INSERT INTO idempotency_records (id, organization_id, user_id, idempotency_key, request_fingerprint, status, created_at, updated_at, status_code) VALUES (gen_random_uuid(), :orgId, :userId, :key, :fingerprint, 'IN_PROGRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL)", nativeQuery = true)
    void insertRecord(@Param("orgId") java.util.UUID orgId, @Param("userId") java.util.UUID userId, @Param("key") String key, @Param("fingerprint") String fingerprint);
}
