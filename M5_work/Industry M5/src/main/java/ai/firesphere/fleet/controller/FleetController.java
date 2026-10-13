package ai.firesphere.fleet.controller;

import ai.firesphere.common.dto.PageResponse;
import ai.firesphere.fleet.dto.*;
import ai.firesphere.fleet.model.VehicleStatus;
import ai.firesphere.fleet.model.VehicleType;
import ai.firesphere.fleet.service.FleetService;
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
@RequestMapping("/api/v1/fleet/vehicles")
@Tag(name = "Fleet & Apparatus Operations", description = "Endpoints for vehicle registration, apparatus tracking, readiness states, status transitions, and audit history")
public class FleetController {

    private final FleetService fleetService;

    public FleetController(FleetService fleetService) {
        this.fleetService = fleetService;
    }

    @PostMapping
    @Operation(summary = "Register vehicle apparatus", description = "Registers a new fire apparatus with registration number, station assignment, and capacity profile")
    public ResponseEntity<VehicleResponseDTO> registerVehicle(@Valid @RequestBody VehicleRegisterRequest request) {
        VehicleResponseDTO response = fleetService.registerVehicle(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get vehicle by ID", description = "Retrieves complete vehicle operational details for the current tenant")
    public ResponseEntity<VehicleResponseDTO> getVehicleById(@PathVariable UUID id) {
        return ResponseEntity.ok(fleetService.getVehicle(id));
    }

    @GetMapping
    @Operation(summary = "List vehicles", description = "Paginated list of vehicles with multi-criteria filtering and text search")
    public ResponseEntity<PageResponse<VehicleResponseDTO>> listVehicles(
            @RequestParam(required = false) VehicleStatus status,
            @RequestParam(required = false) VehicleType vehicleType,
            @RequestParam(required = false) UUID stationId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "registrationNumber", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(fleetService.getVehicles(status, vehicleType, stationId, search, pageable));
    }


    @PutMapping("/{id}")
    @Operation(summary = "Update vehicle metadata", description = "Updates mutable vehicle metadata (call sign, station, make, model, capacity). Does NOT modify status.")
    public ResponseEntity<VehicleResponseDTO> updateVehicle(
            @PathVariable UUID id,
            @Valid @RequestBody VehicleUpdateRequest request) {
        return ResponseEntity.ok(fleetService.updateVehicle(id, request));
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Transition vehicle status", description = "Action endpoint for operational status transitions (AVAILABLE, ASSIGNED, DISPATCHED, EN_ROUTE, ON_SCENE, MAINTENANCE, OUT_OF_SERVICE)")
    public ResponseEntity<VehicleResponseDTO> updateVehicleStatus(
            @PathVariable UUID id,
            @Valid @RequestBody VehicleStatusUpdateRequest request) {
        return ResponseEntity.ok(fleetService.updateStatus(id, request));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get vehicle status history", description = "Retrieves the auditable status transition timeline for a vehicle")
    public ResponseEntity<List<VehicleStatusHistoryDTO>> getVehicleHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(fleetService.getVehicleHistory(id));
    }

    @GetMapping("/{id}/readiness")
    @Operation(summary = "Get vehicle readiness state", description = "Returns operational availability and deployment readiness classification for a vehicle")
    public ResponseEntity<VehicleReadinessDTO> getVehicleReadiness(@PathVariable UUID id) {
        return ResponseEntity.ok(fleetService.getVehicleReadiness(id));
    }
}
