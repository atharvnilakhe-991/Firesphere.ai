package com.firesphere.backend.payment.repository;

import com.firesphere.backend.payment.entity.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentRecord, UUID> {
    Optional<PaymentRecord> findByReferenceId(String referenceId);
    Optional<PaymentRecord> findByApplicationIdAndStatusNot(UUID applicationId, com.firesphere.backend.payment.entity.PaymentStatus status);
}
