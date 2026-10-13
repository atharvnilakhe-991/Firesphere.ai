package ai.firesphere.contracts.integration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service contract defining integration with Member 4 (AI / Computer Vision inspection results).
 * Enforces Human-in-the-Loop operational workflow: AI findings never trigger automated regulatory decisions.
 */
public interface AIInspectionResultProviderContract {

    AIFindingIngestResult ingestAIFinding(AIFindingDTO finding);

    Optional<AIFindingDTO> getFindingById(UUID findingId);

    AIFindingDTO reviewFinding(UUID findingId, String decision, String reviewerNotes, String reviewerId);

    AIJobStatusResponse submitEvidenceForAnalysis(AIJobSubmissionRequest request);

    Optional<AIJobStatusResponse> getJobStatus(String jobId);

    enum AIJobState {
        SUBMITTED,
        PROCESSING,
        COMPLETED,
        FAILED,
        TIMEOUT
    }

    record AIJobSubmissionRequest(
            String jobId,
            UUID inspectionId,
            String evidenceFileId,
            String evidenceMediaUrl,
            String analysisType,
            Map<String, String> parameters
    ) {
        public AIJobSubmissionRequest {
            if (parameters == null) {
                parameters = Map.of();
            }
        }
    }

    record AIJobStatusResponse(
            String jobId,
            UUID inspectionId,
            AIJobState status,
            int progressPercent,
            String resultSummary,
            String explanation,
            List<AIFindingDTO> generatedFindings,
            String errorMessage,
            Instant submittedAt,
            Instant completedAt
    ) {
        public AIJobStatusResponse {
            if (generatedFindings == null) {
                generatedFindings = List.of();
            }
        }
    }

    record AIFindingDTO(
            UUID findingId,
            UUID incidentOrBuildingId,
            String findingType, // e.g. BLOCKED_EXIT, EXTINGUISHER_MISSING, THERMAL_HOTSPOT
            double confidence,
            String severity, // LOW, MEDIUM, HIGH, CRITICAL
            String evidenceMediaUrl,
            String boundingBoxJson,
            String status, // PENDING_REVIEW, APPROVED, DISMISSED, ESCALATED
            String reviewerNotes,
            String reviewedBy,
            Instant detectedAt,
            Instant reviewedAt
    ) {}

    record AIFindingIngestResult(
            UUID findingId,
            boolean accepted,
            String message,
            Instant timestamp
    ) {}
}
