package com.firesphere.backend.checklist.controller;

import com.firesphere.backend.checklist.dto.ChecklistResponse;
import com.firesphere.backend.checklist.dto.GenerateChecklistRequest;
import com.firesphere.backend.checklist.dto.UpdateChecklistItemRequest;
import com.firesphere.backend.checklist.service.NocChecklistService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/noc/applications/{applicationId}/checklists")
public class ChecklistController {

    private final NocChecklistService checklistService;

    public ChecklistController(NocChecklistService checklistService) {
        this.checklistService = checklistService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('noc:update')")
    public ResponseEntity<ChecklistResponse> generateChecklist(
            @PathVariable UUID applicationId,
            @Valid @RequestBody GenerateChecklistRequest request) {
        return ResponseEntity.ok(checklistService.generateChecklist(applicationId, request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('noc:read') or hasRole('CITIZEN')")
    public ResponseEntity<List<ChecklistResponse>> listChecklists(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(checklistService.listChecklists(applicationId));
    }

    @GetMapping("/{checklistId}")
    @PreAuthorize("hasAuthority('noc:read') or hasRole('CITIZEN')")
    public ResponseEntity<ChecklistResponse> getChecklist(
            @PathVariable UUID applicationId,
            @PathVariable UUID checklistId) {
        return ResponseEntity.ok(checklistService.getChecklist(applicationId, checklistId));
    }

    @PatchMapping("/{checklistId}/items/{itemId}")
    @PreAuthorize("hasAuthority('noc:update')")
    public ResponseEntity<ChecklistResponse> updateChecklistItem(
            @PathVariable UUID applicationId,
            @PathVariable UUID checklistId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateChecklistItemRequest request) {
        return ResponseEntity.ok(checklistService.updateChecklistItem(applicationId, checklistId, itemId, request));
    }
}
