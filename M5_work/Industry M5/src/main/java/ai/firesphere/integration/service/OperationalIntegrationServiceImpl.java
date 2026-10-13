package ai.firesphere.integration.service;

import ai.firesphere.contracts.integration.AIInspectionResultProviderContract;
import ai.firesphere.contracts.integration.ExternalCadIntegrationContract;
import ai.firesphere.integration.dto.AIFindingReviewRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Production-ready operational integration service bridging AI computer vision inspection findings
 * and external CAD emergency integrations.
 */
@Service
public class OperationalIntegrationServiceImpl implements OperationalIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(OperationalIntegrationServiceImpl.class);

    private final AIInspectionResultProviderContract aiInspectionProvider;
    private final ExternalCadIntegrationContract externalCadProvider;

    public OperationalIntegrationServiceImpl(
            AIInspectionResultProviderContract aiInspectionProvider,
            ExternalCadIntegrationContract externalCadProvider
    ) {
        this.aiInspectionProvider = aiInspectionProvider;
        this.externalCadProvider = externalCadProvider;
    }

    @Override
    public AIInspectionResultProviderContract.AIFindingIngestResult ingestAIFinding(
            AIInspectionResultProviderContract.AIFindingDTO finding
    ) {
        if (finding == null) {
            throw new IllegalArgumentException("AI finding payload cannot be null");
        }
        log.info("[INTEGRATION OPS] Ingesting AI inspection finding of type: {}", finding.findingType());
        return aiInspectionProvider.ingestAIFinding(finding);
    }

    @Override
    public AIInspectionResultProviderContract.AIFindingDTO getAIFinding(UUID id) {
        log.debug("[INTEGRATION OPS] Fetching AI inspection finding: {}", id);
        return aiInspectionProvider.getFindingById(id)
                .orElseThrow(() -> new ai.firesphere.common.exception.ResourceNotFoundException("AI Inspection finding not found for id: " + id));
    }

    @Override
    public AIInspectionResultProviderContract.AIFindingDTO reviewAIFinding(UUID id, AIFindingReviewRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("AIFindingReviewRequest must not be null");
        }
        log.info("[INTEGRATION OPS] Officer {} reviewing AI finding {} with decision: {}",
                request.reviewerBadge(), id, request.decision());
        return aiInspectionProvider.reviewFinding(
                id,
                request.decision(),
                request.reviewerNotes(),
                request.reviewerBadge()
        );
    }

    @Override
    public AIInspectionResultProviderContract.AIJobStatusResponse submitAIJob(
            AIInspectionResultProviderContract.AIJobSubmissionRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("AI job submission request must not be null");
        }
        log.info("[INTEGRATION OPS] Submitting AI analysis job for inspection: {}", request.inspectionId());
        return aiInspectionProvider.submitEvidenceForAnalysis(request);
    }

    @Override
    public AIInspectionResultProviderContract.AIJobStatusResponse getAIJobStatus(String jobId) {
        log.debug("[INTEGRATION OPS] Fetching AI job status: {}", jobId);
        return aiInspectionProvider.getJobStatus(jobId)
                .orElseThrow(() -> new ai.firesphere.common.exception.ResourceNotFoundException("AI Job not found: " + jobId));
    }

    @Override
    public ExternalCadIntegrationContract.CadTransmissionReceipt transmitToCad(
            ExternalCadIntegrationContract.CadIncidentDTO incident
    ) {
        log.info("[INTEGRATION OPS] Transmitting incident {} to external CAD system", incident.internalIncidentId());
        return externalCadProvider.transmitCadIncident(incident);
    }
}
