package ai.firesphere.contracts.mock;

import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.contracts.integration.AIInspectionResultProviderContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clean mock implementation for AI Inspection Findings ingest, review, and asynchronous job processing.
 * Enforces Human-in-the-Loop operational workflow and strict input validation boundaries.
 */
@Service
@ConditionalOnMissingBean(name = "productionAIInspectionResultProvider")
public class MockAIInspectionResultProvider implements AIInspectionResultProviderContract {

    private static final Logger log = LoggerFactory.getLogger(MockAIInspectionResultProvider.class);
    private final Map<UUID, AIFindingDTO> store = new ConcurrentHashMap<>();
    private final Map<String, AIJobStatusResponse> jobs = new ConcurrentHashMap<>();

    private volatile boolean autoProcessJobs = true;
    private volatile boolean simulateJobFailure = false;
    private volatile String jobFailureMessage = "AI Inference model execution timed out or container crashed";

    @Override
    public AIFindingIngestResult ingestAIFinding(AIFindingDTO finding) {
        if (finding == null) {
            throw new IllegalArgumentException("AI finding payload cannot be null");
        }
        if (finding.findingType() == null || finding.findingType().isBlank()) {
            throw new IllegalArgumentException("findingType is mandatory for AI inspection findings");
        }
        if (Double.isNaN(finding.confidence()) || Double.isInfinite(finding.confidence())
                || finding.confidence() < 0.0 || finding.confidence() > 1.0) {
            throw new IllegalArgumentException("AI detection confidence score must be between 0.0 and 1.0");
        }

        UUID id = finding.findingId() != null ? finding.findingId() : UUID.randomUUID();
        // Force status to PENDING_REVIEW regardless of incoming payload (Human-in-the-Loop guardrail)
        AIFindingDTO toSave = new AIFindingDTO(
                id,
                finding.incidentOrBuildingId(),
                finding.findingType().trim(),
                finding.confidence(),
                finding.severity() != null ? finding.severity().trim() : "NORMAL",
                finding.evidenceMediaUrl(),
                finding.boundingBoxJson(),
                "PENDING_REVIEW",
                null,
                null,
                Instant.now(),
                null
        );
        store.put(id, toSave);
        log.info("[MOCK AI] Ingested AI finding id={}, type={}, confidence={}", id, toSave.findingType(), toSave.confidence());
        return new AIFindingIngestResult(id, true, "AI finding queued for fire officer review", Instant.now());
    }

    @Override
    public Optional<AIFindingDTO> getFindingById(UUID findingId) {
        return Optional.ofNullable(store.get(findingId));
    }

    @Override
    public AIFindingDTO reviewFinding(UUID findingId, String decision, String reviewerNotes, String reviewerId) {
        AIFindingDTO existing = store.get(findingId);
        if (existing == null) {
            throw new ResourceNotFoundException("AI Inspection finding not found: " + findingId);
        }
        if (decision == null || decision.isBlank()) {
            throw new IllegalArgumentException("Review decision is required");
        }
        String normalizedDecision = decision.trim().toUpperCase();
        if (!normalizedDecision.equals("APPROVED") && !normalizedDecision.equals("DISMISSED") && !normalizedDecision.equals("ESCALATED")) {
            throw new IllegalArgumentException("Invalid review decision: '" + decision + "'. Allowed values: APPROVED, DISMISSED, ESCALATED");
        }
        if (reviewerId == null || reviewerId.isBlank()) {
            throw new IllegalArgumentException("Reviewer officer identity is required");
        }

        AIFindingDTO updated = new AIFindingDTO(
                existing.findingId(),
                existing.incidentOrBuildingId(),
                existing.findingType(),
                existing.confidence(),
                existing.severity(),
                existing.evidenceMediaUrl(),
                existing.boundingBoxJson(),
                normalizedDecision,
                reviewerNotes != null ? reviewerNotes.trim() : null,
                reviewerId.trim(),
                existing.detectedAt(),
                Instant.now()
        );
        store.put(findingId, updated);
        log.info("[MOCK AI REVIEW] Finding {} marked as {} by officer {}", findingId, normalizedDecision, reviewerId);
        return updated;
    }

    @Override
    public AIJobStatusResponse submitEvidenceForAnalysis(AIJobSubmissionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("AI job submission request cannot be null");
        }
        if (request.inspectionId() == null) {
            throw new IllegalArgumentException("inspectionId is mandatory for AI analysis");
        }

