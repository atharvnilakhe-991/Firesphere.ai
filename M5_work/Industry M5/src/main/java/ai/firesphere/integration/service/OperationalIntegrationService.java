package ai.firesphere.integration.service;

import ai.firesphere.contracts.integration.AIInspectionResultProviderContract;
import ai.firesphere.contracts.integration.ExternalCadIntegrationContract;
import ai.firesphere.integration.dto.AIFindingReviewRequest;

import java.util.UUID;

public interface OperationalIntegrationService {

    AIInspectionResultProviderContract.AIFindingIngestResult ingestAIFinding(AIInspectionResultProviderContract.AIFindingDTO finding);

    AIInspectionResultProviderContract.AIFindingDTO getAIFinding(UUID id);

    AIInspectionResultProviderContract.AIFindingDTO reviewAIFinding(UUID id, AIFindingReviewRequest request);

    AIInspectionResultProviderContract.AIJobStatusResponse submitAIJob(AIInspectionResultProviderContract.AIJobSubmissionRequest request);

    AIInspectionResultProviderContract.AIJobStatusResponse getAIJobStatus(String jobId);

    ExternalCadIntegrationContract.CadTransmissionReceipt transmitToCad(ExternalCadIntegrationContract.CadIncidentDTO incident);
}
