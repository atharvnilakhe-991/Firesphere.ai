package ai.firesphere.integration.api.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Request payload to upload an object to storage (e.g. evidence photo, inspection floor plan, compliance certificate).
 */
public record FileUploadRequest(
        @NotBlank(message = "File name is required")
        String fileName,

        @NotBlank(message = "Content type is required")
        String contentType,

        @NotNull(message = "File content is required")
        byte[] content,

        String category,

        String entityId,

        Map<String, String> metadata
) {
    public FileUploadRequest {
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
