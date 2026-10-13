package com.firesphere.backend.ai.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AiAnalysisResponse(
        UUID runId,
        UUID applicationId,
        UUID documentId,
        String status,
        OffsetDateTime requestedAt,
        OffsetDateTime completedAt,
        String modelName,
        String modelVersion,
        String failureReason,
        List<AiFindingDto> findings,
        UUID inspectionId,
        String cvStatus,
        String vlmStatus,
        String reconciliationStatus,
        String modelApprovalStatus,
        String calibrationStatus
) {
    public AiAnalysisResponse(
            UUID runId,
            UUID applicationId,
            UUID documentId,
            String status,
            OffsetDateTime requestedAt,
            OffsetDateTime completedAt,
            String modelName,
            String modelVersion,
            String failureReason,
            List<AiFindingDto> findings
    ) {
        this(runId, applicationId, documentId, status, requestedAt, completedAt, modelName, modelVersion,
                failureReason, findings, null, "NOT_APPROVED_GATED", "UNAVAILABLE", "SINGLE_PROVIDER",
                "NOT_APPROVED", "NOT_CALIBRATED");
    }

    public record AiFindingDto(
            UUID id,
            UUID evidenceId,
            String finding,
            Double confidence,
            String severity,
            String reviewStatus,
            Boolean isAdvisory,
            String findingType,
            String description,
            String verificationGuidance,
            String boundingBoxJson,
            String provenance,
            String reconciliationStatus,
            String officerNotes,
            Boolean hazardVerified
    ) {
        public AiFindingDto(String finding, Double confidence) {
            this(null, null, finding, confidence, "MEDIUM", "PENDING_REVIEW", true,
                    "OBSERVATION", finding, null, null, "CV_OBJECT_DETECTION", "SINGLE_PROVIDER", null, false);
        }

        public AiFindingDto(
                UUID id,
                UUID evidenceId,
                String finding,
                Double confidence,
                String severity,
                String reviewStatus,
                Boolean isAdvisory
        ) {
            this(id, evidenceId, finding, confidence, severity, reviewStatus, isAdvisory,
                    "OBSERVATION", finding, null, null, "CV_OBJECT_DETECTION", "SINGLE_PROVIDER", null, false);
        }
    }
}
