package ai.firesphere.dispatch.dto;

import ai.firesphere.incident.model.IncidentSeverity;
import ai.firesphere.incident.model.IncidentType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DispatchRecommendationRequest(
        @NotNull(message = "Incident ID is required")
        UUID incidentId,

        @NotNull(message = "Incident type is required")
        IncidentType incidentType,

        @NotNull(message = "Severity is required")
        IncidentSeverity severity,

        @NotNull(message = "Latitude is required")
        Double latitude,

        @NotNull(message = "Longitude is required")
        Double longitude
) {}
