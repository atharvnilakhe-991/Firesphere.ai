package ai.firesphere.integration.api.signature;

import java.time.Instant;

public record SignatureResult(
        String signatureToken,
        String documentReferenceId,
        String algorithmUsed,
        String certificateFingerprint,
        Instant signedAt
) {}
