package ai.firesphere.integration.api.maps;

public record GeocodingResult(
        String formattedAddress,
        double latitude,
        double longitude,
        String placeId,
        double confidenceScore
) {}
