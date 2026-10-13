package com.firesphere.backend.certificate.repository;

import com.firesphere.backend.certificate.entity.NocCertificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NocCertificateRepository extends JpaRepository<NocCertificate, UUID> {
    Optional<NocCertificate> findByApplicationIdAndOrganizationId(UUID applicationId, UUID organizationId);
    Optional<NocCertificate> findByPublicCertificateIdAndOrganizationId(String publicCertificateId, UUID organizationId);
    boolean existsByApplicationId(UUID applicationId);
}
