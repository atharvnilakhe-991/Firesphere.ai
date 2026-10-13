package com.firesphere.backend.noc.repository;

import com.firesphere.backend.noc.entity.NocApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface NocApplicationRepository extends JpaRepository<NocApplication, UUID> {
    Page<NocApplication> findAllByOrganizationId(UUID organizationId, Pageable pageable);
    Optional<NocApplication> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Page<NocApplication> findAllByApplicantIdAndOrganizationId(UUID applicantId, UUID organizationId, Pageable pageable);
}
