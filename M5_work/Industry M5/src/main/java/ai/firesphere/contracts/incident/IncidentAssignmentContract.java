package ai.firesphere.contracts.incident;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service contract defining integration with Officer/Personnel Roster & Station Operations (Member 1 & Member 2).
 */
public interface IncidentAssignmentContract {

    AssignmentResult assignIncident(UUID incidentId, UUID stationId, List<UUID> vehicleIds, List<UUID> responderIds);

    List<StationOfficerDTO> getAvailableOfficers(UUID stationId);

    boolean verifyOfficerAvailability(UUID officerId);

    record AssignmentResult(
            UUID assignmentId,
            UUID incidentId,
            UUID stationId,
            boolean success,
            String remarks,
            Instant timestamp
    ) {}

    record StationOfficerDTO(
            UUID officerId,
            String badgeNumber,
            String fullName,
            String rank,
            UUID assignedStationId,
            boolean onDuty
    ) {}
}
