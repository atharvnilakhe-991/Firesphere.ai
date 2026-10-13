package ai.firesphere.integration.api.maps;

public record ReverseGeocodingRequest(
        double latitude,
        double longitude
) {}
