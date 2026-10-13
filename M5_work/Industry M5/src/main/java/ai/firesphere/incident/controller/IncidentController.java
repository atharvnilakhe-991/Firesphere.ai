package ai.firesphere.incident.controller;

import ai.firesphere.common.dto.PageResponse;
import ai.firesphere.incident.dto.*;
import ai.firesphere.incident.model.IncidentSeverity;
import ai.firesphere.incident.model.IncidentStatus;
import ai.firesphere.incident.model.IncidentType;
import ai.firesphere.incident.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents")
@Tag(name = "Incident Operations", description = "Endpoints for emergency incident intake, tracking, updates, lifecycle transitions, and history")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping
    @Operation(summary = "Create emergency incident", description = "Registers a new fire/emergency incident with location and triage priority")
    public ResponseEntity<IncidentResponseDTO> createIncident(@Valid @RequestBody IncidentCreateRequest request) {
        IncidentResponseDTO response = incidentService.createIncident(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get incident by ID", description = "Retrieves complete operational details of an incident by its UUID")
    public ResponseEntity<IncidentResponseDTO> getIncidentById(@PathVariable UUID id) {
        return ResponseEntity.ok(incidentService.getIncident(id));
    }

    @GetMapping("/number/{incidentNumber}")
    @Operation(summary = "Get incident by reference number", description = "Retrieves incident details by its unique reference number")
    public ResponseEntity<IncidentResponseDTO> getIncidentByNumber(@PathVariable String incidentNumber) {
        return ResponseEntity.ok(incidentService.getIncidentByNumber(incidentNumber));
    }

    @GetMapping
    @Operation(summary = "List incidents", description = "Paginated list of incidents with multi-criteria filtering and sorting")
    public ResponseEntity<PageResponse<IncidentResponseDTO>> listIncidents(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentSeverity severity,
            @RequestParam(required = false) IncidentType incidentType,
            @RequestParam(required = false) UUID stationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @PageableDefault(size = 20, sort = "reportedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(incidentService.getIncidents(status, severity, incidentType, stationId, fromDate, toDate, pageable));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update incident attributes", description = "Updates editable incident details (title, description, coordinates, address). Does NOT modify status.")
    public ResponseEntity<IncidentResponseDTO> updateIncident(
            @PathVariable UUID id,
            @Valid @RequestBody IncidentUpdateRequest request) {
        return ResponseEntity.ok(incidentService.updateIncident(id, request));
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Transition incident lifecycle status", description = "Action endpoint for strict sequential state progression (REPORTED -> VERIFIED -> DISPATCHED -> EN_ROUTE -> ON_SCENE -> UNDER_CONTROL -> RESOLVED -> CLOSED)")
    public ResponseEntity<IncidentResponseDTO> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody IncidentStatusUpdateRequest request) {
        return ResponseEntity.ok(incidentService.updateStatus(id, request));
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Close incident", description = "Action endpoint to formally close out a resolved incident with mandatory remarks")
    public ResponseEntity<IncidentResponseDTO> closeIncident(
            @PathVariable UUID id,
            @Valid @RequestBody IncidentCloseRequest request) {
        return ResponseEntity.ok(incidentService.closeIncident(id, request));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get incident transition history", description = "Retrieves complete auditable lifecycle timeline and state transitions for an incident")
    public ResponseEntity<List<IncidentStatusHistoryDTO>> getIncidentHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(incidentService.getIncidentHistory(id));
    }
}
