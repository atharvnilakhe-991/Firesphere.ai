package ai.firesphere.integration.api.maps;

public record RouteMatrixRequest(
        double originLat,
        double originLng,
        double destLat,
        double destLng,
        String travelMode // DRIVING, EMERGENCY, TRUCK
) {
    public RouteMatrixRequest {
        if (travelMode == null || travelMode.isBlank()) {
            travelMode = "EMERGENCY";
        }
    }
}
