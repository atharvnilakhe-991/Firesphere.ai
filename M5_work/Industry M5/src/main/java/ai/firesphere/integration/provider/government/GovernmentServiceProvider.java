package ai.firesphere.integration.provider.government;

import ai.firesphere.integration.api.government.CadStatusQueryRequest;
import ai.firesphere.integration.api.government.CadStatusQueryResult;
import ai.firesphere.integration.api.government.CadTransmitRequest;
import ai.firesphere.integration.api.government.CadTransmitResult;
import ai.firesphere.integration.api.government.ComplianceLookupRequest;
import ai.firesphere.integration.api.government.ComplianceLookupResult;

/**
 * Provider SPI interface for external Government and Municipal Emergency CAD (112/911) services.
 */
public interface GovernmentServiceProvider {

    String getProviderName();

    CadTransmitResult transmitCad(CadTransmitRequest request);

    CadStatusQueryResult queryStatus(CadStatusQueryRequest request);

    ComplianceLookupResult lookupCompliance(ComplianceLookupRequest request);
}
