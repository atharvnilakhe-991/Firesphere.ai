package ai.firesphere.integration.api.storage;

/**
 * Result payload containing downloaded file data.
 */
public record FileDownloadResult(
        String fileId,
        String fileName,
        String contentType,
        long sizeBytes,
        byte[] content,
        String checksumSha256
) {}
