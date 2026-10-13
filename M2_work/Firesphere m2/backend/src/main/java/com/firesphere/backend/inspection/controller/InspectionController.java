package com.firesphere.backend.inspection.controller;

import com.firesphere.backend.inspection.dto.InspectionResponse;
import com.firesphere.backend.inspection.dto.ScheduleInspectionRequest;
import com.firesphere.backend.inspection.dto.UpdateInspectionRequest;
import com.firesphere.backend.inspection.service.NocInspectionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/noc/applications/{applicationId}/inspections")
public class InspectionController {

    private final NocInspectionService inspectionService;

    public InspectionController(NocInspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('noc:update')")
    public ResponseEntity<InspectionResponse> scheduleInspection(
            @PathVariable UUID applicationId,
            @Valid @RequestBody ScheduleInspectionRequest request) {
        return ResponseEntity.ok(inspectionService.scheduleInspection(applicationId, request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('noc:read') or hasRole('CITIZEN')")
    public ResponseEntity<List<InspectionResponse>> listInspections(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(inspectionService.listInspections(applicationId));
    }

    @GetMapping("/{inspectionId}")
    @PreAuthorize("hasAuthority('noc:read') or hasRole('CITIZEN')")
    public ResponseEntity<InspectionResponse> getInspection(
            @PathVariable UUID applicationId,
            @PathVariable UUID inspectionId) {
        return ResponseEntity.ok(inspectionService.getInspection(applicationId, inspectionId));
    }

    @PostMapping("/{inspectionId}/start")
    @PreAuthorize("hasAuthority('noc:update')")
    public ResponseEntity<InspectionResponse> startInspection(
            @PathVariable UUID applicationId,
            @PathVariable UUID inspectionId) {
        return ResponseEntity.ok(inspectionService.startInspection(applicationId, inspectionId));
    }

    @PostMapping("/{inspectionId}/complete")
    @PreAuthorize("hasAuthority('noc:update')")
    public ResponseEntity<InspectionResponse> completeInspection(
            @PathVariable UUID applicationId,
            @PathVariable UUID inspectionId,
            @Valid @RequestBody UpdateInspectionRequest request) {
        return ResponseEntity.ok(inspectionService.completeInspection(applicationId, inspectionId, request));
    }
}
