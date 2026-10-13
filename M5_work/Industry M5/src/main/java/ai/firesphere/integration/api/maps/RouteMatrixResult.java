package ai.firesphere.integration.api.maps;

public record RouteMatrixResult(
        double distanceMeters,
        long estimatedDurationSeconds,
        String routeSummary,
        String trafficCondition
) {}
