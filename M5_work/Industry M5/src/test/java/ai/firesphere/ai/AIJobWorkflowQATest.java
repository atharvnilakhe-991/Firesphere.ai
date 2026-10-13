package ai.firesphere.ai;

import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.contracts.integration.AIInspectionResultProviderContract;
import ai.firesphere.contracts.integration.AIInspectionResultProviderContract.*;
import ai.firesphere.contracts.mock.MockAIInspectionResultProvider;
import ai.firesphere.integration.dto.AIFindingReviewRequest;
import ai.firesphere.integration.service.OperationalIntegrationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AIJobWorkflowQATest {

    private MockAIInspectionResultProvider mockAiProvider;
    private OperationalIntegrationServiceImpl operationalService;

    @BeforeEach
    void setUp() {
        mockAiProvider = new MockAIInspectionResultProvider();
        operationalService = new OperationalIntegrationServiceImpl(mockAiProvider, null);
    }

    @Test
    @DisplayName("Submit AI evidence job executes and produces linked findings with explanation")
    void testSubmitAIJobSuccessWithExplanation() {
        UUID inspectionId = UUID.randomUUID();
        String evidenceFileId = "FILE-IMG-991";
        String evidenceUrl = "mock://storage/firesphere/FILE-IMG-991/exit_corridor.jpg";

        AIJobSubmissionRequest request = new AIJobSubmissionRequest(
                "JOB-AI-101",
                inspectionId,
                evidenceFileId,
                evidenceUrl,
                "BLOCKED_EXIT",
                Map.of("model", "yolov8-fire-hazards-v2")
        );

        AIJobStatusResponse response = operationalService.submitAIJob(request);

        assertThat(response).isNotNull();
        assertThat(response.jobId()).isEqualTo("JOB-AI-101");
        assertThat(response.inspectionId()).isEqualTo(inspectionId);
        assertThat(response.status()).isEqualTo(AIJobState.COMPLETED);
        assertThat(response.progressPercent()).isEqualTo(100);
        assertThat(response.explanation()).contains("Computer vision model detected");
        assertThat(response.generatedFindings()).hasSize(1);

        AIFindingDTO finding = response.generatedFindings().get(0);
        assertThat(finding.incidentOrBuildingId()).isEqualTo(inspectionId);
        assertThat(finding.findingType()).isEqualTo("BLOCKED_EXIT");
        assertThat(finding.confidence()).isGreaterThan(0.90);
        // Human-in-the-loop guardrail: finding MUST be PENDING_REVIEW upon ingestion
        assertThat(finding.status()).isEqualTo("PENDING_REVIEW");
        assertThat(finding.evidenceMediaUrl()).isEqualTo(evidenceUrl);
    }

    @Test
    @DisplayName("Polling AI job status retrieves persisted state and explanation")
    void testGetAIJobStatusPolling() {
        UUID inspectionId = UUID.randomUUID();
        AIJobSubmissionRequest request = new AIJobSubmissionRequest(
                "JOB-AI-102",
                inspectionId,
                "FILE-IMG-992",
                "mock://storage/evidence/fire_door.jpg",
                "BLOCKED_EXIT",
                Map.of()
        );

        operationalService.submitAIJob(request);

        AIJobStatusResponse fetched = operationalService.getAIJobStatus("JOB-AI-102");
        assertThat(fetched).isNotNull();
        assertThat(fetched.jobId()).isEqualTo("JOB-AI-102");
        assertThat(fetched.status()).isEqualTo(AIJobState.COMPLETED);
        assertThat(fetched.explanation()).isNotBlank();
    }

    @Test
    @DisplayName("Polling non-existent AI job ID throws ResourceNotFoundException")
    void testGetNonExistentJobThrowsNotFound() {
        assertThatThrownBy(() -> operationalService.getAIJobStatus("JOB-NON-EXISTENT"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("AI Job not found");
    }

    @Test
    @DisplayName("Human officer review approves AI finding and preserves audit trail")
    void testHumanOfficerReviewApproval() {
        UUID inspectionId = UUID.randomUUID();
        AIJobSubmissionRequest request = new AIJobSubmissionRequest(
                "JOB-AI-103",
                inspectionId,
                "FILE-IMG-993",
                "mock://storage/evidence/blocked_door.jpg",
                "BLOCKED_EXIT",
                Map.of()
        );

        AIJobStatusResponse jobResp = operationalService.submitAIJob(request);
        UUID findingId = jobResp.generatedFindings().get(0).findingId();

        // Officer reviews finding and approves violation
        AIFindingReviewRequest reviewReq = new AIFindingReviewRequest(
                "APPROVED",
                "Confirmed exit obstructed by storage boxes during on-site visit",
                "OFFICER-7712"
        );

        AIFindingDTO reviewed = operationalService.reviewAIFinding(findingId, reviewReq);

        assertThat(reviewed.status()).isEqualTo("APPROVED");
        assertThat(reviewed.reviewerNotes()).isEqualTo("Confirmed exit obstructed by storage boxes during on-site visit");
        assertThat(reviewed.reviewedBy()).isEqualTo("OFFICER-7712");
        assertThat(reviewed.reviewedAt()).isNotNull();
    }

    @Test
    @DisplayName("Human officer review dismisses false positive AI finding")
    void testHumanOfficerReviewDismissal() {
        UUID inspectionId = UUID.randomUUID();
        AIJobSubmissionRequest request = new AIJobSubmissionRequest(
                "JOB-AI-104",
                inspectionId,
                "FILE-IMG-994",
                "mock://storage/evidence/shadow.jpg",
                "THERMAL_HOTSPOT",
                Map.of()
        );

        AIJobStatusResponse jobResp = operationalService.submitAIJob(request);
        UUID findingId = jobResp.generatedFindings().get(0).findingId();

        AIFindingReviewRequest reviewReq = new AIFindingReviewRequest(
                "DISMISSED",
                "Shadow artifact caused false thermal detection; area confirmed clear",
                "OFFICER-8801"
        );

        AIFindingDTO reviewed = operationalService.reviewAIFinding(findingId, reviewReq);

        assertThat(reviewed.status()).isEqualTo("DISMISSED");
        assertThat(reviewed.reviewedBy()).isEqualTo("OFFICER-8801");
    }

    @Test
    @DisplayName("Simulated AI model failure transitions job to FAILED with error message")
    void testSimulatedAIFailureState() {
        mockAiProvider.setSimulateJobFailure(true, "GPU Out of Memory on tensor core 3");

        UUID inspectionId = UUID.randomUUID();
        AIJobSubmissionRequest request = new AIJobSubmissionRequest(
                "JOB-AI-FAIL-01",
                inspectionId,
                "FILE-IMG-995",
                "mock://storage/evidence/huge.jpg",
                "PANORAMA_SCAN",
                Map.of()
        );

        AIJobStatusResponse failedJob = operationalService.submitAIJob(request);

        assertThat(failedJob.status()).isEqualTo(AIJobState.FAILED);
        assertThat(failedJob.errorMessage()).contains("GPU Out of Memory");
        assertThat(failedJob.generatedFindings()).isEmpty();
    }

    @Test
    @DisplayName("Job submission with null inspectionId is rejected")
    void testSubmitJobMissingInspectionIdRejected() {
        AIJobSubmissionRequest invalidReq = new AIJobSubmissionRequest(
                "JOB-ERR",
                null,
                "FILE-1",
                "url",
                "TYPE",
                Map.of()
        );

        assertThatThrownBy(() -> operationalService.submitAIJob(invalidReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inspectionId is mandatory");
    }
}
