package ai.firesphere.gis.foundation.controller;

import ai.firesphere.gis.foundation.display.MapDisplayDTO;
import ai.firesphere.gis.foundation.dto.LocationValidationRequest;
import ai.firesphere.gis.foundation.dto.MapDisplayRequest;
import ai.firesphere.gis.foundation.dto.NearbyInfrastructureRequest;
import ai.firesphere.gis.foundation.dto.NavigationHandoffRequest;
import ai.firesphere.gis.foundation.infrastructure.NearbyInfrastructureDTO;
import ai.firesphere.gis.foundation.model.LocationValidationResult;
import ai.firesphere.gis.foundation.navigation.OfficerNavigationHandoffDTO;
import ai.firesphere.gis.foundation.service.GisFoundationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing core GIS Foundation operations:
 * - GPS coordinate & accuracy validation
 * - Tactical map display metadata packaging
 * - Nearby fire infrastructure proximity search
 * - Provider-neutral officer navigation handoff
 */
@RestController
@RequestMapping("/api/v1/gis/foundation")
@Tag(name = "GIS Foundation", description = "Operational GIS Foundation APIs for location validation, map display, and infrastructure")
public class GisFoundationController {

    private final GisFoundationService gisFoundationService;

    public GisFoundationController(GisFoundationService gisFoundationService) {
        this.gisFoundationService = gisFoundationService;
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate GPS coordinates and location accuracy")
    public ResponseEntity<LocationValidationResult> validateLocation(
            @Valid @RequestBody LocationValidationRequest request
    ) {
        LocationValidationResult result = gisFoundationService.validateLocation(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/map-display")
    @Operation(summary = "Generate tactical map display metadata and bounding views")
    public ResponseEntity<MapDisplayDTO> getMapDisplay(
            @Valid @RequestBody MapDisplayRequest request
    ) {
        MapDisplayDTO result = gisFoundationService.getMapDisplay(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/nearby-infrastructure")
    @Operation(summary = "Search nearby fire infrastructure around location")
    public ResponseEntity<NearbyInfrastructureDTO> searchNearbyInfrastructure(
            @Valid @RequestBody NearbyInfrastructureRequest request
    ) {
        NearbyInfrastructureDTO result = gisFoundationService.getNearbyInfrastructure(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/nearby-infrastructure")
    @Operation(summary = "Search nearby fire infrastructure via query parameters")
    public ResponseEntity<NearbyInfrastructureDTO> getNearbyInfrastructure(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(required = false, defaultValue = "500.0") double radiusMeters,
            @RequestParam(required = false) Boolean operableOnly
    ) {
        NearbyInfrastructureRequest request = new NearbyInfrastructureRequest(
                latitude, longitude, radiusMeters, operableOnly
        );
        NearbyInfrastructureDTO result = gisFoundationService.getNearbyInfrastructure(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/navigation-handoff")
    @Operation(summary = "Generate provider-neutral officer navigation handoff payload")
    public ResponseEntity<OfficerNavigationHandoffDTO> prepareNavigationHandoff(
            @Valid @RequestBody NavigationHandoffRequest request
    ) {
        OfficerNavigationHandoffDTO result = gisFoundationService.prepareNavigationHandoff(request);
        return ResponseEntity.ok(result);
    }
}
