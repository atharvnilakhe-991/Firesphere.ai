package ai.firesphere.notification.provider;

import java.time.Instant;

/**
 * Result returned by a notification channel provider delivery invocation.
 */
public record ProviderDeliveryResult(
        boolean success,
        String providerMessageId,
        String errorMessage,
        Instant dispatchedAt
) {
    public static ProviderDeliveryResult success(String providerMessageId) {
        return new ProviderDeliveryResult(true, providerMessageId, null, Instant.now());
    }

    public static ProviderDeliveryResult failure(String errorMessage) {
        return new ProviderDeliveryResult(false, null, errorMessage, Instant.now());
    }
}
