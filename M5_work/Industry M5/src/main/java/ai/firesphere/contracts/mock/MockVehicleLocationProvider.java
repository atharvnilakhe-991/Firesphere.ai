package ai.firesphere.contracts.mock;

import ai.firesphere.contracts.fleet.VehicleLocationProviderContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe mock implementation for vehicle telematics tracking.
 */
@Service
@ConditionalOnMissingBean(name = "productionVehicleLocationProvider")
public class MockVehicleLocationProvider implements VehicleLocationProviderContract {

    private static final Logger log = LoggerFactory.getLogger(MockVehicleLocationProvider.class);
    private final Map<UUID, VehicleTelematicsDTO> locationStore = new ConcurrentHashMap<>();

    @Override
    public void ingestTelematics(VehicleTelematicsDTO telematics) {
        log.info("[MOCK TELEMATICS] Vehicle {} updated: [{}, {}], speed: {} km/h, water: {}%",
                telematics.vehicleId(), telematics.latitude(), telematics.longitude(),
                telematics.speedKmh(), telematics.waterTankLevelPercent());
        locationStore.put(telematics.vehicleId(), telematics);
    }

    @Override
    public Optional<VehicleTelematicsDTO> getLatestTelematics(UUID vehicleId) {
        return Optional.ofNullable(locationStore.get(vehicleId))
                .or(() -> Optional.of(new VehicleTelematicsDTO(
                        vehicleId,
                        19.0760, // Default Mumbai/Metropolitan coordinates
                        72.8777,
                        0.0,
                        0.0,
                        95.0,
                        100.0,
                        Instant.now()
                )));
    }

    @Override
    public List<VehicleTelematicsDTO> getVehiclesNear(double lat, double lon, double radiusMeters) {
        log.info("[MOCK TELEMATICS] Querying vehicles near [{}, {}] within {}m", lat, lon, radiusMeters);
        return List.copyOf(locationStore.values());
    }
}
