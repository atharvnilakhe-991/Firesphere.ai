package ai.firesphere.dispatch.dto;

import java.util.List;
import java.util.UUID;

public record DispatchRecommendationResponse(
        UUID incidentId,
        UUID recommendedStationId,
        String stationName,
        double estimatedDistanceMeters,
        long estimatedResponseSeconds,
        List<RecommendedUnit> recommendedUnits,
        int recommendedResponderCount,
        String rationale
) {
    public record RecommendedUnit(
            String vehicleType,
            int quantity,
            String role
    ) {}
}
