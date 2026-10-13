package com.firesphere.backend.ai.service;

import com.firesphere.backend.ai.client.AiAnalysisClient;
import com.firesphere.backend.ai.dto.AiAnalysisRequest;
import com.firesphere.backend.ai.dto.AiAnalysisResponse;
import com.firesphere.backend.ai.dto.ExternalAiRequest;
import com.firesphere.backend.ai.dto.ExternalAiResponse;
import com.firesphere.backend.ai.dto.ReviewFindingRequest;
import com.firesphere.backend.ai.entity.AiAnalysisFinding;
import com.firesphere.backend.ai.entity.AiAnalysisRun;
import com.firesphere.backend.ai.entity.AiAnalysisStatus;
import com.firesphere.backend.ai.entity.AiOfficerReview;
import com.firesphere.backend.ai.repository.AiAnalysisRunRepository;
import com.firesphere.backend.ai.repository.AiOfficerReviewRepository;
import com.firesphere.backend.common.security.Role;
import com.firesphere.backend.common.security.TenantContext;
import com.firesphere.backend.document.entity.DocumentStatus;
import com.firesphere.backend.document.entity.NocDocument;
import com.firesphere.backend.document.repository.NocDocumentRepository;
import com.firesphere.backend.inspection.entity.NocInspection;
import com.firesphere.backend.inspection.entity.NocInspectionEvidence;
import com.firesphere.backend.inspection.repository.NocInspectionEvidenceRepository;
import com.firesphere.backend.inspection.repository.NocInspectionRepository;
import com.firesphere.backend.inspection.service.NocInspectionEvidenceService;
import com.firesphere.backend.noc.entity.NocApplication;
import com.firesphere.backend.noc.repository.NocApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AiOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AiOrchestrationService.class);

    private final AiAnalysisRunRepository runRepository;
    private final NocApplicationRepository applicationRepository;
    private final NocDocumentRepository documentRepository;
    private final NocInspectionRepository inspectionRepository;
    private final NocInspectionEvidenceRepository evidenceRepository;
    private final NocInspectionEvidenceService evidenceService;
    private final AiAnalysisClient aiClient;
    private final AiPersistenceHelper persistenceHelper;
    private final AiOfficerReviewRepository officerReviewRepository;

    public AiOrchestrationService(
            AiAnalysisRunRepository runRepository,
            NocApplicationRepository applicationRepository,
            NocDocumentRepository documentRepository,
            NocInspectionRepository inspectionRepository,
            NocInspectionEvidenceRepository evidenceRepository,
            NocInspectionEvidenceService evidenceService,
            AiAnalysisClient aiClient,
            AiPersistenceHelper persistenceHelper,
            AiOfficerReviewRepository officerReviewRepository) {
        this.runRepository = runRepository;
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.inspectionRepository = inspectionRepository;
        this.evidenceRepository = evidenceRepository;
        this.evidenceService = evidenceService;
        this.aiClient = aiClient;
        this.persistenceHelper = persistenceHelper;
        this.officerReviewRepository = officerReviewRepository;
    }

    // NON-TRANSACTIONAL entry point: Network I/O occurs outside any DB transaction
    public AiAnalysisResponse requestAnalysis(UUID applicationId, AiAnalysisRequest request) {
        UUID orgId = TenantContext.getCurrentOrganizationId();
        Long internalOrgId = TenantContext.getCurrentInternalOrgId();
        Long internalUserId = TenantContext.getCurrentInternalUserId();
        if (orgId == null && internalOrgId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tenant context missing");
        }

        NocApplication noc = applicationRepository.findByUuid(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (internalOrgId != null && noc.getOrgId() != null && !noc.getOrgId().equals(internalOrgId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant access prohibited");
        }

        if (noc.getApplicantId() != null && !noc.getApplicantId().equals(TenantContext.getCurrentUserId())
                && !TenantContext.hasRole("ADMIN")
                && !TenantContext.hasRole("OFFICER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not authorized to request AI analysis for this application");
        }

        if (request.documentId() != null) {
            Optional<NocDocument> docOpt = documentRepository.findById(request.documentId());
            if (docOpt.isPresent()) {
                NocDocument doc = docOpt.get();
                if (doc.getStatus() != DocumentStatus.UPLOADED && doc.getStatus() != DocumentStatus.VERIFIED) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document must be UPLOADED or VERIFIED to be analyzed");
                }
            }
        }

        // Resolve inspection record
        UUID inspectionUuid = request.inspectionId();
        NocInspection inspection = null;
        if (inspectionUuid != null) {
            inspection = inspectionRepository.findByUuid(inspectionUuid).orElse(null);
        }
        if (inspection == null) {
            List<NocInspection> inspections = inspectionRepository.findAllByNocApplicationIdAndOrgId(noc.getId(), noc.getOrgId());
            if (inspections.isEmpty()) {
                inspections = inspectionRepository.findAllByNocApplicationId(noc.getId());
            }
            if (inspections.isEmpty() && noc.getOrgId() != null) {
                inspections = inspectionRepository.findAllByOrgId(noc.getOrgId());
            }
            if (!inspections.isEmpty()) {
                inspection = inspections.get(inspections.size() - 1);
                inspectionUuid = inspection.getUuid();
            }
        }

        if (inspection == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "AI Inspection Analysis requires a scheduled inspection record for this application.");
        }

        boolean isTestMode = Boolean.TRUE.equals(request.testMode());
        if (!isTestMode) {
            Optional<AiAnalysisRun> existingRun = runRepository.findByInspectionIdAndStatus(inspection.getId(), AiAnalysisStatus.COMPLETED);
            if (existingRun.isPresent() && !existingRun.get().getFindings().isEmpty()) {
                return mapToResponse(existingRun.get());
            }
        }

        Optional<AiAnalysisRun> processingRun = runRepository.findByInspectionIdAndStatus(inspection.getId(), AiAnalysisStatus.PROCESSING);
        if (processingRun.isPresent()) {
            return mapToResponse(processingRun.get());
        }

        // Resolve real inspection evidence UUIDs
        List<UUID> evidenceIds = request.evidenceIds();
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            // Automatically resolve from persisted inspection evidence
            evidenceIds = evidenceService.resolveEvidenceUuidsForInspection(inspection.getUuid(), orgId);
        }

        if (evidenceIds == null || evidenceIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "AI Inspection Analysis requires non-empty evidenceIds. No active inspection evidence was found for this inspection.");
        }

        // Build evidence UUID to internal Long ID mapping for database foreign key persistence
        List<NocInspectionEvidence> persistedEvList = evidenceRepository.findAllByInspectionId(inspection.getId());
        Map<UUID, Long> evidenceMap = new HashMap<>();
        for (NocInspectionEvidence ev : persistedEvList) {
            if (ev.getUuid() != null && ev.getId() != null) {
                evidenceMap.put(ev.getUuid(), ev.getId());
            }
        }
        for (UUID evUuid : evidenceIds) {
            if (!evidenceMap.containsKey(evUuid)) {
                evidenceRepository.findByUuid(evUuid).ifPresent(e -> evidenceMap.put(e.getUuid(), e.getId()));
            }
        }

        // -------------------------------------------------------------
        // PHASE 1: TRANSACTION A (Committed immediately)
        // -------------------------------------------------------------
        AiAnalysisRun run = persistenceHelper.createProcessingRun(
                inspection.getOrgId() != null ? inspection.getOrgId() : (internalOrgId != null ? internalOrgId : 1L),
                inspection.getId(),
                inspection.getUuid(),
                applicationId,
                request.documentId(),
                internalUserId
        );

        // -------------------------------------------------------------
        // PHASE 2: NON-TRANSACTIONAL NETWORK I/O (Outbound call to M4)
        // -------------------------------------------------------------
        ExternalAiRequest externalReq = new ExternalAiRequest(
                inspection.getUuid(),
                evidenceIds,
                isTestMode,
                noc.getBuildingName() != null ? noc.getBuildingName() : "UNKNOWN",
                "UNAVAILABLE",
                orgId != null ? orgId.toString() : "00000000-0000-0000-0000-000000000001"
        );

        ExternalAiResponse externalRes;
        try {
            externalRes = aiClient.analyze(externalReq);
        } catch (Exception e) {
            log.error("Outbound AI service call failed for run [{}]: {}", run.getUuid(), e.getMessage());
            // -------------------------------------------------------------
            // PHASE 3: TRANSACTION B (Persist Failure durably)
            // -------------------------------------------------------------
            AiAnalysisRun failedRun = persistenceHelper.recordRunFailure(run.getUuid(), "AI Service connection error: " + e.getMessage());
            return mapToResponse(failedRun);
        }

        // -------------------------------------------------------------
        // PHASE 3: TRANSACTION B (Persist Result)
        // -------------------------------------------------------------
        boolean isCompleted = "COMPLETED".equalsIgnoreCase(externalRes.status()) || "SUCCESS".equalsIgnoreCase(externalRes.status());
        if (isCompleted) {
            AiAnalysisRun successRun = persistenceHelper.recordRunSuccess(run.getUuid(), externalRes, evidenceMap);
            return mapToResponse(successRun);
        } else {
            String failureReason = externalRes.errorDetail() != null ? externalRes.errorDetail() : "AI service returned failure";
            AiAnalysisRun failedRun = persistenceHelper.recordRunFailure(run.getUuid(), failureReason);
            return mapToResponse(failedRun);
        }
    }

    public AiOfficerReview reviewFinding(UUID applicationId, UUID runId, UUID findingId, ReviewFindingRequest request) {
        UUID orgId = TenantContext.getCurrentOrganizationId();
        Long internalOrgId = TenantContext.getCurrentInternalOrgId();
        Long internalUserId = TenantContext.getCurrentInternalUserId();
        if (orgId == null && internalOrgId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tenant context missing");
        }

        if (!TenantContext.hasRole(Role.OFFICER.name()) && !TenantContext.hasRole(Role.ADMIN.name())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only certified officers can verify AI findings");
        }

        // Validate application exists and tenant matches
        NocApplication noc = applicationRepository.findByUuid(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (internalOrgId != null && noc.getOrgId() != null && !noc.getOrgId().equals(internalOrgId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant access prohibited");
        }

        // Ensure run belongs to application and tenant
        AiAnalysisRun run = runRepository.findByUuid(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI Analysis Run not found"));

        if (internalOrgId != null && run.getOrgId() != null && !run.getOrgId().equals(internalOrgId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant access prohibited");
        }

        // Validate run belongs to this application
        NocInspection runInspection = inspectionRepository.findById(run.getInspectionId()).orElse(null);
        boolean runLinked = runInspection != null && (
                (runInspection.getNocApplicationId() != null && runInspection.getNocApplicationId().equals(noc.getId())) ||
                (runInspection.getApplicationId() != null && runInspection.getApplicationId().equals(noc.getUuid()))
        );
        if (!runLinked) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI Analysis Run does not belong to specified application");
        }

        return persistenceHelper.recordOfficerReview(findingId, run.getId(), internalOrgId, internalUserId, request);
    }

    public List<AiAnalysisResponse> getAnalysesForApplication(UUID applicationId) {
        UUID orgId = TenantContext.getCurrentOrganizationId();
        Long internalOrgId = TenantContext.getCurrentInternalOrgId();
        if (orgId == null && internalOrgId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tenant context missing");
        }

        NocApplication noc = applicationRepository.findByUuid(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (internalOrgId != null && noc.getOrgId() != null && !noc.getOrgId().equals(internalOrgId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant access prohibited");
        }

        List<NocInspection> inspections = inspectionRepository.findAllByNocApplicationId(noc.getId());
        if (inspections.isEmpty()) {
            return List.of();
        }
        java.util.Map<UUID, AiAnalysisRun> runMap = new java.util.LinkedHashMap<>();
        for (NocInspection insp : inspections) {
            List<AiAnalysisRun> runs = runRepository.findAllByInspectionId(insp.getId());
            for (AiAnalysisRun r : runs) {
                if (r.getUuid() != null) {
                    runMap.put(r.getUuid(), r);
                }
            }
        }
        return runMap.values().stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public AiAnalysisResponse getAnalysisById(UUID applicationId, UUID runId) {
        UUID orgId = TenantContext.getCurrentOrganizationId();
        Long internalOrgId = TenantContext.getCurrentInternalOrgId();
        if (orgId == null && internalOrgId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tenant context missing");
        }

        NocApplication noc = applicationRepository.findByUuid(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (internalOrgId != null && noc.getOrgId() != null && !noc.getOrgId().equals(internalOrgId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant access prohibited");
        }

        AiAnalysisRun run = runRepository.findByUuid(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI Analysis Run not found"));

        if (internalOrgId != null && run.getOrgId() != null && !run.getOrgId().equals(internalOrgId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant access prohibited");
        }

        // Validate run belongs to this application
        NocInspection runInspection = inspectionRepository.findById(run.getInspectionId()).orElse(null);
        boolean runLinked = runInspection != null && (
                (runInspection.getNocApplicationId() != null && runInspection.getNocApplicationId().equals(noc.getId())) ||
                (runInspection.getApplicationId() != null && runInspection.getApplicationId().equals(noc.getUuid()))
        );
        if (!runLinked) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI Analysis Run does not belong to specified application");
        }

        return mapToResponse(run);
    }

    private AiAnalysisResponse mapToResponse(AiAnalysisRun run) {
        List<AiAnalysisResponse.AiFindingDto> findings = run.getFindings().stream()
                .map(f -> {
                    Optional<AiOfficerReview> latestReview = f.getId() != null
                            ? officerReviewRepository.findTopByFindingIdOrderByReviewedAtDesc(f.getId())
                            : Optional.empty();
                    String notes = latestReview.map(AiOfficerReview::getOfficerNotes).orElse(null);
                    Boolean hazardVerified = latestReview.map(AiOfficerReview::getHazardVerified).orElse(false);

                    return new AiAnalysisResponse.AiFindingDto(
                            f.getUuid(),
                            f.getEvidenceUuid(),
                            f.getFinding(),
                            f.getConfidence(),
                            f.getSeverity(),
                            f.getReviewStatus(),
                            f.getIsAdvisory(),
                            f.getFindingType(),
                            f.getDescription(),
                            f.getVerificationGuidance(),
                            f.getBoundingBoxJson(),
                            "CV_OBJECT_DETECTION",
                            "SINGLE_PROVIDER",
                            notes,
                            hazardVerified
                    );
                })
                .collect(Collectors.toList());

        return new AiAnalysisResponse(
                run.getUuid(),
                run.getApplicationId(),
                run.getDocumentId(),
                run.getStatus() != null ? run.getStatus().name() : "UNKNOWN",
                run.getRequestedAt(),
                run.getCompletedAt(),
                run.getModelName(),
                run.getModelVersion(),
                run.getFailureReason(),
                findings,
                run.getInspectionUuid(),
                "NOT_APPROVED_GATED",
                "UNAVAILABLE",
                "SINGLE_PROVIDER",
                "NOT_APPROVED",
                "NOT_CALIBRATED"
        );
    }
}
