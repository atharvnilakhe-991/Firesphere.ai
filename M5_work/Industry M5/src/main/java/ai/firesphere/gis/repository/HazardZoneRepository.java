package ai.firesphere.gis.repository;

import ai.firesphere.gis.model.HazardZone;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HazardZoneRepository extends JpaRepository<HazardZone, UUID> {

    Optional<HazardZone> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<HazardZone> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<HazardZone> findAllByTenantIdAndActive(UUID tenantId, Boolean active, Pageable pageable);

    List<HazardZone> findAllByTenantId(UUID tenantId);

    Optional<HazardZone> findByZoneCodeAndTenantId(String zoneCode, UUID tenantId);

    /**
     * Spatial intersection query (ST_Intersects).
     * Authoritatively matches points strictly inside polygon OR on perimeter boundary line.
     */
    @Query("SELECT z FROM HazardZone z WHERE z.tenantId = :tenantId AND z.active = true AND intersects(z.geom, :point) = true")
    List<HazardZone> findZonesContainingPoint(@Param("tenantId") UUID tenantId, @Param("point") Point point);

    /**
     * Spatial proximity query for hazard zones using distance function.
     */
    @Query("SELECT z FROM HazardZone z WHERE z.tenantId = :tenantId AND z.active = true AND distance(z.geom, :point) <= :radiusDegrees ORDER BY distance(z.geom, :point) ASC")
    List<HazardZone> findNearbyHazardsWithinDistance(@Param("tenantId") UUID tenantId, @Param("point") Point point, @Param("radiusDegrees") double radiusDegrees);

    /**
     * Production PostGIS native point-in-zone query using ST_Intersects.
     * Includes points strictly inside as well as points on the polygon perimeter boundary.
     */
    @Query(value = """
        SELECT z.* FROM gis_hazard_zones z
        WHERE z.tenant_id = :tenantId
          AND z.is_active = true
          AND ST_Intersects(z.geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)) = true
    """, nativeQuery = true)
    List<HazardZone> findZonesContainingPointPostGisNative(
            @Param("tenantId") UUID tenantId,
            @Param("lat") double lat,
            @Param("lon") double lon
    );

    /**
     * Production PostGIS native geography proximity query.
     */
    @Query(value = """
        SELECT z.* FROM gis_hazard_zones z
        WHERE z.tenant_id = :tenantId
          AND z.is_active = true
          AND ST_DWithin(z.geom::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radiusMeters)
        ORDER BY ST_Distance(z.geom::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography) ASC
    """, nativeQuery = true)
    List<HazardZone> findNearbyPostGisNative(
            @Param("tenantId") UUID tenantId,
            @Param("lat") double lat,
            @Param("lon") double lon,
            @Param("radiusMeters") double radiusMeters
    );
}
