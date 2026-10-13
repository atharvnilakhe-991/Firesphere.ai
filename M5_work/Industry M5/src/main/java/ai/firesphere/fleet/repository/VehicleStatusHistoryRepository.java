package ai.firesphere.fleet.repository;

import ai.firesphere.fleet.model.VehicleStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VehicleStatusHistoryRepository extends JpaRepository<VehicleStatusHistory, UUID> {

    List<VehicleStatusHistory> findAllByVehicleIdAndTenantIdOrderByTransitionedAtAsc(UUID vehicleId, UUID tenantId);
}
