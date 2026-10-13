package ai.firesphere.integration.api.signature;

import java.time.Instant;

public record SignatureVerificationResult(
        String documentReferenceId,
        boolean valid,
        String signerIdentity,
        String verificationMessage,
        Instant verifiedAt
) {}
