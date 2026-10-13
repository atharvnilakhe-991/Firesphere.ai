package ai.firesphere.integration.api.signature;

public record SignatureVerificationRequest(
        String documentReferenceId,
        byte[] documentHash,
        String signatureToken
) {}
