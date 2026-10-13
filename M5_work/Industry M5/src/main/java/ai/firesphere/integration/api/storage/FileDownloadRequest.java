package ai.firesphere.integration.api.storage;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload to retrieve an object from storage.
 */
public record FileDownloadRequest(
        @NotBlank(message = "File ID is required")
        String fileId
) {}
