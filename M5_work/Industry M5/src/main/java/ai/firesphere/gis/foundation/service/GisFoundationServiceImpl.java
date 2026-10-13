package ai.firesphere.gis.foundation.service;

import ai.firesphere.gis.foundation.display.MapDisplayDTO;
import ai.firesphere.gis.foundation.display.MapDisplayService;
import ai.firesphere.gis.foundation.dto.LocationValidationRequest;
import ai.firesphere.gis.foundation.dto.MapDisplayRequest;
import ai.firesphere.gis.foundation.dto.NearbyInfrastructureRequest;
import ai.firesphere.gis.foundation.dto.NavigationHandoffRequest;
import ai.firesphere.gis.foundation.infrastructure.NearbyInfrastructureDTO;
import ai.firesphere.gis.foundation.infrastructure.NearbyInfrastructureService;
import ai.firesphere.gis.foundation.model.LocationRecord;
import ai.firesphere.gis.foundation.model.LocationValidationResult;
import ai.firesphere.gis.foundation.navigation.NavigationHandoffService;
import ai.firesphere.gis.foundation.navigation.OfficerNavigationHandoffDTO;
import ai.firesphere.gis.foundation.validation.GpsCoordinateValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GisFoundationServiceImpl implements GisFoundationService {

    private static final Logger log = LoggerFactory.getLogger(GisFoundationServiceImpl.class);

    private final GpsCoordinateValidator validator;
    private final MapDisplayService mapDisplayService;
    private final NearbyInfrastructureService nearbyInfrastructureService;
    private final NavigationHandoffService navigationHandoffService;

    public GisFoundationServiceImpl(
            GpsCoordinateValidator validator,
            MapDisplayService mapDisplayService,
            NearbyInfrastructureService nearbyInfrastructureService,
            NavigationHandoffService navigationHandoffService
    ) {
        this.validator = validator;
        this.mapDisplayService = mapDisplayService;
        this.nearbyInfrastructureService = nearbyInfrastructureService;
        this.navigationHandoffService = navigationHandoffService;
    }

    @Override
    public LocationValidationResult validateCoordinates(Double latitude, Double longitude, Double accuracyMeters) {
        log.debug("[GIS FOUNDATION] Validating coordinates lat={}, lon={}, acc={}", latitude, longitude, accuracyMeters);
        return validator.validate(latitude, longitude, accuracyMeters);
    }

    @Override
    public LocationValidationResult validateLocation(LocationValidationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("LocationValidationRequest cannot be null");
        }
        return validateCoordinates(request.latitude(), request.longitude(), request.accuracyMeters());
    }

    @Override
    public MapDisplayDTO getMapDisplay(MapDisplayRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("MapDisplayRequest cannot be null");
        }
        log.debug("[GIS FOUNDATION] Generating map display for ref={}", request.referenceId());
        LocationRecord location = LocationRecord.of(
                request.referenceId(),
                request.referenceType(),
                request.latitude(),
                request.longitude(),
                request.address(),
                request.accuracyMeters()
        );
        return mapDisplayService.generateMapDisplay(location);
    }

    @Override
    public NearbyInfrastructureDTO getNearbyInfrastructure(NearbyInfrastructureRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("NearbyInfrastructureRequest cannot be null");
        }
        log.debug("[GIS FOUNDATION] Querying infrastructure around [{}, {}], radius={}m",
                request.latitude(), request.longitude(), request.radiusMeters());
        return nearbyInfrastructureService.findNearby(
                request.latitude(),
                request.longitude(),
                request.radiusMeters(),
                request.operableOnly()
        );
    }

    @Override
    public OfficerNavigationHandoffDTO prepareNavigationHandoff(NavigationHandoffRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("NavigationHandoffRequest cannot be null");
        }
        log.debug("[GIS FOUNDATION] Preparing navigation handoff for destination [{}, {}]",
                request.destinationLatitude(), request.destinationLongitude());
        return navigationHandoffService.prepareHandoff(
                request.destinationLatitude(),
                request.destinationLongitude(),
                request.destinationLabel(),
                request.destinationAddress()
        );
    }
}
