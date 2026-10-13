package ai.firesphere.gis.controller;

import ai.firesphere.gis.dto.HazardZoneCreateRequest;
import ai.firesphere.gis.dto.HazardZoneResponseDTO;
import ai.firesphere.gis.dto.HazardZoneUpdateRequest;
import ai.firesphere.gis.service.GisOperationalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/gis/hazard-zones")
@Tag(name = "GIS Hazard Zone Management", description = "Operational APIs for hazard perimeter mapping, severity protocols, and spatial point-in-zone queries")
public class HazardZoneController {

    private final GisOperationalService gisService;

    public HazardZoneController(GisOperationalService gisService) {
        this.gisService = gisService;
    }

    @PostMapping
    @Operation(summary = "Create a new hazard zone with polygonal or circular buffer geometry")
    public ResponseEntity<HazardZoneResponseDTO> createHazardZone(@Valid @RequestBody HazardZoneCreateRequest request) {
        HazardZoneResponseDTO response = gisService.createHazardZone(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve a hazard zone by unique identifier")
    public ResponseEntity<HazardZoneResponseDTO> getHazardZoneById(@PathVariable UUID id) {
        return ResponseEntity.ok(gisService.getHazardZoneById(id));
    }

    @GetMapping
    @Operation(summary = "List and paginate hazard zones with optional active filter")
    public ResponseEntity<Page<HazardZoneResponseDTO>> getHazardZones(
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20) Pageable pageable) {

        int boundedSize = Math.min(pageable.getPageSize(), 100);
        Pageable bounded = PageRequest.of(pageable.getPageNumber(), boundedSize, pageable.getSort());
        return ResponseEntity.ok(gisService.getHazardZones(active, bounded));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing hazard zone perimeter, severity, or protocol")
    public ResponseEntity<HazardZoneResponseDTO> updateHazardZone(
            @PathVariable UUID id,
            @Valid @RequestBody HazardZoneUpdateRequest request) {
        return ResponseEntity.ok(gisService.updateHazardZone(id, request));
    }

    @GetMapping("/contains")
    @Operation(summary = "Point-in-zone spatial containment query: check if coordinates fall within any active hazard zone")
    public ResponseEntity<List<HazardZoneResponseDTO>> findHazardZonesContainingPoint(
            @RequestParam double latitude,
            @RequestParam double longitude) {
        return ResponseEntity.ok(gisService.findHazardZonesContainingPoint(latitude, longitude));
    }

    @GetMapping("/nearby")
    @Operation(summary = "Find nearby hazard zones within a specified radius")
    public ResponseEntity<List<HazardZoneResponseDTO>> findNearbyHazards(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam double radiusMeters) {
        return ResponseEntity.ok(gisService.findNearbyHazards(latitude, longitude, radiusMeters));
    }
}
