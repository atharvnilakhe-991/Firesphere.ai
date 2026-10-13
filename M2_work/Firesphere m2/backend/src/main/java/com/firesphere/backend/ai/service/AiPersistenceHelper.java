package com.firesphere.backend.ai.service;

import com.firesphere.backend.ai.dto.ExternalAiResponse;
import com.firesphere.backend.ai.dto.ReviewFindingRequest;
import com.firesphere.backend.ai.entity.AiAnalysisFinding;
import com.firesphere.backend.ai.entity.AiAnalysisRun;
import com.firesphere.backend.ai.entity.AiAnalysisStatus;
import com.firesphere.backend.ai.entity.AiFindingAudit;
import com.firesphere.backend.ai.entity.AiOfficerReview;
import com.firesphere.backend.ai.repository.AiAnalysisRunRepository;
import com.firesphere.backend.ai.repository.AiFindingAuditRepository;
import com.firesphere.backend.ai.repository.AiFindingRepository;
import com.firesphere.backend.ai.repository.AiOfficerReviewRepository;
import com.firesphere.backend.common.audit.AuditEventType;
import com.firesphere.backend.common.audit.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Component
public class AiPersistenceHelper {

    private static final Logger log = LoggerFactory.getLogger(AiPersistenceHelper.class);

    private final AiAnalysisRunRepository runRepository;
    private final AiFindingRepository findingRepository;
    private final AiOfficerReviewRepository officerReviewRepository;
    private final AiFindingAuditRepository auditRepository;
    private final AuditLogger auditLogger;

