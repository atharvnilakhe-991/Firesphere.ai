package ai.firesphere.inventory.repository;

import ai.firesphere.inventory.model.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    Page<StockMovement> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<StockMovement> findAllByItemIdAndTenantIdOrderByCreatedAtDesc(UUID itemId, UUID tenantId, Pageable pageable);

    List<StockMovement> findAllByItemIdAndTenantIdOrderByCreatedAtAsc(UUID itemId, UUID tenantId);

    List<StockMovement> findAllByIncidentIdAndTenantId(UUID incidentId, UUID tenantId);
}
