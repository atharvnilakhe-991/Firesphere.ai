package ai.firesphere.incident.repository;

import ai.firesphere.incident.model.IncidentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IncidentStatusHistoryRepository extends JpaRepository<IncidentStatusHistory, UUID> {

    List<IncidentStatusHistory> findAllByIncidentIdAndTenantIdOrderByTransitionedAtAsc(UUID incidentId, UUID tenantId);
}
