package ai.firesphere.contracts.fleet;

import ai.firesphere.fleet.model.VehicleStatus;
import ai.firesphere.fleet.model.VehicleType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service contract defining inter-module vehicle availability queries for emergency operations.
 */
public interface VehicleAvailabilityContract {

    boolean isVehicleAvailable(UUID vehicleId);

    Optional<VehicleStatus> getVehicleStatus(UUID vehicleId);

    List<UUID> getAvailableVehiclesByStation(UUID stationId, VehicleType type);
}
