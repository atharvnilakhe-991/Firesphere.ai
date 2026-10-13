package ai.firesphere.integration;

import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.integration.adapter.storage.StorageIntegrationAdapter;
import ai.firesphere.integration.api.storage.FileDeleteRequest;
import ai.firesphere.integration.api.storage.FileDeleteResult;
import ai.firesphere.integration.api.storage.FileDownloadRequest;
import ai.firesphere.integration.api.storage.FileDownloadResult;
import ai.firesphere.integration.api.storage.FileUploadRequest;
import ai.firesphere.integration.api.storage.FileUploadResult;
import ai.firesphere.integration.core.configuration.IntegrationProperties;
import ai.firesphere.integration.core.idempotency.IdempotencyKey;
import ai.firesphere.integration.core.idempotency.InMemoryIdempotencyStore;
import ai.firesphere.integration.core.logging.IntegrationLogger;
import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;
import ai.firesphere.integration.core.retry.IntegrationRetryExecutor;
import ai.firesphere.integration.provider.storage.MockStorageProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectStorageIntegrationTest {

    private MockStorageProvider mockProvider;
    private StorageIntegrationAdapter adapter;
    private InMemoryIdempotencyStore idempotencyStore;
    private UUID testTenantId;

    @BeforeEach
    void setUp() {
        mockProvider = new MockStorageProvider();
        idempotencyStore = new InMemoryIdempotencyStore();
        IntegrationProperties properties = new IntegrationProperties();
        IntegrationRetryExecutor retryExecutor = new IntegrationRetryExecutor();
        IntegrationLogger logger = new IntegrationLogger();
        adapter = new StorageIntegrationAdapter(mockProvider, properties, retryExecutor, idempotencyStore, logger);

        testTenantId = UUID.randomUUID();
        TenantContext.setTenantId(testTenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        mockProvider.reset();
    }

    @Test
    @DisplayName("Upload file succeeds and returns storage metadata with sha256 checksum")
    void testUploadFileSuccess() {
        byte[] content = "Fire safety evacuation blueprint and floor plan content".getBytes(StandardCharsets.UTF_8);
        FileUploadRequest req = new FileUploadRequest(
                "floor_plan_level_1.pdf",
                "application/pdf",
                content,
                "FLOOR_PLAN",
                "BLD-101",
                Map.of("building", "Tower A", "floor", "1")
        );

        IntegrationRequest<FileUploadRequest> integrationReq = IntegrationRequest.of(
                "CORR-STOR-01",
                IdempotencyKey.of("IDEMP-STOR-UPLOAD-01"),
                req
        );

        IntegrationResponse<FileUploadResult> response = adapter.uploadFile(integrationReq);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.data()).isNotNull();
        assertThat(response.data().fileId()).startsWith("FILE-");
        assertThat(response.data().fileName()).isEqualTo("floor_plan_level_1.pdf");
        assertThat(response.data().contentType()).isEqualTo("application/pdf");
        assertThat(response.data().sizeBytes()).isEqualTo(content.length);
        assertThat(response.data().checksumSha256()).isNotBlank();
        assertThat(response.data().storageUrl()).contains("floor_plan_level_1.pdf");
    }

    @Test
    @DisplayName("Download file succeeds and returns byte content matching original upload")
    void testDownloadFileSuccess() {
        byte[] content = "Inspection evidence photo bytes".getBytes(StandardCharsets.UTF_8);
        FileUploadRequest uploadReq = new FileUploadRequest(
                "evidence_photo.jpg",
                "image/jpeg",
                content,
                "EVIDENCE",
                "INSP-202",
                Map.of()
        );

        IntegrationResponse<FileUploadResult> uploadResp = adapter.uploadFile(IntegrationRequest.of(uploadReq));
        assertThat(uploadResp.isSuccess()).isTrue();
        String fileId = uploadResp.data().fileId();

        IntegrationResponse<FileDownloadResult> downloadResp = adapter.downloadFile(
                IntegrationRequest.of(new FileDownloadRequest(fileId))
        );

        assertThat(downloadResp.isSuccess()).isTrue();
        assertThat(downloadResp.data()).isNotNull();
        assertThat(downloadResp.data().fileId()).isEqualTo(fileId);
        assertThat(downloadResp.data().content()).isEqualTo(content);
        assertThat(downloadResp.data().contentType()).isEqualTo("image/jpeg");
        assertThat(downloadResp.data().sizeBytes()).isEqualTo(content.length);
    }

    @Test
    @DisplayName("Delete file removes stored object and subsequent download fails")
    void testDeleteFileSuccess() {
        byte[] content = "Temporary certificate draft".getBytes(StandardCharsets.UTF_8);
        FileUploadRequest uploadReq = new FileUploadRequest(
                "draft.pdf",
                "application/pdf",
                content,
                "DRAFT",
                "NOC-303",
                Map.of()
        );

        IntegrationResponse<FileUploadResult> uploadResp = adapter.uploadFile(IntegrationRequest.of(uploadReq));
        String fileId = uploadResp.data().fileId();

        IntegrationResponse<FileDeleteResult> deleteResp = adapter.deleteFile(
                IntegrationRequest.of(new FileDeleteRequest(fileId))
        );
        assertThat(deleteResp.isSuccess()).isTrue();
        assertThat(deleteResp.data().deleted()).isTrue();

        IntegrationResponse<FileDownloadResult> downloadResp = adapter.downloadFile(
                IntegrationRequest.of(new FileDownloadRequest(fileId))
        );
        assertThat(downloadResp.isSuccess()).isFalse();
        assertThat(downloadResp.error().code()).isEqualTo("ERR_STORAGE_FILE_NOT_FOUND");
    }

    @Test
    @DisplayName("Upload exceeding maximum file size limit is rejected")
    void testUploadOversizedFileRejected() {
        mockProvider.setMaxFileSizeBytes(50); // limit to 50 bytes
        byte[] largeContent = new byte[100];

        FileUploadRequest uploadReq = new FileUploadRequest(
                "oversized.pdf",
                "application/pdf",
                largeContent,
                "DOCUMENT",
                "DOC-404",
                Map.of()
        );

        IntegrationResponse<FileUploadResult> response = adapter.uploadFile(IntegrationRequest.of(uploadReq));
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.error().code()).isEqualTo("ERR_STORAGE_PAYLOAD_TOO_LARGE");
    }

    @Test
    @DisplayName("Upload unsupported MIME type is rejected")
    void testUploadUnsupportedMimeTypeRejected() {
        byte[] scriptContent = "echo 'danger'".getBytes(StandardCharsets.UTF_8);
        FileUploadRequest uploadReq = new FileUploadRequest(
                "malicious.sh",
                "application/x-sh",
                scriptContent,
                "SCRIPT",
                "ATTACK-1",
                Map.of()
        );

        IntegrationResponse<FileUploadResult> response = adapter.uploadFile(IntegrationRequest.of(uploadReq));
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.error().code()).isEqualTo("ERR_STORAGE_UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    @DisplayName("Upload is idempotent when reusing same idempotency key")
    void testUploadIdempotency() {
        byte[] content = "Cert payload".getBytes(StandardCharsets.UTF_8);
        FileUploadRequest req = new FileUploadRequest("cert.pdf", "application/pdf", content, "CERT", "C-1", Map.of());
        IdempotencyKey key = IdempotencyKey.of("IDEMP-KEY-STOR-999");

        IntegrationResponse<FileUploadResult> first = adapter.uploadFile(IntegrationRequest.of("CORR-1", key, req));
        assertThat(first.isSuccess()).isTrue();
        int initialInvocations = mockProvider.getInvocationCount();

        IntegrationResponse<FileUploadResult> second = adapter.uploadFile(IntegrationRequest.of("CORR-2", key, req));
        assertThat(second.status()).isEqualTo(ai.firesphere.integration.core.model.IntegrationStatus.DUPLICATE);
        assertThat(second.data().fileId()).isEqualTo(first.data().fileId());
        assertThat(mockProvider.getInvocationCount()).isEqualTo(initialInvocations); // Provider was not invoked again
    }

    @Test
    @DisplayName("Upload recovers from transient retryable provider failures")
    void testUploadRetryRecovery() {
        mockProvider.failNextAttempts(2, "ERR_STORAGE_IO_TRANSIENT", "Network socket glitch", true);
        byte[] content = "Data to retry".getBytes(StandardCharsets.UTF_8);
        FileUploadRequest req = new FileUploadRequest("retry.txt", "text/plain", content, "LOG", "L-1", Map.of());

        IntegrationResponse<FileUploadResult> resp = adapter.uploadFile(IntegrationRequest.of(req));
        assertThat(resp.isSuccess()).isTrue();
        assertThat(mockProvider.getInvocationCount()).isEqualTo(3); // 2 failures + 1 success
    }

    @Test
    @DisplayName("Non-retryable provider failure stops immediately without retry")
    void testUploadNonRetryableFailure() {
        mockProvider.simulateFailure("ERR_STORAGE_ACCESS_DENIED", "Bucket permission revoked", false);
        byte[] content = "Data".getBytes(StandardCharsets.UTF_8);
        FileUploadRequest req = new FileUploadRequest("denied.txt", "text/plain", content, "LOG", "L-2", Map.of());

        IntegrationResponse<FileUploadResult> resp = adapter.uploadFile(IntegrationRequest.of(req));
        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.error().code()).isEqualTo("ERR_STORAGE_ACCESS_DENIED");
        assertThat(mockProvider.getInvocationCount()).isEqualTo(1); // No retries attempted
    }
}
