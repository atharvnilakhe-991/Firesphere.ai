package ai.firesphere.gis.dto;

import ai.firesphere.gis.model.HazardZone;
import ai.firesphere.gis.util.GeometryUtils;

import java.time.Instant;
import java.util.UUID;

public record HazardZoneResponseDTO(
        UUID id,
        String zoneCode,
        String hazardName,
        String hazardType,
        Double centerLatitude,
        Double centerLongitude,
        Double radiusMeters,
        String severity,
        Boolean active,
        String evacuationProtocol,
        Double distanceMeters,
        String polygonWkt,
        Instant createdAt,
        Instant updatedAt
) {
    public static HazardZoneResponseDTO fromEntity(HazardZone entity, Double distanceMeters) {
        String wkt = entity.getGeom() != null ? GeometryUtils.toWkt(entity.getGeom()) : null;
        return new HazardZoneResponseDTO(
                entity.getId(),
                entity.getZoneCode(),
                entity.getHazardName(),
                entity.getHazardType(),
                entity.getCenterLatitude(),
                entity.getCenterLongitude(),
                entity.getRadiusMeters(),
                entity.getSeverity(),
                entity.getActive(),
                entity.getEvacuationProtocol(),
                distanceMeters,
                wkt,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
