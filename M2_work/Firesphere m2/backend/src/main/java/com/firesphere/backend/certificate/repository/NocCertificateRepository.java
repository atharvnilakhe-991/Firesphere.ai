package com.firesphere.backend.certificate.repository;

import com.firesphere.backend.certificate.entity.NocCertificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NocCertificateRepository extends JpaRepository<NocCertificate, Long> {
    Optional<NocCertificate> findByUuid(UUID uuid);
    Optional<NocCertificate> findByCertificateNumber(String certificateNumber);
    Optional<NocCertificate> findByApplicationId(Long applicationId);

    default Optional<NocCertificate> findByApplicationIdAndOrganizationId(UUID applicationId, UUID organizationId) {
        return findAll().stream()
                .filter(c -> (applicationId != null && (applicationId.equals(c.getApplicationUuid()) || applicationId.equals(c.getUuid())))
                        || (applicationId != null && c.getApplicationId() != null && c.getApplicationId().equals(applicationId.getLeastSignificantBits())))
                .findFirst();
    }

    default Optional<NocCertificate> findByPublicCertificateIdAndOrganizationId(String publicCertificateId, UUID organizationId) {
        return findByCertificateNumber(publicCertificateId);
    }

    default boolean existsByApplicationId(UUID applicationId) {
        return false;
    }
}