    public AiPersistenceHelper(
            AiAnalysisRunRepository runRepository,
            AiFindingRepository findingRepository,
            AiOfficerReviewRepository officerReviewRepository,
            AiFindingAuditRepository auditRepository,
            AuditLogger auditLogger) {
        this.runRepository = runRepository;
        this.findingRepository = findingRepository;
        this.officerReviewRepository = officerReviewRepository;
        this.auditRepository = auditRepository;
        this.auditLogger = auditLogger;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiAnalysisRun createProcessingRun(Long internalOrgId, Long internalInspectionId, UUID publicInspectionUuid, UUID applicationId, UUID documentId, Long internalUserId) {
        AiAnalysisRun run = new AiAnalysisRun();
        run.setOrgId(internalOrgId != null ? internalOrgId : 1L);
        run.setInspectionId(internalInspectionId);
        run.setInspectionUuid(publicInspectionUuid);
        run.setApplicationId(applicationId);
        run.setDocumentId(documentId);
        run.setRequestedByUserId(internalUserId);
        run.setJobId(UUID.randomUUID());
        run.setStatus(AiAnalysisStatus.PROCESSING);
        run.setRequestedAt(OffsetDateTime.now());

        run = runRepository.save(run);
        auditLogger.log(AuditEventType.AI_ANALYSIS_REQUESTED, run.getUuid(), "AI Analysis requested and PROCESSING record committed");
        log.info("Committed Transaction A: Created PROCESSING run [uuid={}, id={}] for inspection [id={}]", run.getUuid(), run.getId(), internalInspectionId);
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiAnalysisRun recordRunSuccess(UUID runUuid, ExternalAiResponse externalRes, Map<UUID, Long> evidenceUuidToIdMap) {
        AiAnalysisRun run = runRepository.findByUuid(runUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found for UUID: " + runUuid));

        run.setStatus(AiAnalysisStatus.COMPLETED);
        run.setModelName(externalRes.modelName() != null ? externalRes.modelName() : externalRes.model());
        run.setModelVersion(externalRes.modelVersion());
        run.setModelProvider("PYTORCH_ONNX_EMBEDDED");
        run.setCompletedAt(OffsetDateTime.now());

        if (externalRes.findings() != null) {
            for (ExternalAiResponse.ExternalAiFinding f : externalRes.findings()) {
                if (f.confidence() != null && (f.confidence() < 0.0 || f.confidence() > 1.0)) {
                    log.warn("Invalid confidence [{}] received from AI service for run [{}]", f.confidence(), runUuid);
                    run.setStatus(AiAnalysisStatus.FAILED);
                    run.setFailureReason("Invalid confidence value from AI service");
                    auditLogger.log(AuditEventType.AI_ANALYSIS_FAILED, run.getUuid(), "AI Analysis failed due to out-of-range confidence");
                    return runRepository.save(run);
                }

                AiAnalysisFinding finding = new AiAnalysisFinding();
                finding.setRun(run);

                Long internalEvidenceId = null;
                if (f.evidenceId() != null && evidenceUuidToIdMap != null) {
                    internalEvidenceId = evidenceUuidToIdMap.get(f.evidenceId());
                }
                finding.setEvidenceId(internalEvidenceId);
                finding.setEvidenceUuid(f.evidenceId());

                finding.setFindingType(f.findingType() != null ? f.findingType() : (f.label() != null ? f.label() : "OBSERVATION"));
                finding.setFinding(f.findingType() != null ? f.findingType() : (f.label() != null ? f.label() : "OBSERVATION"));
                finding.setConfidence(f.confidence() != null ? f.confidence() : 0.0);
                finding.setSeverity(f.severity() != null ? f.severity() : "MEDIUM");
                finding.setDescription(f.explanation());
                finding.setVerificationGuidance(f.verificationGuidance() != null ? String.join("; ", f.verificationGuidance()) : null);
                finding.setIsAdvisory(true); // Statutory safeguard enforced
                finding.setReviewStatus("PENDING_REVIEW");

                if (f.boundingBox() != null) {
                    finding.setBoundingBoxJson(String.format("{\"xMin\":%f,\"yMin\":%f,\"xMax\":%f,\"yMax\":%f}",
                            f.boundingBox().xMin(), f.boundingBox().yMin(), f.boundingBox().xMax(), f.boundingBox().yMax()));
                }

                run.getFindings().add(finding);
            }
        }

        run = runRepository.save(run);
        auditLogger.log(AuditEventType.AI_ANALYSIS_COMPLETED, run.getUuid(), "AI Analysis successfully completed and persisted");
        log.info("Committed Transaction B (Success): Completed run [uuid={}] with {} findings", run.getUuid(), run.getFindings().size());
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiAnalysisRun recordRunFailure(UUID runUuid, String failureReason) {
        AiAnalysisRun run = runRepository.findByUuid(runUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found for UUID: " + runUuid));

        run.setStatus(AiAnalysisStatus.FAILED);
        run.setFailureReason(failureReason != null ? failureReason : "AI Service inference failed");
        run.setCompletedAt(OffsetDateTime.now());

        run = runRepository.save(run);
        auditLogger.log(AuditEventType.AI_ANALYSIS_FAILED, run.getUuid(), "AI Analysis failed: " + run.getFailureReason());
        log.info("Committed Transaction B (Failure): Marked run [uuid={}] as FAILED", run.getUuid());
        return run;
    }

    @Transactional
    public AiOfficerReview recordOfficerReview(UUID findingUuid, Long expectedRunId, Long internalOrgId, Long internalOfficerId, ReviewFindingRequest request) {
        AiAnalysisFinding finding = findingRepository.findByUuid(findingUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI Finding not found"));

        if (expectedRunId != null && (finding.getRun() == null || !expectedRunId.equals(finding.getRun().getId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Finding does not belong to specified analysis run");
        }

        if (internalOrgId != null && finding.getRun() != null && finding.getRun().getOrgId() != null
                && !internalOrgId.equals(finding.getRun().getOrgId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant access prohibited");
        }

        // Idempotency check: if latest review has identical status, hazard validation, and notes, return existing review
        java.util.Optional<AiOfficerReview> latestOpt = officerReviewRepository.findTopByFindingIdOrderByReviewedAtDesc(finding.getId());
        if (latestOpt.isPresent()) {
            AiOfficerReview latest = latestOpt.get();
            boolean sameStatus = request.reviewStatus().equals(latest.getReviewStatus());
            boolean sameHazard = Boolean.valueOf(latest.getHazardVerified()).equals(Boolean.TRUE.equals(request.hazardVerified()));
            boolean sameNotes = (request.officerNotes() == null && latest.getOfficerNotes() == null) ||
                    (request.officerNotes() != null && request.officerNotes().equals(latest.getOfficerNotes()));
            if (sameStatus && sameHazard && sameNotes) {
                log.info("Idempotent review submission for finding [{}]. Returning existing review [id={}]", findingUuid, latest.getId());
                return latest;
            }
        }

        String previousState = finding.getReviewStatus();
        finding.setReviewStatus(request.reviewStatus());
        findingRepository.save(finding);

        AiOfficerReview review = new AiOfficerReview();
        review.setFinding(finding);
        review.setOfficerId(internalOfficerId != null ? internalOfficerId : 2L);
        review.setReviewStatus(request.reviewStatus());
        review.setOfficerNotes(request.officerNotes());
        review.setHazardVerified(request.hazardVerified() != null ? request.hazardVerified() : false);
        review.setReviewedAt(OffsetDateTime.now());
        review = officerReviewRepository.save(review);

        AiFindingAudit audit = new AiFindingAudit();
        audit.setFinding(finding);
        audit.setCorrelationId(UUID.randomUUID());
        audit.setAction("OFFICER_REVIEW");
        audit.setPreviousState(previousState);
        audit.setNewState(request.reviewStatus());
        audit.setPerformedByUserId(internalOfficerId != null ? internalOfficerId : 2L);
        audit.setPerformedAt(OffsetDateTime.now());
        auditRepository.save(audit);

        auditLogger.log(AuditEventType.INSPECTION_COMPLETED, finding.getUuid(), "Officer reviewed AI finding: " + request.reviewStatus());
        return review;
    }

    @Transactional
    public AiOfficerReview recordOfficerReview(UUID findingUuid, Long internalOfficerId, ReviewFindingRequest request) {
        return recordOfficerReview(findingUuid, null, null, internalOfficerId, request);
    }
}
