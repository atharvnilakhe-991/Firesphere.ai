package ai.firesphere.integration.api.maps;

import ai.firesphere.integration.core.model.IntegrationRequest;
import ai.firesphere.integration.core.model.IntegrationResponse;

/**
 * Internal business contract for GIS and Mapping third-party integrations (Geocoding, Routing).
 */
public interface MapsIntegration {

    IntegrationResponse<GeocodingResult> geocode(IntegrationRequest<GeocodingRequest> request);

    IntegrationResponse<ReverseGeocodingResult> reverseGeocode(IntegrationRequest<ReverseGeocodingRequest> request);

    IntegrationResponse<RouteMatrixResult> calculateRoute(IntegrationRequest<RouteMatrixRequest> request);
}
