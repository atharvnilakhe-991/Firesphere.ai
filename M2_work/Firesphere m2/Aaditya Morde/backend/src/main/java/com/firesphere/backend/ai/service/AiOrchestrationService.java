package com.firesphere.backend.ai.service;

import com.firesphere.backend.ai.client.AiAnalysisClient;
import com.firesphere.backend.ai.dto.AiAnalysisRequest;
import com.firesphere.backend.ai.dto.AiAnalysisResponse;
import com.firesphere.backend.ai.dto.ExternalAiRequest;
import com.firesphere.backend.ai.dto.ExternalAiResponse;
import com.firesphere.backend.ai.entity.AiAnalysisFinding;
import com.firesphere.backend.ai.entity.AiAnalysisRun;
import com.firesphere.backend.ai.entity.AiAnalysisStatus;
import com.firesphere.backend.ai.repository.AiAnalysisRunRepository;
import com.firesphere.backend.common.audit.AuditEventType;
import com.firesphere.backend.common.audit.AuditLogger;
import com.firesphere.backend.common.security.TenantContext;
import com.firesphere.backend.document.entity.DocumentStatus;
import com.firesphere.backend.document.entity.NocDocument;
import com.firesphere.backend.document.repository.NocDocumentRepository;
import com.firesphere.backend.noc.entity.NocApplication;
import com.firesphere.backend.noc.repository.NocApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AiOrchestrationService {

    private final AiAnalysisRunRepository runRepository;
    private final NocApplicationRepository applicationRepository;
    private final NocDocumentRepository documentRepository;
    private final AiAnalysisClient aiClient;
    private final AuditLogger auditLogger;

    public AiOrchestrationService(AiAnalysisRunRepository runRepository,
                                  NocApplicationRepository applicationRepository,
                                  NocDocumentRepository documentRepository,
                                  AiAnalysisClient aiClient,
                                  AuditLogger auditLogger) {
        this.runRepository = runRepository;
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.aiClient = aiClient;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public AiAnalysisResponse requestAnalysis(UUID applicationId, AiAnalysisRequest request) {
        UUID orgId = TenantContext.getCurrentOrganizationId();

        NocApplication noc = applicationRepository.findByIdAndOrganizationId(applicationId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (!noc.getApplicantId().equals(TenantContext.getCurrentUserId()) && !TenantContext.hasRole("ADMIN") && !TenantContext.hasRole("OFFICER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not authorized to request AI analysis for this application");
        }

        NocDocument document = documentRepository.findByIdAndApplicationIdAndOrganizationId(request.documentId(), applicationId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found or does not belong to application/tenant"));
        
        if (document.getStatus() != DocumentStatus.UPLOADED && document.getStatus() != DocumentStatus.VERIFIED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document must be UPLOADED or VERIFIED to be analyzed");
        }

        // Idempotency: Check if already completed
        Optional<AiAnalysisRun> existingRun = runRepository.findByApplicationIdAndDocumentIdAndStatus(applicationId, request.documentId(), AiAnalysisStatus.COMPLETED);
        if (existingRun.isPresent()) {
            return mapToResponse(existingRun.get());
        }
        
        Optional<AiAnalysisRun> processingRun = runRepository.findByApplicationIdAndDocumentIdAndStatus(applicationId, request.documentId(), AiAnalysisStatus.PROCESSING);
        if (processingRun.isPresent()) {
            return mapToResponse(processingRun.get());
        }

        AiAnalysisRun run = new AiAnalysisRun();
        run.setApplicationId(applicationId);
        run.setOrganizationId(orgId);
        run.setDocumentId(request.documentId());
        run.setStatus(AiAnalysisStatus.PROCESSING);
        run = runRepository.save(run);
        
        auditLogger.log(AuditEventType.AI_ANALYSIS_REQUESTED, run.getId(), "AI Analysis requested for document " + document.getId());

        ExternalAiRequest externalReq = new ExternalAiRequest(
                run.getId(),
                applicationId,
                document.getStorageReference(),
                request.analysisType()
        );

        ExternalAiResponse externalRes = aiClient.analyze(externalReq);

        if ("SUCCESS".equals(externalRes.status())) {
            run.setStatus(AiAnalysisStatus.COMPLETED);
            run.setModelName(externalRes.modelName());
            run.setModelVersion(externalRes.modelVersion());
            run.setCompletedAt(OffsetDateTime.now());

            if (externalRes.findings() != null) {
                for (ExternalAiResponse.Finding f : externalRes.findings()) {
                    if (f.confidence() != null && (f.confidence() < 0.0 || f.confidence() > 1.0)) {
                        run.setStatus(AiAnalysisStatus.FAILED);
                        run.setFailureReason("Invalid confidence value from AI service");
                        auditLogger.log(AuditEventType.AI_ANALYSIS_FAILED, run.getId(), "AI Analysis failed for document " + document.getId() + " due to invalid confidence");
                        return mapToResponse(runRepository.save(run));
                    }
                    AiAnalysisFinding finding = new AiAnalysisFinding();
                    finding.setRun(run);
                    finding.setFinding(f.label());
                    finding.setConfidence(f.confidence());
                    run.getFindings().add(finding);
                }
            }
            auditLogger.log(AuditEventType.AI_ANALYSIS_COMPLETED, run.getId(), "AI Analysis completed for document " + document.getId());
        } else {
            run.setStatus(AiAnalysisStatus.FAILED);
            run.setFailureReason(externalRes.errorDetail());
            run.setCompletedAt(OffsetDateTime.now());
            auditLogger.log(AuditEventType.AI_ANALYSIS_FAILED, run.getId(), "AI Analysis failed for document " + document.getId());
        }

        return mapToResponse(runRepository.save(run));
    }

    public List<AiAnalysisResponse> getAnalysesForApplication(UUID applicationId) {
        UUID orgId = TenantContext.getCurrentOrganizationId();
        List<AiAnalysisRun> runs = runRepository.findAllByApplicationIdAndOrganizationId(applicationId, orgId);
        return runs.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public AiAnalysisResponse getAnalysisById(UUID applicationId, UUID runId) {
        UUID orgId = TenantContext.getCurrentOrganizationId();
        AiAnalysisRun run = runRepository.findByIdAndApplicationIdAndOrganizationId(runId, applicationId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI Analysis Run not found"));
        return mapToResponse(run);
    }

    private AiAnalysisResponse mapToResponse(AiAnalysisRun run) {
        List<AiAnalysisResponse.AiFindingDto> findings = run.getFindings().stream()
                .map(f -> new AiAnalysisResponse.AiFindingDto(f.getFinding(), f.getConfidence()))
                .collect(Collectors.toList());

        return new AiAnalysisResponse(
                run.getId(),
                run.getApplicationId(),
                run.getDocumentId(),
                run.getStatus().name(),
                run.getRequestedAt(),
                run.getCompletedAt(),
                run.getModelName(),
                run.getModelVersion(),
                run.getFailureReason(),
                findings
        );
    }
}
