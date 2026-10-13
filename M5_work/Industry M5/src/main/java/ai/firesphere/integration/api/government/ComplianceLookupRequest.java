package ai.firesphere.integration.api.government;

public record ComplianceLookupRequest(
        String buildingTaxId,
        String jurisdictionCode,
        String occupancyCategory
) {}
