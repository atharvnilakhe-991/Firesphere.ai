package ai.firesphere.integration.api.government;

import java.time.Instant;

public record CadStatusQueryResult(
        String externalCadIncidentId,
        String assignedAgency,
        String externalStatus, // EN_ROUTE, ON_SCENE, UNDER_CONTROL, RESOLVED, CLOSED
        String externalNotes,
        Instant lastUpdatedAt
) {}
