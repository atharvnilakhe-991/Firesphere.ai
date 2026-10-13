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
@RequestMapping("/api/v1/officer/assignments")
@Tag(name = "Officer Assignments", description = "Endpoints for officers to retrieve their assigned field inspections")
public class OfficerAssignmentController {

    private final NocInspectionService inspectionService;

    public OfficerAssignmentController(NocInspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    @GetMapping
    @PreAuthorize("hasRole('OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Get list of assigned inspections for the authenticated officer")
    public ResponseEntity<List<OfficerAssignmentResponse>> getMyAssignments() {
        return ResponseEntity.ok(inspectionService.getAssignmentsForCurrentOfficer());
    }
}
