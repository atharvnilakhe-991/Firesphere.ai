package com.firesphere.backend.workflow.controller;

import com.firesphere.backend.workflow.dto.WorkflowActionRequest;
import com.firesphere.backend.workflow.dto.WorkflowTransitionResponse;
import com.firesphere.backend.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/noc/applications")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping("/{id}/transitions")
    @PreAuthorize("hasAuthority('noc:update') or hasAuthority('noc:approve') or hasRole('CITIZEN')")
    public ResponseEntity<WorkflowTransitionResponse> performTransition(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody WorkflowActionRequest request) {
        return ResponseEntity.ok(workflowService.performTransition(id, request, idempotencyKey));
    }
}
