package ai.firesphere.gis.controller;

import ai.firesphere.gis.dto.RouteCalculationRequest;
import ai.firesphere.gis.dto.RouteCalculationResponse;
import ai.firesphere.gis.service.GisOperationalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gis/routes")
@Tag(name = "GIS Route Calculation", description = "Development baseline and contract-oriented routing interface")
public class GisRoutingController {

    private final GisOperationalService gisService;

    public GisRoutingController(GisOperationalService gisService) {
        this.gisService = gisService;
    }

    @PostMapping("/calculate")
    @Operation(summary = "Calculate emergency route using development baseline contract (Development Mock / Non-Production navigation)")
    public ResponseEntity<RouteCalculationResponse> calculateEmergencyRoute(
            @Valid @RequestBody RouteCalculationRequest request) {
        return ResponseEntity.ok(gisService.calculateEmergencyRoute(request));
    }
}
