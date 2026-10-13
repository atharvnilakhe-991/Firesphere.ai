package ai.firesphere.integration.api.signature;

import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;

/**
 * Internal business contract for digital signature operations (PKI / e-Sign).
 */
public interface DigitalSignatureIntegration {

    IntegrationResponse<SignatureResult> signDocument(IntegrationRequest<SignatureRequest> request);

    IntegrationResponse<SignatureVerificationResult> verifySignature(IntegrationRequest<SignatureVerificationRequest> request);
}
