package ai.firesphere.gis.service;

import ai.firesphere.common.exception.ConflictException;
import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.contracts.gis.MapRoutingProviderContract;
import ai.firesphere.gis.dto.*;
import ai.firesphere.gis.model.HazardZone;
import ai.firesphere.gis.model.Hydrant;
import ai.firesphere.gis.repository.HazardZoneRepository;
import ai.firesphere.gis.repository.HydrantRepository;
import ai.firesphere.gis.util.GeometryUtils;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Production-ready GIS Operational Service implementation providing PostGIS spatial queries,
 * hydrant management, hazard zone containment, and deterministic contract-based mock routing.
 */
@Service
@Transactional
public class GisOperationalServiceImpl implements GisOperationalService {

    private static final Logger log = LoggerFactory.getLogger(GisOperationalServiceImpl.class);

    private final HydrantRepository hydrantRepository;
    private final HazardZoneRepository hazardZoneRepository;
    private final MapRoutingProviderContract mapRoutingProvider;

    public GisOperationalServiceImpl(HydrantRepository hydrantRepository,
                                     HazardZoneRepository hazardZoneRepository,
                                     MapRoutingProviderContract mapRoutingProvider) {
        this.hydrantRepository = hydrantRepository;
        this.hazardZoneRepository = hazardZoneRepository;
        this.mapRoutingProvider = mapRoutingProvider;
    }

    // ========================================================================
    // HYDRANT MANAGEMENT
    // ========================================================================

    @Override
    public HydrantResponseDTO registerHydrant(HydrantCreateRequest request) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        GeometryUtils.validateCoordinates(request.latitude(), request.longitude());

        String normalizedCode = request.hydrantCode().trim().toUpperCase();
        if (hydrantRepository.findByHydrantCodeAndTenantId(normalizedCode, tenantId).isPresent()) {
            throw new ConflictException("Hydrant with code already exists: " + normalizedCode);
        }

        Hydrant hydrant = new Hydrant();
        hydrant.setTenantId(tenantId);
        hydrant.setHydrantCode(normalizedCode);
        hydrant.setLatitude(request.latitude());
        hydrant.setLongitude(request.longitude());
        hydrant.setPressurePsi(request.pressurePsi());
        hydrant.setFlowRateLpm(request.flowRateLpm());
        hydrant.setOperable(request.operable() != null ? request.operable() : true);
        hydrant.setAddress(request.address());
        hydrant.synchronizeGeometry();

        Hydrant saved = hydrantRepository.save(hydrant);
        log.info("[GIS] Registered hydrant {} at [{}, {}] for tenant {}",
                saved.getHydrantCode(), saved.getLatitude(), saved.getLongitude(), tenantId);

