package ai.firesphere.contracts.fleet;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service contract defining integration with Vehicle Telematics & GPS tracking units.
 */
public interface VehicleLocationProviderContract {

    void ingestTelematics(VehicleTelematicsDTO telematics);

    Optional<VehicleTelematicsDTO> getLatestTelematics(UUID vehicleId);

    List<VehicleTelematicsDTO> getVehiclesNear(double lat, double lon, double radiusMeters);

    record VehicleTelematicsDTO(
            UUID vehicleId,
            double latitude,
            double longitude,
            double speedKmh,
            double headingDegrees,
            double fuelLevelPercent,
            double waterTankLevelPercent,
            Instant timestamp
    ) {}
}
