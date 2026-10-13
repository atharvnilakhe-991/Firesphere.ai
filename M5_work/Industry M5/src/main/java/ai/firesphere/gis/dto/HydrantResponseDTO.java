package ai.firesphere.gis.dto;

import ai.firesphere.gis.model.Hydrant;

import java.time.Instant;
import java.util.UUID;

public record HydrantResponseDTO(
        UUID id,
        String hydrantCode,
        Double latitude,
        Double longitude,
        Double pressurePsi,
        Double flowRateLpm,
        Boolean operable,
        String address,
        Double distanceMeters,
        Instant createdAt,
        Instant updatedAt
) {
    public static HydrantResponseDTO fromEntity(Hydrant entity, Double distanceMeters) {
        return new HydrantResponseDTO(
                entity.getId(),
                entity.getHydrantCode(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getPressurePsi(),
                entity.getFlowRateLpm(),
                entity.getOperable(),
                entity.getAddress(),
                distanceMeters,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
