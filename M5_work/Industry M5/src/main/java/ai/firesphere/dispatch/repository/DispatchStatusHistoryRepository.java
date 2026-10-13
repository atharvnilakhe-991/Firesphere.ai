package ai.firesphere.dispatch.repository;

import ai.firesphere.dispatch.model.DispatchStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DispatchStatusHistoryRepository extends JpaRepository<DispatchStatusHistory, UUID> {

    List<DispatchStatusHistory> findAllByDispatchOrderIdAndTenantIdOrderByTransitionedAtAsc(UUID dispatchOrderId, UUID tenantId);
}
