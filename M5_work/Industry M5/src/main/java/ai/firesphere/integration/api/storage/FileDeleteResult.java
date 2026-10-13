package ai.firesphere.integration.api.storage;

/**
 * Result payload of a file delete operation.
 */
public record FileDeleteResult(
        String fileId,
        boolean deleted,
        String message
) {}