        return HydrantResponseDTO.fromEntity(saved, null);
    }

    @Override
    @Transactional(readOnly = true)
    public HydrantResponseDTO getHydrantById(UUID id) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Hydrant hydrant = hydrantRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Hydrant not found: " + id));
        return HydrantResponseDTO.fromEntity(hydrant, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<HydrantResponseDTO> getHydrants(Boolean operableOnly, Pageable pageable) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Page<Hydrant> page;
        if (operableOnly != null) {
            page = hydrantRepository.findAllByTenantIdAndOperable(tenantId, operableOnly, pageable);
        } else {
            page = hydrantRepository.findAllByTenantId(tenantId, pageable);
        }
        return page.map(h -> HydrantResponseDTO.fromEntity(h, null));
    }

    @Override
    public HydrantResponseDTO updateHydrant(UUID id, HydrantUpdateRequest request) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Hydrant hydrant = hydrantRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Hydrant not found: " + id));

        GeometryUtils.validateCoordinates(request.latitude(), request.longitude());

        hydrant.setLatitude(request.latitude());
        hydrant.setLongitude(request.longitude());
        if (request.pressurePsi() != null) hydrant.setPressurePsi(request.pressurePsi());
        if (request.flowRateLpm() != null) hydrant.setFlowRateLpm(request.flowRateLpm());
        if (request.operable() != null) hydrant.setOperable(request.operable());
        if (request.address() != null) hydrant.setAddress(request.address());
        hydrant.synchronizeGeometry();

        Hydrant updated = hydrantRepository.save(hydrant);
        log.info("[GIS] Updated hydrant {} for tenant {}", updated.getHydrantCode(), tenantId);
        return HydrantResponseDTO.fromEntity(updated, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HydrantResponseDTO> findNearbyHydrants(double latitude, double longitude, double radiusMeters) {
        return findNearbyHydrants(latitude, longitude, radiusMeters, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HydrantResponseDTO> findNearbyHydrants(double latitude, double longitude, double radiusMeters, Boolean operableOnly) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        GeometryUtils.validateCoordinates(latitude, longitude);
        GeometryUtils.validateRadius(radiusMeters, GeometryUtils.MAX_SEARCH_RADIUS_METERS);

        // Convert radius in meters to mathematically bounded degree envelope
        double radiusDegrees = GeometryUtils.metersToMaxDegrees(radiusMeters, latitude);
        Point queryPoint = GeometryUtils.createPoint(longitude, latitude);

        List<Hydrant> candidates = Boolean.TRUE.equals(operableOnly)
                ? hydrantRepository.findNearbyOperableWithinDistance(tenantId, queryPoint, radiusDegrees)
                : hydrantRepository.findNearbyWithinDistance(tenantId, queryPoint, radiusDegrees);

        return candidates.stream()
                .map(h -> {
                    double distance = GeometryUtils.calculateHaversineDistanceMeters(
                            latitude, longitude, h.getLatitude(), h.getLongitude());
                    return new HydrantWithDistance(h, distance);
                })
                .filter(hd -> hd.distanceMeters <= radiusMeters)
                .sorted(Comparator.comparingDouble(hd -> hd.distanceMeters))
                .map(hd -> HydrantResponseDTO.fromEntity(hd.hydrant, hd.distanceMeters))
                .toList();
    }

    // ========================================================================
    // HAZARD ZONE MANAGEMENT
    // ========================================================================

    @Override
    public HazardZoneResponseDTO createHazardZone(HazardZoneCreateRequest request) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        GeometryUtils.validateCoordinates(request.centerLatitude(), request.centerLongitude());
        if (request.radiusMeters() <= 0) {
            throw new IllegalArgumentException("Hazard zone radius must be greater than zero");
        }

        String zoneCode = request.zoneCode() != null && !request.zoneCode().isBlank()
                ? request.zoneCode().trim().toUpperCase()
                : "HZ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        if (hazardZoneRepository.findByZoneCodeAndTenantId(zoneCode, tenantId).isPresent()) {
            throw new ConflictException("Hazard zone with code already exists: " + zoneCode);
        }

        HazardZone zone = new HazardZone();
        zone.setTenantId(tenantId);
        zone.setZoneCode(zoneCode);
        zone.setHazardName(request.hazardName().trim());
        zone.setHazardType(request.hazardType().trim().toUpperCase());
        zone.setCenterLatitude(request.centerLatitude());
        zone.setCenterLongitude(request.centerLongitude());
        zone.setRadiusMeters(request.radiusMeters());
        zone.setSeverity(request.severity().trim().toUpperCase());
        zone.setActive(true);
        zone.setEvacuationProtocol(request.evacuationProtocol());

        if (request.polygonWkt() != null && !request.polygonWkt().isBlank()) {
            Geometry geom = GeometryUtils.parseWkt(request.polygonWkt());
            zone.setGeom(geom);
        } else {
            zone.setGeom(GeometryUtils.createCircularPolygon(
                    request.centerLongitude(), request.centerLatitude(), request.radiusMeters(), 32));
        }

        HazardZone saved = hazardZoneRepository.save(zone);
        log.info("[GIS] Created hazard zone {} ({}) for tenant {}", saved.getZoneCode(), saved.getHazardName(), tenantId);

        return HazardZoneResponseDTO.fromEntity(saved, null);
    }

    @Override
    @Transactional(readOnly = true)
    public HazardZoneResponseDTO getHazardZoneById(UUID id) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        HazardZone zone = hazardZoneRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Hazard zone not found: " + id));
        return HazardZoneResponseDTO.fromEntity(zone, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<HazardZoneResponseDTO> getHazardZones(Boolean activeOnly, Pageable pageable) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Page<HazardZone> page;
        if (activeOnly != null) {
            page = hazardZoneRepository.findAllByTenantIdAndActive(tenantId, activeOnly, pageable);
        } else {
            page = hazardZoneRepository.findAllByTenantId(tenantId, pageable);
        }
        return page.map(z -> HazardZoneResponseDTO.fromEntity(z, null));
    }

    @Override
    public HazardZoneResponseDTO updateHazardZone(UUID id, HazardZoneUpdateRequest request) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        HazardZone zone = hazardZoneRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Hazard zone not found: " + id));

        GeometryUtils.validateCoordinates(request.centerLatitude(), request.centerLongitude());
        if (request.radiusMeters() <= 0) {
            throw new IllegalArgumentException("Hazard zone radius must be greater than zero");
        }

        zone.setHazardName(request.hazardName().trim());
        zone.setHazardType(request.hazardType().trim().toUpperCase());
        zone.setCenterLatitude(request.centerLatitude());
        zone.setCenterLongitude(request.centerLongitude());
        zone.setRadiusMeters(request.radiusMeters());
        zone.setSeverity(request.severity().trim().toUpperCase());
        if (request.active() != null) zone.setActive(request.active());
        if (request.evacuationProtocol() != null) zone.setEvacuationProtocol(request.evacuationProtocol());

        if (request.polygonWkt() != null && !request.polygonWkt().isBlank()) {
            Geometry geom = GeometryUtils.parseWkt(request.polygonWkt());
            zone.setGeom(geom);
        } else {
            zone.setGeom(GeometryUtils.createCircularPolygon(
                    request.centerLongitude(), request.centerLatitude(), request.radiusMeters(), 32));
        }

        HazardZone updated = hazardZoneRepository.save(zone);
        log.info("[GIS] Updated hazard zone {} for tenant {}", updated.getZoneCode(), tenantId);
        return HazardZoneResponseDTO.fromEntity(updated, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HazardZoneResponseDTO> findHazardZonesContainingPoint(double latitude, double longitude) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        GeometryUtils.validateCoordinates(latitude, longitude);

        Point queryPoint = GeometryUtils.createPoint(longitude, latitude);
        List<HazardZone> containingZones = hazardZoneRepository.findZonesContainingPoint(tenantId, queryPoint);

        return containingZones.stream()
                .map(z -> {
                    double dist = GeometryUtils.calculateHaversineDistanceMeters(
                            latitude, longitude, z.getCenterLatitude(), z.getCenterLongitude());
                    return HazardZoneResponseDTO.fromEntity(z, dist);
                })
                .sorted(Comparator.comparingDouble(HazardZoneResponseDTO::distanceMeters))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HazardZoneResponseDTO> findNearbyHazards(double latitude, double longitude, double radiusMeters) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        GeometryUtils.validateCoordinates(latitude, longitude);
        GeometryUtils.validateRadius(radiusMeters, GeometryUtils.MAX_SEARCH_RADIUS_METERS);

        double radiusDegrees = GeometryUtils.metersToMaxDegrees(radiusMeters, latitude);
        Point queryPoint = GeometryUtils.createPoint(longitude, latitude);

        List<HazardZone> candidates = hazardZoneRepository.findNearbyHazardsWithinDistance(tenantId, queryPoint, radiusDegrees);

        return candidates.stream()
                .map(z -> {
                    double dist = GeometryUtils.calculateHaversineDistanceMeters(
                            latitude, longitude, z.getCenterLatitude(), z.getCenterLongitude());
                    return new HazardWithDistance(z, dist);
                })
                .filter(zd -> zd.distanceMeters <= radiusMeters)
                .sorted(Comparator.comparingDouble(zd -> zd.distanceMeters))
                .map(zd -> HazardZoneResponseDTO.fromEntity(zd.zone, zd.distanceMeters))
                .toList();
    }

    // ========================================================================
    // ROUTE CALCULATION (CONTRACT BASELINE ONLY)
    // ========================================================================

    @Override
    @Transactional(readOnly = true)
    public RouteCalculationResponse calculateEmergencyRoute(RouteCalculationRequest request) {
        GeometryUtils.validateCoordinates(request.startLatitude(), request.startLongitude());
        GeometryUtils.validateCoordinates(request.destinationLatitude(), request.destinationLongitude());

        log.info("[GIS ROUTING - BASELINE CONTRACT] Computing mock emergency route from [{}, {}] to [{}, {}]",
                request.startLatitude(), request.startLongitude(),
                request.destinationLatitude(), request.destinationLongitude());

        MapRoutingProviderContract.RouteCalculationResult result = mapRoutingProvider.calculateEmergencyRoute(
                request.startLatitude(), request.startLongitude(),
                request.destinationLatitude(), request.destinationLongitude());

        return new RouteCalculationResponse(
                result.distanceMeters(),
                result.estimatedDurationSeconds(),
                result.waypoints(),
                result.routeGeometryPolyline()
        );
    }

    // Helper records for distance sorting
    private record HydrantWithDistance(Hydrant hydrant, double distanceMeters) {}
    private record HazardWithDistance(HazardZone zone, double distanceMeters) {}
}
