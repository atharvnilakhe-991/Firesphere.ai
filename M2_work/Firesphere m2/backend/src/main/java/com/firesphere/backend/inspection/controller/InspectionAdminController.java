package com.firesphere.backend.inspection.controller;

import com.firesphere.backend.inspection.dto.OfficerAssignmentResponse;
import com.firesphere.backend.inspection.service.NocInspectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inspections")
@Tag(name = "Inspections Admin", description = "Endpoints for cross-application inspection monitoring")
public class InspectionAdminController {

    private final NocInspectionService inspectionService;

    public InspectionAdminController(NocInspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('OFFICER')")
    @Operation(summary = "List all inspections in the current user's organization")
    public ResponseEntity<List<OfficerAssignmentResponse>> getAllInspections() {
        return ResponseEntity.ok(inspectionService.getAllInspectionsForOrganization());
    }
}
