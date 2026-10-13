package ai.firesphere.contracts.integration;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Service contract defining integration with external Emergency CAD systems (112/911).
 */
public interface ExternalCadIntegrationContract {

    CadTransmissionReceipt transmitCadIncident(CadIncidentDTO incident);

    Optional<CadIncidentStatusDTO> queryCadIncidentStatus(String externalCadIncidentId);

    record CadIncidentDTO(
            UUID internalIncidentId,
            String incidentNumber,
            String emergencyType,
            String priority,
            double latitude,
            double longitude,
            String address,
            String callerName,
            String callerPhone,
            String narrative
    ) {}

    record CadTransmissionReceipt(
            String externalCadIncidentId,
            UUID internalIncidentId,
            String status,
            Instant transmittedAt
    ) {}

    record CadIncidentStatusDTO(
            String externalCadIncidentId,
            String agencyAssigned,
            String status,
            Instant lastUpdate
    ) {}
}
