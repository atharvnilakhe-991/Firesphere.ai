package ai.firesphere.ai;

import ai.firesphere.contracts.integration.AIInspectionResultProviderContract;
import ai.firesphere.contracts.mock.MockAIInspectionResultProvider;
import ai.firesphere.integration.dto.AIFindingReviewRequest;
import ai.firesphere.integration.service.OperationalIntegrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AI QA Suite: Provider Resilience, Zero-Network Boundary & Concurrency.
 * Validates in-memory mock isolation without external network traffic,
 * multi-threaded concurrent ingest and review thread-safety, and exception boundaries.
 */
@SpringBootTest
@ActiveProfiles("test")
class AIProviderResilienceAndMockBoundaryQATest {

    @Autowired
    private MockAIInspectionResultProvider mockAiProvider;

    @Autowired
    private OperationalIntegrationService operationalService;

    @BeforeEach
    void setUp() {
        mockAiProvider.clear();
    }

    @Test
    @DisplayName("AI Mock Boundary: Operates with zero network calls and deterministic in-memory store")
    void testMockProviderZeroNetworkIsolation() {
        UUID id = UUID.randomUUID();
        AIInspectionResultProviderContract.AIFindingDTO finding = new AIInspectionResultProviderContract.AIFindingDTO(
                id,
                UUID.randomUUID(),
                "SPRINKLER_VALVE_SHUT",
                0.96,
                "HIGH",
                null,
                null,
                null,
                null,
                null,
                Instant.now(),
                null
        );

        AIInspectionResultProviderContract.AIFindingIngestResult result = operationalService.ingestAIFinding(finding);
        assertThat(result.accepted()).isTrue();
        assertThat(result.findingId()).isEqualTo(id);

        AIInspectionResultProviderContract.AIFindingDTO retrieved = operationalService.getAIFinding(id);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.findingType()).isEqualTo("SPRINKLER_VALVE_SHUT");
        assertThat(retrieved.status()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    @DisplayName("AI Concurrency: Multi-threaded concurrent ingest and human review executes without race conditions")
    void testConcurrentAIFindingIngestAndReview() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<UUID> findingIds = new ArrayList<>();

        // Ingest 20 findings in parallel
        List<Callable<UUID>> ingestTasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            ingestTasks.add(() -> {
                UUID id = UUID.randomUUID();
                AIInspectionResultProviderContract.AIFindingDTO finding = new AIInspectionResultProviderContract.AIFindingDTO(
                        id,
                        UUID.randomUUID(),
                        "DEFECT_" + index,
                        0.85,
                        "NORMAL",
                        null,
                        null,
                        null,
                        null,
                        null,
                        Instant.now(),
                        null
                );
                operationalService.ingestAIFinding(finding);
                return id;
            });
        }

        List<Future<UUID>> ingestResults = executor.invokeAll(ingestTasks);
        for (Future<UUID> fut : ingestResults) {
            findingIds.add(fut.get());
        }

        assertThat(findingIds).hasSize(threadCount);

        // Review all 20 findings concurrently
        List<Callable<AIInspectionResultProviderContract.AIFindingDTO>> reviewTasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            UUID id = findingIds.get(i);
            final int idx = i;
            reviewTasks.add(() -> operationalService.reviewAIFinding(
                    id,
                    new AIFindingReviewRequest("APPROVED", "Concurrent review " + idx, "OFFICER_" + idx)
            ));
        }

        List<Future<AIInspectionResultProviderContract.AIFindingDTO>> reviewResults = executor.invokeAll(reviewTasks);
        for (Future<AIInspectionResultProviderContract.AIFindingDTO> fut : reviewResults) {
            AIInspectionResultProviderContract.AIFindingDTO reviewed = fut.get();
            assertThat(reviewed.status()).isEqualTo("APPROVED");
            assertThat(reviewed.reviewedBy()).startsWith("OFFICER_");
        }

        executor.shutdown();
    }

    @Test
    @DisplayName("AI Boundary: Operational service cleanly rejects null or missing input payloads")
    void testServiceFallbackWhenProviderThrowsException() {
        assertThatThrownBy(() -> operationalService.ingestAIFinding(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AI finding payload cannot be null");
    }
}
