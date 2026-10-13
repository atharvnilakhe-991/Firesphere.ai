package ai.firesphere.integration.provider.signature;

import ai.firesphere.integration.api.signature.SignatureRequest;
import ai.firesphere.integration.api.signature.SignatureResult;
import ai.firesphere.integration.api.signature.SignatureVerificationRequest;
import ai.firesphere.integration.api.signature.SignatureVerificationResult;

/**
 * Provider SPI interface for Digital Signature / PKI engines (e.g. eMudhra / DSC mock).
 */
public interface DigitalSignatureProvider {

    String getProviderName();

    SignatureResult sign(SignatureRequest request);

    SignatureVerificationResult verify(SignatureVerificationRequest request);
}
