package ai.firesphere.fleet.service;

import ai.firesphere.common.dto.PageResponse;
import ai.firesphere.fleet.dto.*;
import ai.firesphere.fleet.model.VehicleStatus;
import ai.firesphere.fleet.model.VehicleType;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface FleetService {

    VehicleResponseDTO registerVehicle(VehicleRegisterRequest request);

    VehicleResponseDTO getVehicle(UUID id);

    PageResponse<VehicleResponseDTO> getVehicles(
            VehicleStatus status,
            VehicleType vehicleType,
            UUID stationId,
            String search,
            Pageable pageable
    );

    PageResponse<VehicleResponseDTO> getVehicles(VehicleStatus status, Pageable pageable);

    List<VehicleResponseDTO> getVehiclesByStation(UUID stationId);

    VehicleResponseDTO updateVehicle(UUID id, VehicleUpdateRequest request);

    VehicleResponseDTO updateStatus(UUID id, VehicleStatusUpdateRequest request);

    List<VehicleStatusHistoryDTO> getVehicleHistory(UUID id);

    VehicleReadinessDTO getVehicleReadiness(UUID id);

    VehicleResponseDTO updateTelematics(UUID id, VehicleTelematicsUpdateRequest request);
}
