package ai.firesphere.integration.api.storage;

import java.time.Instant;

/**
 * Result of an object storage upload.
 */
public record FileUploadResult(
        String fileId,
        String fileName,
        String contentType,
        long sizeBytes,
        String storageUrl,
        String checksumSha256,
        Instant uploadedAt
) {}
