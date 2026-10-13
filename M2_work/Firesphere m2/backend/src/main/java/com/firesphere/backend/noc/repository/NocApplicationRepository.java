package com.firesphere.backend.noc.repository;

import com.firesphere.backend.noc.entity.NocApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NocApplicationRepository extends JpaRepository<NocApplication, Long> {
    Optional<NocApplication> findByUuid(UUID uuid);
    Optional<NocApplication> findByUuidAndOrgId(UUID uuid, Long orgId);
    Page<NocApplication> findAllByOrgId(Long orgId, Pageable pageable);
    
    default Optional<NocApplication> findById(UUID uuid) {
        return findByUuid(uuid);
    }

    default Optional<NocApplication> findByIdAndOrganizationId(UUID id, UUID organizationId) {
        return findByUuid(id);
    }

    default Page<NocApplication> findAllByOrganizationId(UUID organizationId, Pageable pageable) {
        return findAll(pageable);
    }

    default Page<NocApplication> findAllByApplicantIdAndOrganizationId(UUID applicantId, UUID organizationId, Pageable pageable) {
        return findAll(pageable);
    }
}
