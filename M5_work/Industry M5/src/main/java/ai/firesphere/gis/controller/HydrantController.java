package ai.firesphere.gis.controller;

import ai.firesphere.gis.dto.HydrantCreateRequest;
import ai.firesphere.gis.dto.HydrantResponseDTO;
import ai.firesphere.gis.dto.HydrantUpdateRequest;
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
@RequestMapping("/api/v1/gis/hydrants")
@Tag(name = "GIS Hydrant Management", description = "Operational APIs for hydrant inventory, status, and spatial proximity lookups")
public class HydrantController {

    private final GisOperationalService gisService;

    public HydrantController(GisOperationalService gisService) {
        this.gisService = gisService;
    }

    @PostMapping
    @Operation(summary = "Register a new hydrant with spatial coordinates")
    public ResponseEntity<HydrantResponseDTO> registerHydrant(@Valid @RequestBody HydrantCreateRequest request) {
        HydrantResponseDTO response = gisService.registerHydrant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve a hydrant by unique identifier")
    public ResponseEntity<HydrantResponseDTO> getHydrantById(@PathVariable UUID id) {
        return ResponseEntity.ok(gisService.getHydrantById(id));
    }

    @GetMapping
    @Operation(summary = "List and paginate hydrants with optional operational status filter")
    public ResponseEntity<Page<HydrantResponseDTO>> getHydrants(
            @RequestParam(required = false) Boolean operable,
            @PageableDefault(size = 20) Pageable pageable) {

        int boundedSize = Math.min(pageable.getPageSize(), 100);
        Pageable bounded = PageRequest.of(pageable.getPageNumber(), boundedSize, pageable.getSort());
        return ResponseEntity.ok(gisService.getHydrants(operable, bounded));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing hydrant's coordinates and metadata")
    public ResponseEntity<HydrantResponseDTO> updateHydrant(
            @PathVariable UUID id,
            @Valid @RequestBody HydrantUpdateRequest request) {
        return ResponseEntity.ok(gisService.updateHydrant(id, request));
    }

    @GetMapping("/nearby")
    @Operation(summary = "Find nearby hydrants using PostGIS spatial proximity index within a specified radius")
    public ResponseEntity<List<HydrantResponseDTO>> findNearbyHydrants(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam double radiusMeters,
            @RequestParam(required = false) Boolean operableOnly) {

        List<HydrantResponseDTO> nearby = gisService.findNearbyHydrants(latitude, longitude, radiusMeters, operableOnly);
        return ResponseEntity.ok(nearby);
    }
}
