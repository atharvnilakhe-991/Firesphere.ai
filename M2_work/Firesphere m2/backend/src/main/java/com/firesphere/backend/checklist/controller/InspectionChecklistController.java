package com.firesphere.backend.checklist.controller;

import com.firesphere.backend.checklist.dto.*;
import com.firesphere.backend.checklist.service.InspectionChecklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inspections")
@Tag(name = "Inspection Checklist", description = "Endpoints for regulatory checklist templates, officer responses, AI advisory links, and completion validation")
public class InspectionChecklistController {

    private final InspectionChecklistService checklistService;

    public InspectionChecklistController(InspectionChecklistService checklistService) {
        this.checklistService = checklistService;
    }

    @GetMapping("/checklist-templates")
    @PreAuthorize("hasAuthority('noc:read') or hasRole('OFFICER') or hasRole('ADMIN') or hasRole('CITIZEN')")
    @Operation(summary = "List available versioned statutory checklist templates")
    public ResponseEntity<List<ChecklistTemplateResponse>> listTemplates(
            @RequestParam(value = "occupancyType", required = false) String occupancyType,
            @RequestParam(value = "inspectionType", required = false) String inspectionType) {
        return ResponseEntity.ok(checklistService.getAvailableTemplates(occupancyType, inspectionType));
    }

    @GetMapping("/checklist-templates/{templateId}")
    @PreAuthorize("hasAuthority('noc:read') or hasRole('OFFICER') or hasRole('ADMIN') or hasRole('CITIZEN')")
    @Operation(summary = "Get specific checklist template details with questions")
    public ResponseEntity<ChecklistTemplateResponse> getTemplate(
            @PathVariable UUID templateId) {
        return ResponseEntity.ok(checklistService.getTemplateByUuid(templateId));
    }

    @PostMapping("/{inspectionId}/checklist")
    @PreAuthorize("hasAuthority('noc:update') or hasRole('OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Initialize an inspection checklist instance from a versioned template")
    public ResponseEntity<InspectionChecklistResponseDto> createChecklist(
            @PathVariable UUID inspectionId,
            @RequestBody(required = false) CreateInspectionChecklistRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(checklistService.createChecklistForInspection(inspectionId, request));
    }

    @GetMapping("/{inspectionId}/checklist")
    @PreAuthorize("hasAuthority('noc:read') or hasRole('OFFICER') or hasRole('ADMIN') or hasRole('CITIZEN')")
    @Operation(summary = "Retrieve inspection checklist with responses, AI suggestions, and attached evidence")
    public ResponseEntity<InspectionChecklistResponseDto> getChecklist(
            @PathVariable UUID inspectionId) {
        return ResponseEntity.ok(checklistService.getChecklistForInspection(inspectionId));
    }

    @GetMapping("/{inspectionId}/checklist/items/{itemId}")
    @PreAuthorize("hasAuthority('noc:read') or hasRole('OFFICER') or hasRole('ADMIN') or hasRole('CITIZEN')")
    @Operation(summary = "Retrieve specific checklist item detail with responses and AI findings")
    public ResponseEntity<InspectionChecklistItemDetailDto> getItemDetail(
            @PathVariable UUID inspectionId,
            @PathVariable UUID itemId) {
        return ResponseEntity.ok(checklistService.getChecklistItemDetail(inspectionId, itemId));
    }

    @PutMapping("/{inspectionId}/checklist/items/{itemId}/response")
    @PreAuthorize("hasAuthority('noc:update') or hasRole('OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Record human officer authoritative response for a checklist question")
    public ResponseEntity<InspectionChecklistResponseDto> recordResponse(
            @PathVariable UUID inspectionId,
            @PathVariable UUID itemId,
            @Valid @RequestBody RecordOfficerResponseRequest request) {
        return ResponseEntity.ok(checklistService.recordOfficerResponse(inspectionId, itemId, request));
    }

    @PostMapping("/{inspectionId}/checklist/items/{itemId}/ai-links")
    @PreAuthorize("hasAuthority('noc:update') or hasRole('OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Link an AI advisory finding to a checklist item")
    public ResponseEntity<InspectionChecklistItemDetailDto> linkAiFinding(
            @PathVariable UUID inspectionId,
            @PathVariable UUID itemId,
            @Valid @RequestBody LinkAiFindingRequest request) {
        return ResponseEntity.ok(checklistService.linkAiFindingToChecklist(inspectionId, itemId, request));
    }

    @PatchMapping("/{inspectionId}/checklist/items/{itemId}/ai-links/{linkId}")
    @PreAuthorize("hasRole('OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Record officer review of an AI advisory suggestion link")
    public ResponseEntity<InspectionChecklistItemDetailDto> reviewAiLink(
            @PathVariable UUID inspectionId,
            @PathVariable UUID itemId,
            @PathVariable UUID linkId,
            @Valid @RequestBody ReviewAiLinkRequest request) {
        return ResponseEntity.ok(checklistService.reviewAiFindingLink(inspectionId, itemId, linkId, request));
    }

    @PostMapping("/{inspectionId}/checklist/map-ai-findings")
    @PreAuthorize("hasAuthority('noc:update') or hasRole('OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Deterministically map AI inspection findings to checklist questions")
    public ResponseEntity<AiChecklistMappingResult> mapAiFindings(
            @PathVariable UUID inspectionId) {
        return ResponseEntity.ok(checklistService.mapAiFindingsToChecklist(inspectionId));
    }

    @GetMapping("/{inspectionId}/checklist/ai-mappings")
    @PreAuthorize("hasAuthority('noc:read') and (hasRole('OFFICER') or hasRole('ADMIN'))")
    @Operation(summary = "Get mapped and unmapped AI inspection findings with explanations and provenance")
    public ResponseEntity<AiChecklistMappingResult> getAiMappings(
            @PathVariable UUID inspectionId) {
        return ResponseEntity.ok(checklistService.getAiChecklistMappings(inspectionId));
    }

    @PostMapping("/{inspectionId}/checklist/items/{itemId}/evidence")
    @PreAuthorize("hasAuthority('noc:update') or hasRole('OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Attach photographic evidence to a checklist item")
    public ResponseEntity<InspectionChecklistItemDetailDto> attachEvidence(
            @PathVariable UUID inspectionId,
            @PathVariable UUID itemId,
            @Valid @RequestBody AttachEvidenceRequest request) {
        return ResponseEntity.ok(checklistService.attachEvidenceToItem(inspectionId, itemId, request));
    }

    @PostMapping("/{inspectionId}/checklist/complete")
    @PreAuthorize("hasRole('OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Submit checklist for completion and statutory validation")
    public ResponseEntity<InspectionChecklistResponseDto> completeChecklist(
            @PathVariable UUID inspectionId,
            @RequestBody(required = false) CompleteChecklistRequest request) {
        return ResponseEntity.ok(checklistService.submitChecklistCompletion(inspectionId, request));
    }
}
