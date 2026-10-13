package ai.firesphere.integration.api.maps;

public record GeocodingRequest(
        String rawAddress,
        String city,
        String postalCode,
        String countryCode
) {}
