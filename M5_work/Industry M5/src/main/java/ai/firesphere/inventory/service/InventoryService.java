package ai.firesphere.inventory.service;

import ai.firesphere.common.dto.PageResponse;
import ai.firesphere.inventory.dto.*;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface InventoryService {

    InventoryItemResponseDTO registerItem(InventoryItemCreateRequest request);

    InventoryItemResponseDTO getItem(UUID id);

    PageResponse<InventoryItemResponseDTO> getItems(
            String category,
            UUID stationId,
            Boolean isActive,
            Boolean lowStock,
            String search,
            Pageable pageable);

    PageResponse<InventoryItemResponseDTO> getItems(Pageable pageable);

    PageResponse<InventoryItemResponseDTO> getLowStockItems(Pageable pageable);

    List<InventoryItemResponseDTO> getItemsByStation(UUID stationId);

    InventoryItemResponseDTO updateItem(UUID id, InventoryItemUpdateRequest request);

    StockMovementResponseDTO receiveStock(UUID id, StockReceiveRequest request);

    StockMovementResponseDTO issueStock(UUID id, StockIssueRequest request);

    StockMovementResponseDTO issueStock(StockIssueRequest request);

    StockMovementResponseDTO returnStock(UUID id, StockReturnRequest request);

    StockMovementResponseDTO returnStock(StockReturnRequest request);

    StockMovementResponseDTO adjustStock(UUID id, StockAdjustmentRequest request);

    PageResponse<StockMovementResponseDTO> getItemMovements(UUID itemId, Pageable pageable);
}
