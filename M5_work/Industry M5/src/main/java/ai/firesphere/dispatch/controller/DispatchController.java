package ai.firesphere.dispatch.controller;

import ai.firesphere.common.dto.PageResponse;
import ai.firesphere.dispatch.dto.*;
import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.dispatch.model.DispatchStatus;
import ai.firesphere.dispatch.service.DispatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dispatch")
@Tag(name = "Emergency Dispatch Operations", description = "Endpoints for managing dispatch orders, operational status transitions, recommendations, and audit history")
public class DispatchController {

    private final DispatchService dispatchService;

    public DispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @PostMapping("/orders")
    @Operation(summary = "Create dispatch order", description = "Issues a new emergency dispatch turnout order linked to an active incident")
    public ResponseEntity<DispatchResponseDTO> createDispatchOrder(@Valid @RequestBody DispatchCreateRequest request) {
        DispatchResponseDTO response = dispatchService.createDispatch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "Get dispatch order by ID", description = "Retrieves operational details of a dispatch order within the current tenant")
    public ResponseEntity<DispatchResponseDTO> getDispatchOrderById(@PathVariable UUID id) {
        return ResponseEntity.ok(dispatchService.getDispatch(id));
    }

    @GetMapping("/orders")
    @Operation(summary = "List dispatch orders", description = "Paginated list of dispatch orders with multi-criteria filtering and sorting")
    public ResponseEntity<PageResponse<DispatchResponseDTO>> listDispatchOrders(
            @RequestParam(required = false) DispatchStatus status,
            @RequestParam(required = false) DispatchPriority priority,
            @RequestParam(required = false) UUID incidentId,
            @RequestParam(required = false) UUID stationId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(dispatchService.getDispatches(status, priority, incidentId, stationId, pageable));
    }

    @GetMapping("/orders/incident/{incidentId}")
    @Operation(summary = "List dispatch orders by incident", description = "Retrieves all dispatch orders associated with a specific incident")
    public ResponseEntity<List<DispatchResponseDTO>> getDispatchesForIncident(@PathVariable UUID incidentId) {
        return ResponseEntity.ok(dispatchService.getDispatchesForIncident(incidentId));
    }

    @PutMapping("/orders/{id}")
    @Operation(summary = "Update dispatch order metadata", description = "Updates mutable metadata (notes, station, resource references). Does NOT allow changing status.")
    public ResponseEntity<DispatchResponseDTO> updateDispatchOrder(
            @PathVariable UUID id,
            @Valid @RequestBody DispatchUpdateRequest request) {
        return ResponseEntity.ok(dispatchService.updateDispatch(id, request));
    }

    @PostMapping("/orders/{id}/status")
    @Operation(summary = "Transition dispatch order status", description = "Action endpoint for strict sequential state progression (CREATED -> DISPATCHED -> ACKNOWLEDGED -> EN_ROUTE -> ARRIVED -> COMPLETED, or CREATED -> CANCELLED)")
    public ResponseEntity<DispatchResponseDTO> updateDispatchStatus(
            @PathVariable UUID id,
            @Valid @RequestBody DispatchStatusUpdateRequest request) {
        return ResponseEntity.ok(dispatchService.updateDispatchStatus(id, request));
    }

    @GetMapping("/orders/{id}/history")
    @Operation(summary = "Get dispatch order history", description = "Retrieves the auditable status transition timeline for a dispatch order")
    public ResponseEntity<List<DispatchStatusHistoryDTO>> getDispatchHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(dispatchService.getDispatchHistory(id));
    }

    @PostMapping("/recommendations")
    @Operation(summary = "Get dispatch recommendations", description = "Deterministic recommendation engine suggesting apparatus and crew sizing based on incident profile")
    public ResponseEntity<DispatchRecommendationResponse> getRecommendations(@Valid @RequestBody DispatchRecommendationRequest request) {
        return ResponseEntity.ok(dispatchService.getRecommendations(request));
    }
}