        String jobId = request.jobId() != null && !request.jobId().isBlank()
                ? request.jobId()
                : "AI-JOB-" + UUID.randomUUID();

        Instant now = Instant.now();

        if (simulateJobFailure) {
            AIJobStatusResponse failedJob = new AIJobStatusResponse(
                    jobId,
                    request.inspectionId(),
                    AIJobState.FAILED,
                    100,
                    "Analysis execution failed",
                    null,
                    List.of(),
                    jobFailureMessage,
                    now,
                    now
            );
            jobs.put(jobId, failedJob);
            return failedJob;
        }

        if (!autoProcessJobs) {
            AIJobStatusResponse queuedJob = new AIJobStatusResponse(
                    jobId,
                    request.inspectionId(),
                    AIJobState.SUBMITTED,
                    0,
                    "Job queued for computer vision pipeline",
                    null,
                    List.of(),
                    null,
                    now,
                    null
            );
            jobs.put(jobId, queuedJob);
            return queuedJob;
        }

        // Auto-complete simulation: create findings linked to the inspection and evidence
        UUID findingId = UUID.randomUUID();
        String findingType = request.analysisType() != null && !request.analysisType().isBlank()
                ? request.analysisType()
                : "BLOCKED_EXIT";

        String evidenceUrl = request.evidenceMediaUrl() != null
                ? request.evidenceMediaUrl()
                : (request.evidenceFileId() != null ? "mock://storage/evidence/" + request.evidenceFileId() : "mock://storage/evidence/default.jpg");

        AIFindingDTO finding = new AIFindingDTO(
                findingId,
                request.inspectionId(),
                findingType,
                0.94,
                "HIGH",
                evidenceUrl,
                "{\"boxes\":[{\"label\":\"exit_obstruction\",\"confidence\":0.94,\"coordinates\":[120,45,300,420]}]}",
                "PENDING_REVIEW",
                null,
                null,
                now,
                null
        );
        store.put(findingId, finding);

        String explanation = "Computer vision model detected exit corridor obstruction with high confidence (0.94) based on uploaded floor evidence.";
        AIJobStatusResponse completedJob = new AIJobStatusResponse(
                jobId,
                request.inspectionId(),
                AIJobState.COMPLETED,
                100,
                "AI analysis completed with 1 detected safety hazard",
                explanation,
                List.of(finding),
                null,
                now,
                now
        );
        jobs.put(jobId, completedJob);
        log.info("[MOCK AI JOB] Job {} completed with finding {}", jobId, findingId);
        return completedJob;
    }

    @Override
    public Optional<AIJobStatusResponse> getJobStatus(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(jobs.get(jobId));
    }

    public void advanceJobStatus(String jobId, AIJobState newState, int progress, String summary, String explanation, List<AIFindingDTO> findings) {
        AIJobStatusResponse existing = jobs.get(jobId);
        if (existing != null) {
            Instant completedAt = (newState == AIJobState.COMPLETED || newState == AIJobState.FAILED || newState == AIJobState.TIMEOUT)
                    ? Instant.now()
                    : null;
            AIJobStatusResponse updated = new AIJobStatusResponse(
                    jobId,
                    existing.inspectionId(),
                    newState,
                    progress,
                    summary,
                    explanation,
                    findings != null ? findings : existing.generatedFindings(),
                    null,
                    existing.submittedAt(),
                    completedAt
            );
            jobs.put(jobId, updated);
        }
    }

    public void failJob(String jobId, String errorMessage) {
        AIJobStatusResponse existing = jobs.get(jobId);
        if (existing != null) {
            AIJobStatusResponse failed = new AIJobStatusResponse(
                    jobId,
                    existing.inspectionId(),
                    AIJobState.FAILED,
                    100,
                    "Analysis failed",
                    null,
                    List.of(),
                    errorMessage,
                    existing.submittedAt(),
                    Instant.now()
            );
            jobs.put(jobId, failed);
        }
    }

    public void setAutoProcessJobs(boolean autoProcessJobs) {
        this.autoProcessJobs = autoProcessJobs;
    }

    public void setSimulateJobFailure(boolean simulateJobFailure, String failureMessage) {
        this.simulateJobFailure = simulateJobFailure;
        this.jobFailureMessage = failureMessage != null ? failureMessage : this.jobFailureMessage;
    }

    public void clear() {
        store.clear();
        jobs.clear();
        this.autoProcessJobs = true;
        this.simulateJobFailure = false;
    }
}
