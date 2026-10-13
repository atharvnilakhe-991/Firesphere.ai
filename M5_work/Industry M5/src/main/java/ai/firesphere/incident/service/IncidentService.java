package ai.firesphere.incident.service;

import ai.firesphere.common.dto.PageResponse;
import ai.firesphere.incident.dto.*;
import ai.firesphere.incident.model.IncidentSeverity;
import ai.firesphere.incident.model.IncidentStatus;
import ai.firesphere.incident.model.IncidentType;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface IncidentService {

    IncidentResponseDTO createIncident(IncidentCreateRequest request);

    IncidentResponseDTO getIncident(UUID id);

    IncidentResponseDTO getIncidentByNumber(String incidentNumber);

    PageResponse<IncidentResponseDTO> getIncidents(
            IncidentStatus status,
            IncidentSeverity severity,
            IncidentType incidentType,
            UUID stationId,
            Instant fromDate,
            Instant toDate,
            Pageable pageable
    );

    IncidentResponseDTO updateIncident(UUID id, IncidentUpdateRequest request);

    IncidentResponseDTO updateStatus(UUID id, IncidentStatusUpdateRequest request);

    IncidentResponseDTO closeIncident(UUID id, IncidentCloseRequest request);

    List<IncidentStatusHistoryDTO> getIncidentHistory(UUID id);
}
