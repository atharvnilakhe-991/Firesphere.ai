package com.firesphere.backend.noc.controller;

import com.firesphere.backend.noc.dto.CreateNocRequest;
import com.firesphere.backend.noc.dto.NocResponse;
import com.firesphere.backend.noc.dto.UpdateNocRequest;
import com.firesphere.backend.noc.service.NocService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/noc/applications")
@Tag(name = "NOC Applications", description = "Endpoints for managing Fire NOC applications")
public class NocController {

    private final NocService nocService;
    private final com.firesphere.backend.workflow.service.WorkflowService workflowService;

    public NocController(NocService nocService, com.firesphere.backend.workflow.service.WorkflowService workflowService) {
        this.nocService = nocService;
        this.workflowService = workflowService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('noc:create') or hasRole('CITIZEN')")
    @Operation(summary = "Create a new NOC application in DRAFT status")
    public ResponseEntity<NocResponse> createApplication(@Valid @RequestBody CreateNocRequest request) {
        return ResponseEntity.ok(nocService.createApplication(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('noc:read') or hasRole('CITIZEN')")
    @Operation(summary = "List NOC applications for the current organization")
    public ResponseEntity<Page<NocResponse>> listApplications(@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(nocService.listApplications(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('noc:read') or hasRole('CITIZEN')")
    @Operation(summary = "Get a specific NOC application by ID")
    public ResponseEntity<NocResponse> getApplication(@PathVariable UUID id) {
        return ResponseEntity.ok(nocService.getApplication(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('noc:update') or hasRole('CITIZEN')")
    @Operation(summary = "Update an existing NOC application in DRAFT status")
    public ResponseEntity<NocResponse> updateDraft(@PathVariable UUID id, @Valid @RequestBody UpdateNocRequest request) {
        return ResponseEntity.ok(nocService.updateDraft(id, request));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('noc:update') or hasRole('CITIZEN')")
    @Operation(summary = "Submit a DRAFT NOC application for processing")
    public ResponseEntity<com.firesphere.backend.workflow.dto.WorkflowTransitionResponse> submitApplication(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(
            workflowService.performTransition(id, new com.firesphere.backend.workflow.dto.WorkflowActionRequest(com.firesphere.backend.workflow.model.WorkflowAction.SUBMIT, null), idempotencyKey)
        );
    }
}
