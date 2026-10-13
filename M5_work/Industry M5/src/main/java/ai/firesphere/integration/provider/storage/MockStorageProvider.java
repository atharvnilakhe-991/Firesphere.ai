package ai.firesphere.integration.provider.storage;

import ai.firesphere.integration.api.storage.FileDeleteRequest;
import ai.firesphere.integration.api.storage.FileDeleteResult;
import ai.firesphere.integration.api.storage.FileDownloadRequest;
import ai.firesphere.integration.api.storage.FileDownloadResult;
import ai.firesphere.integration.api.storage.FileUploadRequest;
import ai.firesphere.integration.api.storage.FileUploadResult;
import ai.firesphere.integration.core.exception.IntegrationInvalidResponseException;
import ai.firesphere.integration.core.exception.IntegrationProviderException;
import ai.firesphere.integration.core.exception.IntegrationTimeoutException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe deterministic in-memory mock storage provider.
 * Enforces size limits, content type filtering, and provides testing failure simulation hooks.
 */
@Component("integrationMockStorageProvider")
public class MockStorageProvider implements StorageProvider {

    public static final long DEFAULT_MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/pdf",
            "application/json",
            "text/plain"
    );

    private final Map<String, StoredFileRecord> storage = new ConcurrentHashMap<>();
    private final AtomicInteger invocationCount = new AtomicInteger(0);

    private volatile long maxFileSizeBytes = DEFAULT_MAX_FILE_SIZE_BYTES;
    private volatile boolean simulateTimeout = false;
    private volatile long timeoutDelayMs = 0;
    private volatile boolean simulateInvalidResponse = false;
    private volatile String invalidResponseMessage = "Simulated invalid storage response";
    private volatile boolean simulateFailure = false;
    private volatile String failureCode = "ERR_STORAGE_IO_FAILURE";
    private volatile String failureMessage = "Object storage service I/O error";
    private volatile boolean failureRetryable = false;
    private final AtomicInteger remainingFailures = new AtomicInteger(0);

    public record StoredFileRecord(
            String fileId,
            String fileName,
            String contentType,
            long sizeBytes,
            byte[] content,
            String checksumSha256,
            Instant uploadedAt,
            Map<String, String> metadata
    ) {}

    @Override
    public String getProviderName() {
        return "MOCK_OBJECT_STORAGE";
    }

    @Override
    public FileUploadResult upload(FileUploadRequest request) {
        checkSimulation();

        if (request == null || request.content() == null) {
            throw new IntegrationProviderException(getProviderName(), "ERR_STORAGE_NULL_PAYLOAD", "Upload payload cannot be null", false);
        }

        if (request.content().length > maxFileSizeBytes) {
            throw new IntegrationProviderException(
                    getProviderName(),
                    "ERR_STORAGE_PAYLOAD_TOO_LARGE",
                    "File size " + request.content().length + " bytes exceeds limit of " + maxFileSizeBytes + " bytes",
                    false
            );
        }

        if (request.contentType() != null && !ALLOWED_CONTENT_TYPES.contains(request.contentType().toLowerCase())) {
            throw new IntegrationProviderException(
                    getProviderName(),
                    "ERR_STORAGE_UNSUPPORTED_MEDIA_TYPE",
                    "Content type " + request.contentType() + " is not supported. Allowed: " + ALLOWED_CONTENT_TYPES,
                    false
            );
        }

        String fileId = "FILE-" + UUID.randomUUID();
        String checksum = calculateSha256(request.content());
        Instant now = Instant.now();
        String storageUrl = "mock://storage/firesphere/" + fileId + "/" + request.fileName();

        StoredFileRecord record = new StoredFileRecord(
                fileId,
                request.fileName(),
                request.contentType(),
                request.content().length,
                request.content().clone(),
                checksum,
                now,
                request.metadata() != null ? Map.copyOf(request.metadata()) : Map.of()
        );

        storage.put(fileId, record);

        return new FileUploadResult(
                fileId,
                request.fileName(),
                request.contentType(),
                request.content().length,
                storageUrl,
                checksum,
                now
        );
    }

    @Override
    public FileDownloadResult download(FileDownloadRequest request) {
        checkSimulation();

        if (request == null || request.fileId() == null || request.fileId().isBlank()) {
            throw new IntegrationProviderException(getProviderName(), "ERR_STORAGE_INVALID_FILE_ID", "File ID is required", false);
        }

        StoredFileRecord record = storage.get(request.fileId());
        if (record == null) {
            throw new IntegrationProviderException(getProviderName(), "ERR_STORAGE_FILE_NOT_FOUND", "File not found: " + request.fileId(), false);
        }

        return new FileDownloadResult(
                record.fileId(),
                record.fileName(),
                record.contentType(),
                record.sizeBytes(),
                record.content().clone(),
                record.checksumSha256()
        );
    }

    @Override
    public FileDeleteResult delete(FileDeleteRequest request) {
        checkSimulation();

        if (request == null || request.fileId() == null || request.fileId().isBlank()) {
            throw new IntegrationProviderException(getProviderName(), "ERR_STORAGE_INVALID_FILE_ID", "File ID is required", false);
        }

        StoredFileRecord removed = storage.remove(request.fileId());
        if (removed == null) {
            return new FileDeleteResult(request.fileId(), false, "File did not exist or was already deleted");
        }

        return new FileDeleteResult(request.fileId(), true, "File successfully deleted from object storage");
    }

    private String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return "sha256-mock-" + data.length;
        }
    }

    private void checkSimulation() {
        invocationCount.incrementAndGet();

        if (simulateTimeout) {
            if (timeoutDelayMs > 0) {
                try {
                    Thread.sleep(timeoutDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            throw new IntegrationTimeoutException(getProviderName(), timeoutDelayMs);
        }

        if (simulateInvalidResponse) {
            throw new IntegrationInvalidResponseException(getProviderName(), invalidResponseMessage);
        }

        if (remainingFailures.get() > 0) {
            remainingFailures.decrementAndGet();
            throw new IntegrationProviderException(getProviderName(), failureCode, failureMessage, failureRetryable);
        }

        if (simulateFailure) {
            throw new IntegrationProviderException(getProviderName(), failureCode, failureMessage, failureRetryable);
        }
    }

    public void simulateSuccess() {
        this.simulateTimeout = false;
        this.simulateInvalidResponse = false;
        this.simulateFailure = false;
        this.remainingFailures.set(0);
    }

    public void simulateFailure(String code, String message, boolean retryable) {
        this.simulateFailure = true;
        this.failureCode = code;
        this.failureMessage = message;
        this.failureRetryable = retryable;
    }

    public void simulateTimeout(long delayMs) {
        this.simulateTimeout = true;
        this.timeoutDelayMs = delayMs;
    }

    public void simulateInvalidResponse(String reason) {
        this.simulateInvalidResponse = true;
        this.invalidResponseMessage = reason;
    }

    public void failNextAttempts(int count, String code, String message, boolean retryable) {
        this.remainingFailures.set(count);
        this.failureCode = code;
        this.failureMessage = message;
        this.failureRetryable = retryable;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public int getInvocationCount() {
        return invocationCount.get();
    }

    public int getStoredCount() {
        return storage.size();
    }

    public void reset() {
        simulateSuccess();
        this.invocationCount.set(0);
        this.maxFileSizeBytes = DEFAULT_MAX_FILE_SIZE_BYTES;
        this.storage.clear();
    }
}
