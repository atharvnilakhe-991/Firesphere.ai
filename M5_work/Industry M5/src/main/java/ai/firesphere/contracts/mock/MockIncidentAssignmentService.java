package ai.firesphere.contracts.mock;

import ai.firesphere.contracts.incident.IncidentAssignmentContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Clean development and testing mock implementation for IncidentAssignmentContract.
 */
@Service
@ConditionalOnMissingBean(name = "productionIncidentAssignmentService")
public class MockIncidentAssignmentService implements IncidentAssignmentContract {

    private static final Logger log = LoggerFactory.getLogger(MockIncidentAssignmentService.class);

    @Override
    public AssignmentResult assignIncident(UUID incidentId, UUID stationId, List<UUID> vehicleIds, List<UUID> responderIds) {
        log.info("[MOCK] Assigning incident {} to station {} with {} vehicles and {} responders",
                incidentId, stationId, vehicleIds.size(), responderIds.size());
        return new AssignmentResult(
                UUID.randomUUID(),
                incidentId,
                stationId,
                true,
                "Assigned successfully via MockIncidentAssignmentService",
                Instant.now()
        );
    }

    @Override
    public List<StationOfficerDTO> getAvailableOfficers(UUID stationId) {
        log.info("[MOCK] Fetching available officers for station {}", stationId);
        return List.of(
                new StationOfficerDTO(UUID.randomUUID(), "FS-101", "Capt. James Miller", "STATION_CAPTAIN", stationId, true),
                new StationOfficerDTO(UUID.randomUUID(), "FS-102", "Lt. Sarah Connor", "LIEUTENANT", stationId, true),
                new StationOfficerDTO(UUID.randomUUID(), "FS-103", "Firefighter John Davis", "FIREFIGHTER", stationId, true)
        );
    }

    @Override
    public boolean verifyOfficerAvailability(UUID officerId) {
        log.info("[MOCK] Verifying availability for officer {}", officerId);
        return true;
    }
}
