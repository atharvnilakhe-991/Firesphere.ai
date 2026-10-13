package ai.firesphere.dispatch.repository;

import ai.firesphere.dispatch.model.DispatchOrder;
import ai.firesphere.dispatch.model.DispatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DispatchOrderRepository extends JpaRepository<DispatchOrder, UUID>, JpaSpecificationExecutor<DispatchOrder> {

    Optional<DispatchOrder> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    Page<DispatchOrder> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<DispatchOrder> findAllByTenantIdAndStatus(UUID tenantId, DispatchStatus status, Pageable pageable);

    List<DispatchOrder> findAllByIncidentIdAndTenantId(UUID incidentId, UUID tenantId);

    List<DispatchOrder> findAllByIncidentIdAndTenantIdAndStatusIn(UUID incidentId, UUID tenantId, Collection<DispatchStatus> statuses);

    Optional<DispatchOrder> findByDispatchCodeAndTenantId(String dispatchCode, UUID tenantId);

    boolean existsByDispatchCode(String dispatchCode);
}
