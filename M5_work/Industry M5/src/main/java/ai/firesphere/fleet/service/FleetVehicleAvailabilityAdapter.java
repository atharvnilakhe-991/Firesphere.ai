package ai.firesphere.fleet.service;

import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.contracts.fleet.VehicleAvailabilityContract;
import ai.firesphere.fleet.model.Vehicle;
import ai.firesphere.fleet.model.VehicleStatus;
import ai.firesphere.fleet.model.VehicleType;
import ai.firesphere.fleet.repository.VehicleRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class FleetVehicleAvailabilityAdapter implements VehicleAvailabilityContract {

    private final VehicleRepository vehicleRepository;

    public FleetVehicleAvailabilityAdapter(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    public boolean isVehicleAvailable(UUID vehicleId) {
        UUID tenantId = TenantContext.getTenantId();
        return vehicleRepository.findByIdAndTenantId(vehicleId, tenantId)
                .map(v -> v.getStatus() == VehicleStatus.AVAILABLE)
                .orElse(false);
    }

    @Override
    public Optional<VehicleStatus> getVehicleStatus(UUID vehicleId) {
        UUID tenantId = TenantContext.getTenantId();
        return vehicleRepository.findByIdAndTenantId(vehicleId, tenantId)
                .map(Vehicle::getStatus);
    }

    @Override
    public List<UUID> getAvailableVehiclesByStation(UUID stationId, VehicleType type) {
        UUID tenantId = TenantContext.getTenantId();
        return vehicleRepository.findAllByTenantIdAndAssignedStationId(tenantId, stationId).stream()
                .filter(v -> v.getStatus() == VehicleStatus.AVAILABLE)
                .filter(v -> type == null || v.getVehicleType() == type)
                .map(Vehicle::getId)
                .toList();
    }
}
