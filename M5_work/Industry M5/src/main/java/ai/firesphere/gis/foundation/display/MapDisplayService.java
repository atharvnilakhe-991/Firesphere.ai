package ai.firesphere.gis.foundation.display;

import ai.firesphere.gis.foundation.model.LocationRecord;
import ai.firesphere.gis.foundation.validation.GpsCoordinateValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service producing map display metadata and bounding views for properties and inspections.
 * Defaults to deterministic MOCK_MODE without external network dependencies.
 * Provides configurable simulation controls for provider availability and failure testing.
 */
@Service
public class MapDisplayService {

    private static final Logger log = LoggerFactory.getLogger(MapDisplayService.class);

    private final GpsCoordinateValidator validator;
    private final AtomicBoolean simulatedProviderUnavailable = new AtomicBoolean(false);

    public MapDisplayService(GpsCoordinateValidator validator) {
        this.validator = validator;
    }

    public MapDisplayDTO generateMapDisplay(LocationRecord location) {
        if (location == null) {
            throw new IllegalArgumentException("LocationRecord cannot be null for map display generation");
        }

        var validationResult = validator.validate(location.latitude(), location.longitude(), location.accuracyMeters());
        if (!validationResult.valid()) {
            throw new IllegalArgumentException("Cannot generate map display with invalid coordinates: "
                    + String.join(", ", validationResult.validationErrors()));
        }

        if (simulatedProviderUnavailable.get()) {
            log.warn("[MAP DISPLAY] Map tile provider currently unavailable");
            return new MapDisplayDTO(
                    location.latitude(),
                    location.longitude(),
                    15,
                    calculateBoundingBox(location.latitude(), location.longitude(), 0.005),
                    List.of(),
                    MapProviderStatus.UNAVAILABLE,
                    false,
                    "Map visualization provider is currently unreachable. Displaying fallback coordinate summary.",
                    Instant.now()
            );
        }

        List<MapMarkerDTO> markers = new ArrayList<>();

        // Primary location marker
        String markerColor = switch (location.referenceType()) {
            case PROPERTY -> "#38A169";   // Green
            case INSPECTION -> "#3182CE"; // Blue
            case INCIDENT -> "#E53E3E";   // Red
            case FACILITY -> "#805AD5";   // Purple
            default -> "#718096";         // Slate Gray
        };

        markers.add(new MapMarkerDTO(
                "marker-" + location.referenceId(),
                location.referenceId(),
                location.referenceType().name(),
                location.address() != null ? location.address() : "Target Site",
                "Accuracy: " + (location.accuracyMeters() != null ? location.accuracyMeters() + "m" : "Unspecified"),
                location.latitude(),
                location.longitude(),
                markerColor,
                location.accuracyMeters(),
                "marker-pin-" + location.referenceType().name().toLowerCase()
        ));

        // Bounding box approximation (~500m radius around target)
        double delta = 0.0045; // roughly 500m
        MapDisplayDTO.BoundingBox bbox = calculateBoundingBox(location.latitude(), location.longitude(), delta);

        return new MapDisplayDTO(
                location.latitude(),
                location.longitude(),
                16,
                bbox,
                markers,
                MapProviderStatus.MOCK_MODE,
                false,
                "Map displayed using FireSphere local tactical mock provider. No external tile servers contacted.",
                Instant.now()
        );
    }

    private MapDisplayDTO.BoundingBox calculateBoundingBox(double lat, double lon, double delta) {
        return new MapDisplayDTO.BoundingBox(
                lat - delta,
                lon - delta,
                lat + delta,
                lon + delta
        );
    }

    // --- Simulation Controls for Testing ---

    public void setSimulatedProviderUnavailable(boolean unavailable) {
        this.simulatedProviderUnavailable.set(unavailable);
    }

    public boolean isSimulatedProviderUnavailable() {
        return this.simulatedProviderUnavailable.get();
    }
}
