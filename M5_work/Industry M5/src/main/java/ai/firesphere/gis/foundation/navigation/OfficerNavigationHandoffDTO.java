package ai.firesphere.gis.foundation.navigation;

import java.time.Instant;

public record OfficerNavigationHandoffDTO(
        Double destinationLatitude,
        Double destinationLongitude,
        String destinationLabel,
        String destinationAddress,
        String universalGeoUri,
        String googleMapsUrl,
        String appleMapsUrl,
        String wazeUrl,
        NavigationHandoffStatus status,
        String statusMessage,
        Instant preparedAt
) {

    public static OfficerNavigationHandoffDTO error(NavigationHandoffStatus status, String errorMessage) {
        return new OfficerNavigationHandoffDTO(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                status,
                errorMessage,
                Instant.now()
        );
    }
}
