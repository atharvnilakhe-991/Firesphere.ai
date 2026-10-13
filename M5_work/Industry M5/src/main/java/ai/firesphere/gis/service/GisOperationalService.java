package ai.firesphere.gis.service;

import ai.firesphere.gis.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface GisOperationalService {

    // --- Hydrant Management ---
    HydrantResponseDTO registerHydrant(HydrantCreateRequest request);

    HydrantResponseDTO getHydrantById(UUID id);

    Page<HydrantResponseDTO> getHydrants(Boolean operableOnly, Pageable pageable);

    HydrantResponseDTO updateHydrant(UUID id, HydrantUpdateRequest request);

    List<HydrantResponseDTO> findNearbyHydrants(double latitude, double longitude, double radiusMeters);

    List<HydrantResponseDTO> findNearbyHydrants(double latitude, double longitude, double radiusMeters, Boolean operableOnly);

    // --- Hazard Zone Management ---
    HazardZoneResponseDTO createHazardZone(HazardZoneCreateRequest request);

    HazardZoneResponseDTO getHazardZoneById(UUID id);

    Page<HazardZoneResponseDTO> getHazardZones(Boolean activeOnly, Pageable pageable);

    HazardZoneResponseDTO updateHazardZone(UUID id, HazardZoneUpdateRequest request);

    List<HazardZoneResponseDTO> findHazardZonesContainingPoint(double latitude, double longitude);

    List<HazardZoneResponseDTO> findNearbyHazards(double latitude, double longitude, double radiusMeters);

    // --- Routing Contract Baseline ---
    RouteCalculationResponse calculateEmergencyRoute(RouteCalculationRequest request);
}
