package ai.firesphere.gis.repository;

import ai.firesphere.gis.model.Hydrant;
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
public interface HydrantRepository extends JpaRepository<Hydrant, UUID> {

    Optional<Hydrant> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Hydrant> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<Hydrant> findAllByTenantIdAndOperable(UUID tenantId, Boolean operable, Pageable pageable);

    List<Hydrant> findAllByTenantIdAndOperable(UUID tenantId, Boolean operable);

    Optional<Hydrant> findByHydrantCodeAndTenantId(String hydrantCode, UUID tenantId);

    /**
     * Spatial proximity query using Hibernate spatial distance function.
     * Evaluates Euclidean/planar distance in degrees against geometric Point.
     */
    @Query("SELECT h FROM Hydrant h WHERE h.tenantId = :tenantId AND distance(h.geom, :point) <= :radiusDegrees ORDER BY distance(h.geom, :point) ASC")
    List<Hydrant> findNearbyWithinDistance(@Param("tenantId") UUID tenantId, @Param("point") Point point, @Param("radiusDegrees") double radiusDegrees);

    /**
     * Spatial proximity query for operable hydrants using Hibernate spatial distance function.
     */
    @Query("SELECT h FROM Hydrant h WHERE h.tenantId = :tenantId AND h.operable = true AND distance(h.geom, :point) <= :radiusDegrees ORDER BY distance(h.geom, :point) ASC")
    List<Hydrant> findNearbyOperableWithinDistance(@Param("tenantId") UUID tenantId, @Param("point") Point point, @Param("radiusDegrees") double radiusDegrees);

    /**
     * Production PostGIS native geography proximity query.
     * Evaluates exact spheroid distance in meters and utilizes GiST geography index (ST_DWithin).
     */
    @Query(value = """
        SELECT h.* FROM gis_hydrants h
        WHERE h.tenant_id = :tenantId
          AND (:operableOnly = false OR h.is_operable = true)
          AND ST_DWithin(h.geom::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radiusMeters)
        ORDER BY ST_Distance(h.geom::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography) ASC
    """, nativeQuery = true)
    List<Hydrant> findNearbyPostGisNative(
            @Param("tenantId") UUID tenantId,
            @Param("lat") double lat,
            @Param("lon") double lon,
            @Param("radiusMeters") double radiusMeters,
            @Param("operableOnly") boolean operableOnly
    );
}
