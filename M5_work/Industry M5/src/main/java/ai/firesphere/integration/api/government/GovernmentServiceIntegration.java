package ai.firesphere.integration.api.government;

import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;

/**
 * Internal business contract for Government Services and Emergency CAD (112/911) integrations.
 */
public interface GovernmentServiceIntegration {

    IntegrationResponse<CadTransmitResult> transmitCadIncident(IntegrationRequest<CadTransmitRequest> request);

    IntegrationResponse<CadStatusQueryResult> queryCadStatus(IntegrationRequest<CadStatusQueryRequest> request);

    IntegrationResponse<ComplianceLookupResult> verifyCompliance(IntegrationRequest<ComplianceLookupRequest> request);
}
