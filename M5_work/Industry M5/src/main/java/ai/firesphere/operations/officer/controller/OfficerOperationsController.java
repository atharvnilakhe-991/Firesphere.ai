package ai.firesphere.operations.officer.controller;

import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.operations.officer.dto.*;
import ai.firesphere.operations.officer.model.OfficerTaskStatus;
import ai.firesphere.operations.officer.service.OfficerOperationsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/officer-operations")
@Tag(name = "Officer Operations Support", description = "Endpoints for officer task dispatch, operational status tracking, workload monitoring, and SLA escalations")
public class OfficerOperationsController {

    private final OfficerOperationsService operationsService;

    public OfficerOperationsController(OfficerOperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @PostMapping("/tasks")
    @Operation(summary = "Create officer task", description = "Registers an operational task or inspection assignment")
    public ResponseEntity<OfficerTaskResponseDTO> createTask(@Valid @RequestBody OfficerTaskCreateRequest request) {
        OfficerTaskResponseDTO created = operationsService.createTask(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/tasks/{id}")
    @Operation(summary = "Get task by ID", description = "Retrieves an officer operational task by its unique identifier")
    public ResponseEntity<OfficerTaskResponseDTO> getTaskById(@PathVariable UUID id) {
        return ResponseEntity.ok(operationsService.getTask(id));
    }

    @GetMapping("/tasks/reference/{reference}")
    @Operation(summary = "Get task by reference", description = "Retrieves an officer operational task by unique reference number")
    public ResponseEntity<OfficerTaskResponseDTO> getTaskByReference(@PathVariable String reference) {
        return ResponseEntity.ok(operationsService.getTaskByReference(reference));
    }

    @GetMapping("/tasks")
    @Operation(summary = "Filter tasks", description = "Retrieves tasks filtered by officer badge, status, priority, and date range")
    public ResponseEntity<List<OfficerTaskResponseDTO>> getTasks(
            @RequestParam(required = false) String officerBadge,
            @RequestParam(required = false) OfficerTaskStatus status,
            @RequestParam(required = false) DispatchPriority priority,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate
    ) {
        return ResponseEntity.ok(operationsService.getTasks(officerBadge, status, priority, fromDate, toDate));
    }

    @PutMapping("/tasks/{id}/status")
    @Operation(summary = "Update task status", description = "Updates the operational progression status of an officer task")
    public ResponseEntity<OfficerTaskResponseDTO> updateTaskStatus(
            @PathVariable UUID id,
            @Valid @RequestBody OfficerTaskStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(operationsService.updateTaskStatus(id, request));
    }

    @PatchMapping("/tasks/{id}/checklist")
    @Operation(summary = "Update task checklist and notes", description = "Updates completed checklist items and on-site inspection notes")
    public ResponseEntity<OfficerTaskResponseDTO> updateTaskChecklist(
            @PathVariable UUID id,
            @RequestBody OfficerTaskChecklistUpdateRequest request
    ) {
        return ResponseEntity.ok(operationsService.updateTaskChecklist(id, request));
    }

    @GetMapping("/overdue")
    @Operation(summary = "List overdue tasks", description = "Returns all operational tasks whose deadline has passed")
    public ResponseEntity<List<OfficerTaskResponseDTO>> getOverdueTasks() {
        return ResponseEntity.ok(operationsService.getOverdueTasks());
    }

    @GetMapping("/workload/{officerBadge}")
    @Operation(summary = "Get officer workload summary", description = "Calculates workload metrics, pending counts, and capacity status for an officer")
    public ResponseEntity<OfficerWorkloadSummaryDTO> getOfficerWorkload(@PathVariable String officerBadge) {
        return ResponseEntity.ok(operationsService.getOfficerWorkload(officerBadge));
    }

    @PostMapping("/alerts/evaluate")
    @Operation(summary = "Evaluate operational alerts", description = "Evaluates tasks against SLA criteria and generates deduplicated alerts")
    public ResponseEntity<List<OperationalAlertDTO>> evaluateAlerts() {
        return ResponseEntity.ok(operationsService.evaluateOperationalAlerts());
    }

    @PostMapping("/escalations/evaluate")
    @Operation(summary = "Evaluate escalations", description = "Evaluates tasks against escalation thresholds and promotes eligible tasks")
    public ResponseEntity<List<EscalationResultDTO>> evaluateEscalations() {
        return ResponseEntity.ok(operationsService.evaluateEscalations());
    }

    @PostMapping("/tasks/{id}/escalate")
    @Operation(summary = "Manual task escalation", description = "Manually escalates a task to supervisory oversight")
    public ResponseEntity<EscalationResultDTO> manualEscalate(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String reason = (body != null) ? body.get("reason") : "Manual escalation requested";
        return ResponseEntity.ok(operationsService.manualEscalateTask(id, reason));
    }

    @GetMapping("/escalations/history")
    @Operation(summary = "List escalation history", description = "Retrieves the history of escalated tasks for the current tenant")
    public ResponseEntity<List<EscalationResultDTO>> getEscalationHistory() {
        return ResponseEntity.ok(operationsService.getEscalationHistory());
    }
}
