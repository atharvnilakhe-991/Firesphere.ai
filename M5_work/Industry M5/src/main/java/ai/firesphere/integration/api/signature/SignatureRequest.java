package ai.firesphere.integration.api.signature;

public record SignatureRequest(
        String documentReferenceId,
        String documentType, // FIRE_SAFETY_NOC, INSPECTION_CERTIFICATE, COMPLIANCE_ORDER
        byte[] documentHash,
        String signerIdentity,
        String designation,
        String signingReason
) {}
