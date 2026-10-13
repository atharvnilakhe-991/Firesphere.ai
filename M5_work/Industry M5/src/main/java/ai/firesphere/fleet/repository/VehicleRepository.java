package ai.firesphere.fleet.repository;

import ai.firesphere.fleet.model.Vehicle;
import ai.firesphere.fleet.model.VehicleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID>, JpaSpecificationExecutor<Vehicle> {

    Optional<Vehicle> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByRegistrationNumber(String registrationNumber);

    boolean existsByRegistrationNumberAndTenantId(String registrationNumber, UUID tenantId);

    Page<Vehicle> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<Vehicle> findAllByTenantIdAndStatus(UUID tenantId, VehicleStatus status, Pageable pageable);

    List<Vehicle> findAllByTenantIdAndAssignedStationId(UUID tenantId, UUID assignedStationId);

    Optional<Vehicle> findByRegistrationNumberAndTenantId(String registrationNumber, UUID tenantId);
}
