package com.firesphere.backend.document.repository;

import com.firesphere.backend.document.entity.NocDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NocDocumentRepository extends JpaRepository<NocDocument, UUID> {
    List<NocDocument> findAllByApplicationIdAndOrganizationId(UUID applicationId, UUID organizationId);
    Optional<NocDocument> findByIdAndApplicationIdAndOrganizationId(UUID id, UUID applicationId, UUID organizationId);
}
