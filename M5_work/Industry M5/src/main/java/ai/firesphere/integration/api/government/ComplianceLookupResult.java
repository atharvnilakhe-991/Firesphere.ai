package ai.firesphere.integration.api.government;

import java.time.Instant;
import java.util.List;

public record ComplianceLookupResult(
        String buildingTaxId,
        boolean compliant,
        String nocStatus, // ACTIVE, EXPIRED, REVOKED, NOT_FOUND
        String permitNumber,
        Instant expiryDate,
        List<String> outstandingViolations
) {}
