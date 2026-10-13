package ai.firesphere.inventory.repository;

import ai.firesphere.inventory.model.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID>, JpaSpecificationExecutor<InventoryItem> {

    Optional<InventoryItem> findByIdAndTenantId(UUID id, UUID tenantId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT i FROM InventoryItem i WHERE i.id = :id AND i.tenantId = :tenantId")
    Optional<InventoryItem> findByIdAndTenantIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id, @org.springframework.data.repository.query.Param("tenantId") UUID tenantId);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsBySkuAndTenantId(String sku, UUID tenantId);

    Optional<InventoryItem> findBySkuAndTenantId(String sku, UUID tenantId);

    Page<InventoryItem> findAllByTenantId(UUID tenantId, Pageable pageable);

    List<InventoryItem> findAllByTenantIdAndStationId(UUID tenantId, UUID stationId);
}
