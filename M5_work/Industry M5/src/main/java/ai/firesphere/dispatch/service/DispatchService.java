package ai.firesphere.dispatch.service;

import ai.firesphere.common.dto.PageResponse;
import ai.firesphere.dispatch.dto.*;
import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.dispatch.model.DispatchStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface DispatchService {

    DispatchResponseDTO createDispatch(DispatchCreateRequest request);

    DispatchResponseDTO getDispatch(UUID id);

    PageResponse<DispatchResponseDTO> getDispatches(
            DispatchStatus status,
            DispatchPriority priority,
            UUID incidentId,
            UUID stationId,
            Pageable pageable
    );

    PageResponse<DispatchResponseDTO> getDispatches(DispatchStatus status, Pageable pageable);

    List<DispatchResponseDTO> getDispatchesForIncident(UUID incidentId);

    DispatchResponseDTO updateDispatch(UUID id, DispatchUpdateRequest request);

    DispatchResponseDTO updateDispatchStatus(UUID id, DispatchStatusUpdateRequest request);

    DispatchResponseDTO acknowledgeDispatch(UUID id, UUID responderId);

    List<DispatchStatusHistoryDTO> getDispatchHistory(UUID id);

    DispatchRecommendationResponse getRecommendations(DispatchRecommendationRequest request);
}
