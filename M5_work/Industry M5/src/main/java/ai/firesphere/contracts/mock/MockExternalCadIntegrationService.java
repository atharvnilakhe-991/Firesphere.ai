package ai.firesphere.contracts.mock;

import ai.firesphere.contracts.integration.ExternalCadIntegrationContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Clean mock implementation for external 112/911 CAD integration.
 */
@Service
@ConditionalOnMissingBean(name = "productionExternalCadIntegrationService")
public class MockExternalCadIntegrationService implements ExternalCadIntegrationContract {

    private static final Logger log = LoggerFactory.getLogger(MockExternalCadIntegrationService.class);

    @Override
    public CadTransmissionReceipt transmitCadIncident(CadIncidentDTO incident) {
        String cadId = "CAD-EXT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[MOCK CAD] Transmitted incident {} to external agency CAD. Assigned external ID: {}",
                incident.internalIncidentId(), cadId);
        return new CadTransmissionReceipt(cadId, incident.internalIncidentId(), "TRANSMITTED", Instant.now());
    }

    @Override
    public Optional<CadIncidentStatusDTO> queryCadIncidentStatus(String externalCadIncidentId) {
        return Optional.of(new CadIncidentStatusDTO(
                externalCadIncidentId,
                "MUNICIPAL_DISPATCH_NORTH",
                "ACTIVE",
                Instant.now()
        ));
    }
}
