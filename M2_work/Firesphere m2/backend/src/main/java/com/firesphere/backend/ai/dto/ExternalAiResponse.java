package com.firesphere.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalAiResponse(
        UUID runId,
        String status, // e.g., "COMPLETED", "FAILED"
        List<ExternalAiFinding> findings,
        ExternalAiSummary summary,
        String model,
        String modelVersion,
        Double executionDurationMs,
        String tenantId,
        String modelApprovalStatus,
        String calibrationStatus,
        String errorCode,
        String errorDetail
) {
    public static ExternalAiResponse failed(String errorCode, String errorDetail) {
        return new ExternalAiResponse(
                null, "FAILED", List.of(), null, null, null, null, null,
                "NOT_APPROVED", "NOT_CALIBRATED", errorCode, errorDetail
        );
    }

    public String modelName() {
        return model;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExternalAiFinding(
            UUID findingId,
            UUID evidenceId,
            String findingType,
            Double confidence,
            String confidenceLevel,
            String severity,
            String severityReason,
            Integer operationalRiskScore,
            String operationalRiskLevel,
            String explanation,
            List<String> verificationGuidance,
            BoundingBoxDto boundingBox,
            String evidenceQuality,
            String complianceStatus,
            String model,
            String modelVersion,
            Boolean isTestData,
            String modelApprovalStatus,
            String calibrationStatus,
            String validationStatus,
            UUID inspectionId,
            UUID analysisRunId
    ) {
        public String label() {
            return findingType;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BoundingBoxDto(
            Double xMin,
            Double yMin,
            Double xMax,
            Double yMax
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExternalAiSummary(
            int totalFindings,
            int uniqueFindingTypes,
            String highestSeverity,
            String highestRiskLevel,
            Integer highestRiskScore,
            int evidenceCount,
            boolean requiresOfficerReview,
            int findingsRequiringOfficerReview,
            Map<String, Object> confidenceSummary,
            Map<String, Integer> evidenceQualitySummary,
            String calibrationStatus,
            String complianceDecisionNote
    ) {}
}
