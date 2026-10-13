package ai.firesphere.integration.provider.maps;

import ai.firesphere.integration.api.maps.GeocodingRequest;
import ai.firesphere.integration.api.maps.GeocodingResult;
import ai.firesphere.integration.api.maps.ReverseGeocodingRequest;
import ai.firesphere.integration.api.maps.ReverseGeocodingResult;
import ai.firesphere.integration.api.maps.RouteMatrixRequest;
import ai.firesphere.integration.api.maps.RouteMatrixResult;

/**
 * Provider SPI interface for Maps & Geolocation services (e.g. Google Maps/OSM mock).
 */
public interface MapsProvider {

    String getProviderName();

    GeocodingResult geocode(GeocodingRequest request);

    ReverseGeocodingResult reverseGeocode(ReverseGeocodingRequest request);

    RouteMatrixResult calculateRoute(RouteMatrixRequest request);
}
