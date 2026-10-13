package ai.firesphere.integration.api.storage;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload to delete an object from storage.
 */
public record FileDeleteRequest(
        @NotBlank(message = "File ID is required")
        String fileId
) {}
