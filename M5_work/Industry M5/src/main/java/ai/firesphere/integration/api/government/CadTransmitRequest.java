package ai.firesphere.integration.api.government;

import java.util.UUID;

public record CadTransmitRequest(
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
