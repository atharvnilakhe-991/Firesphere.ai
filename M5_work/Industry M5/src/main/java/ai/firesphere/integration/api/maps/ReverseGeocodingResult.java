package ai.firesphere.integration.api.maps;

public record ReverseGeocodingResult(
        String formattedAddress,
        String street,
        String city,
        String state,
        String postalCode,
        String country
) {}
