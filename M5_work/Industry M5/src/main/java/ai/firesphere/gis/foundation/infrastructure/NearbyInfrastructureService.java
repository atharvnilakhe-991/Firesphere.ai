package ai.firesphere.gis.foundation.infrastructure;

import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.gis.foundation.validation.GpsCoordinateValidator;
import ai.firesphere.gis.model.Hydrant;
import ai.firesphere.gis.repository.HydrantRepository;
import ai.firesphere.gis.util.GeometryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service for querying nearby fire infrastructure (hydrants, stations).
 * Reuses the existing HydrantRepository data source and calculates accurate geodesic distances.
 * Safely handles empty results and disconnected database environments.
 */
@Service
public class NearbyInfrastructureService {

    private static final Logger log = LoggerFactory.getLogger(NearbyInfrastructureService.class);

    private final HydrantRepository hydrantRepository;
    private final GpsCoordinateValidator validator;
    private final List<InfrastructureItemDTO> fixtureItems = new CopyOnWriteArrayList<>();

    public NearbyInfrastructureService(
            @Autowired(required = false) HydrantRepository hydrantRepository,
            GpsCoordinateValidator validator
    ) {
        this.hydrantRepository = hydrantRepository;
        this.validator = validator;
    }

    public NearbyInfrastructureDTO findNearby(double latitude, double longitude, double radiusMeters, Boolean operableOnly) {
        var validation = validator.validate(latitude, longitude, null);
        if (!validation.valid()) {
            throw new IllegalArgumentException("Invalid search center coordinates: "
                    + String.join(", ", validation.validationErrors()));
        }

        if (radiusMeters <= 0.0 || Double.isNaN(radiusMeters) || Double.isInfinite(radiusMeters)) {
            throw new IllegalArgumentException("Search radius must be a positive non-zero number in meters");
        }

        List<InfrastructureItemDTO> results = new ArrayList<>();

        // 1. Search database via HydrantRepository if available
        if (hydrantRepository != null) {
            try {
                UUID tenantId = TenantContext.getTenantId();
                List<Hydrant> hydrants;
                if (Boolean.TRUE.equals(operableOnly)) {
                    hydrants = hydrantRepository.findAllByTenantIdAndOperable(tenantId, true);
                } else {
                    hydrants = hydrantRepository.findAllByTenantId(tenantId, org.springframework.data.domain.Pageable.unpaged()).getContent();
                }

                for (Hydrant h : hydrants) {
                    if (h.getLatitude() != null && h.getLongitude() != null) {
                        double dist = GeometryUtils.calculateHaversineDistanceMeters(
                                latitude, longitude, h.getLatitude(), h.getLongitude()
                        );
                        if (dist <= radiusMeters) {
                            String details = String.format("Pressure: %.1f psi, Flow: %.1f lpm",
                                    h.getPressurePsi() != null ? h.getPressurePsi() : 0.0,
                                    h.getFlowRateLpm() != null ? h.getFlowRateLpm() : 0.0);
                            results.add(new InfrastructureItemDTO(
                                    h.getId() != null ? h.getId().toString() : UUID.randomUUID().toString(),
                                    h.getHydrantCode(),
                                    "FIRE_HYDRANT",
                                    h.getLatitude(),
                                    h.getLongitude(),
                                    Math.round(dist * 10.0) / 10.0,
                                    Boolean.TRUE.equals(h.getOperable()) ? "OPERABLE" : "INOPERABLE",
                                    h.getAddress() != null ? h.getAddress() : "Public Right-of-Way",
                                    details
                            ));
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("[INFRASTRUCTURE QUERY] Database query failed or unattached, checking fixture fallback: {}", ex.getMessage());
            }
        }

        // 2. Include any registered fixture items (used for testing or offline simulations)
        for (InfrastructureItemDTO fix : fixtureItems) {
            double dist = GeometryUtils.calculateHaversineDistanceMeters(
                    latitude, longitude, fix.latitude(), fix.longitude()
            );
            if (dist <= radiusMeters) {
                if (operableOnly == null || !operableOnly || "OPERABLE".equalsIgnoreCase(fix.status())) {
                    results.add(new InfrastructureItemDTO(
                            fix.id(),
                            fix.code(),
                            fix.infrastructureType(),
                            fix.latitude(),
                            fix.longitude(),
                            Math.round(dist * 10.0) / 10.0,
                            fix.status(),
                            fix.address(),
                            fix.operationalDetails()
                    ));
                }
            }
        }

        // 3. Sort by closest first
        results.sort(Comparator.comparingDouble(InfrastructureItemDTO::distanceMeters));

        if (results.isEmpty()) {
            return NearbyInfrastructureDTO.empty(latitude, longitude, radiusMeters);
        }

        Double nearest = results.get(0).distanceMeters();
        return new NearbyInfrastructureDTO(
                latitude,
                longitude,
                radiusMeters,
                results.size(),
                nearest,
                results
        );
    }

    // --- Fixture Support for Testing ---

    public void addFixtureItem(InfrastructureItemDTO item) {
        if (item != null) {
            this.fixtureItems.add(item);
        }
    }

    public void clearFixtures() {
        this.fixtureItems.clear();
    }
}
