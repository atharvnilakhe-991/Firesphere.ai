package com.firesphere.backend.ai.controller;

import com.firesphere.backend.ai.dto.AiAnalysisRequest;
import com.firesphere.backend.ai.dto.AiAnalysisResponse;
import com.firesphere.backend.ai.dto.ReviewFindingRequest;
import com.firesphere.backend.ai.entity.AiOfficerReview;
import com.firesphere.backend.ai.service.AiOrchestrationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/noc/applications/{applicationId}/ai-analysis")
@Tag(name = "AI Analysis", description = "Endpoints for requesting and retrieving AI model analysis for NOC documents")
public class AiAnalysisController {

    private final AiOrchestrationService aiService;

    public AiAnalysisController(AiOrchestrationService aiService) {
        this.aiService = aiService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('noc:ai:request')")
    @Operation(summary = "Request a new AI analysis run for a specific document")
    public ResponseEntity<AiAnalysisResponse> requestAnalysis(
            @PathVariable("applicationId") UUID applicationId,
            @Valid @RequestBody AiAnalysisRequest request) {
        return ResponseEntity.ok(aiService.requestAnalysis(applicationId, request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('noc:ai:read')")
    @Operation(summary = "List all AI analyses for a specific application")
    public ResponseEntity<List<AiAnalysisResponse>> getAnalyses(
            @PathVariable("applicationId") UUID applicationId) {
        return ResponseEntity.ok(aiService.getAnalysesForApplication(applicationId));
    }

    @GetMapping("/{runId}")
    @PreAuthorize("hasAuthority('noc:ai:read')")
    @Operation(summary = "Get a specific AI analysis result by run ID")
    public ResponseEntity<AiAnalysisResponse> getAnalysis(
            @PathVariable("applicationId") UUID applicationId,
            @PathVariable("runId") UUID runId) {
        return ResponseEntity.ok(aiService.getAnalysisById(applicationId, runId));
    }

    @PostMapping("/{runId}/findings/{findingId}/review")
    @PreAuthorize("hasRole('OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Record human officer verification/review of an AI finding")
    public ResponseEntity<AiOfficerReview> reviewFinding(
            @PathVariable("applicationId") UUID applicationId,
            @PathVariable("runId") UUID runId,
            @PathVariable("findingId") UUID findingId,
            @Valid @RequestBody ReviewFindingRequest request) {
        return ResponseEntity.ok(aiService.reviewFinding(applicationId, runId, findingId, request));
    }
}
