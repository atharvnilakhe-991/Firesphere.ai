package ai.firesphere.gis.foundation.service;

import ai.firesphere.gis.foundation.display.MapDisplayDTO;
import ai.firesphere.gis.foundation.dto.LocationValidationRequest;
import ai.firesphere.gis.foundation.dto.MapDisplayRequest;
import ai.firesphere.gis.foundation.dto.NearbyInfrastructureRequest;
import ai.firesphere.gis.foundation.dto.NavigationHandoffRequest;
import ai.firesphere.gis.foundation.infrastructure.NearbyInfrastructureDTO;
import ai.firesphere.gis.foundation.model.LocationValidationResult;
import ai.firesphere.gis.foundation.navigation.OfficerNavigationHandoffDTO;

/**
 * Unified service contract for GIS Foundation capabilities.
 * Supports location validation, map display packaging, nearby fire infrastructure
 * querying, and provider-neutral officer navigation handoff.
 */
public interface GisFoundationService {

    LocationValidationResult validateCoordinates(Double latitude, Double longitude, Double accuracyMeters);

    LocationValidationResult validateLocation(LocationValidationRequest request);

    MapDisplayDTO getMapDisplay(MapDisplayRequest request);

    NearbyInfrastructureDTO getNearbyInfrastructure(NearbyInfrastructureRequest request);

    OfficerNavigationHandoffDTO prepareNavigationHandoff(NavigationHandoffRequest request);
}
