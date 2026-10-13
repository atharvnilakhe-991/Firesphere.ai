package ai.firesphere.incident.dto;

import ai.firesphere.incident.model.Incident;
import ai.firesphere.incident.model.IncidentSeverity;
import ai.firesphere.incident.model.IncidentStatus;
import ai.firesphere.incident.model.IncidentType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IncidentResponseDTO(
        UUID id,
        String incidentNumber,
        String title,
        String description,
        IncidentType incidentType,
        IncidentSeverity severity,
        IncidentStatus status,
        Double latitude,
        Double longitude,
        String address,
        String callerName,
        String callerPhone,
        UUID assignedStationId,
        List<String> assignedResources,
        Instant reportedAt,
        Instant verifiedAt,
        Instant dispatchedAt,
        Instant enRouteAt,
        Instant onSceneAt,
        Instant underControlAt,
        Instant resolvedAt,
        Instant closedAt,
        String closureRemarks,
        Instant createdAt,
        Instant updatedAt
) {
    public static IncidentResponseDTO fromEntity(Incident entity) {
        return new IncidentResponseDTO(
                entity.getId(),
                entity.getIncidentNumber(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getIncidentType(),
                entity.getSeverity(),
                entity.getStatus(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getAddress(),
                entity.getCallerName(),
                entity.getCallerPhone(),
                entity.getAssignedStationId(),
                entity.getAssignedResources(),
                entity.getReportedAt(),
                entity.getVerifiedAt(),
                entity.getDispatchedAt(),
                entity.getEnRouteAt(),
                entity.getOnSceneAt(),
                entity.getUnderControlAt(),
                entity.getResolvedAt(),
                entity.getClosedAt(),
                entity.getClosureRemarks(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
