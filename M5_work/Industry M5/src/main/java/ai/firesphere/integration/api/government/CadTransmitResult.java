package ai.firesphere.integration.api.government;

import java.time.Instant;
import java.util.UUID;

public record CadTransmitResult(
        String externalCadIncidentId,
        UUID internalIncidentId,
        String status, // TRANSMITTED, QUEUED, REJECTED
        String assignedAgency,
        Instant transmittedAt
) {}
