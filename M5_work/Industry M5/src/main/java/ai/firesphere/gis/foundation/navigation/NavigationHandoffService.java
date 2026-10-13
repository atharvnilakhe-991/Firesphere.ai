package ai.firesphere.gis.foundation.navigation;

import ai.firesphere.gis.foundation.validation.GpsCoordinateValidator;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

/**
 * Generates provider-neutral navigation handoff payloads for field officer MDTs and mobile devices.
 * Prepares standard RFC-5870 geo URIs and vendor-neutral intents without auto-launching external applications.
 */
@Service
public class NavigationHandoffService {

    private final GpsCoordinateValidator validator;

    public NavigationHandoffService(GpsCoordinateValidator validator) {
        this.validator = validator;
    }

    public OfficerNavigationHandoffDTO prepareHandoff(
            Double destinationLatitude,
            Double destinationLongitude,
            String destinationLabel,
            String destinationAddress
    ) {
        if (destinationLatitude == null || destinationLongitude == null) {
            return OfficerNavigationHandoffDTO.error(
                    NavigationHandoffStatus.MISSING_COORDINATES,
                    "Destination latitude and longitude must both be provided"
            );
        }

        var validation = validator.validate(destinationLatitude, destinationLongitude, null);
        if (!validation.valid()) {
            return OfficerNavigationHandoffDTO.error(
                    NavigationHandoffStatus.INVALID_DESTINATION,
                    "Destination coordinates are invalid: " + String.join(", ", validation.validationErrors())
            );
        }

        String label = (destinationLabel != null && !destinationLabel.isBlank())
                ? destinationLabel.trim()
                : "Incident Scene";
        String encodedLabel = URLEncoder.encode(label, StandardCharsets.UTF_8);

        // Standard RFC-5870 URI format
        String geoUri = String.format(Locale.ROOT, "geo:%.6f,%.6f?q=%.6f,%.6f(%s)",
                destinationLatitude, destinationLongitude, destinationLatitude, destinationLongitude, encodedLabel);

        // Universal web/app URLs
        String googleMapsUrl = String.format(Locale.ROOT,
                "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f",
                destinationLatitude, destinationLongitude);

        String appleMapsUrl = String.format(Locale.ROOT,
                "https://maps.apple.com/?daddr=%.6f,%.6f&dirflg=d",
                destinationLatitude, destinationLongitude);

        String wazeUrl = String.format(Locale.ROOT,
                "https://waze.com/ul?ll=%.6f,%.6f&navigate=yes",
                destinationLatitude, destinationLongitude);

        return new OfficerNavigationHandoffDTO(
                destinationLatitude,
                destinationLongitude,
                label,
                destinationAddress,
                geoUri,
                googleMapsUrl,
                appleMapsUrl,
                wazeUrl,
                NavigationHandoffStatus.READY,
                "Navigation handoff payload prepared successfully",
                Instant.now()
        );
    }
}
