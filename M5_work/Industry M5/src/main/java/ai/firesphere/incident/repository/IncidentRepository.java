package ai.firesphere.incident.repository;

import ai.firesphere.incident.model.Incident;
import ai.firesphere.incident.model.IncidentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID>, JpaSpecificationExecutor<Incident> {

    Optional<Incident> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Incident> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<Incident> findAllByTenantIdAndStatus(UUID tenantId, IncidentStatus status, Pageable pageable);

    Optional<Incident> findByIncidentNumberAndTenantId(String incidentNumber, UUID tenantId);

    boolean existsByIncidentNumber(String incidentNumber);
}
