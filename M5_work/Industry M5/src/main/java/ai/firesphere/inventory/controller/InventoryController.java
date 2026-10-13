package ai.firesphere.inventory.controller;

import ai.firesphere.common.dto.PageResponse;
import ai.firesphere.inventory.dto.*;
import ai.firesphere.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/items")
@Tag(name = "Fire Store & Inventory Operations", description = "Endpoints for equipment tracking, consumables, stock issue/return, receipts, adjustments, and movements")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping
    @Operation(summary = "Register inventory item", description = "Onboards a new inventory SKU with category, station assignment, and initial quantity")
    public ResponseEntity<InventoryItemResponseDTO> registerItem(@Valid @RequestBody InventoryItemCreateRequest request) {
        InventoryItemResponseDTO response = inventoryService.registerItem(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List inventory items", description = "Paginated list of inventory items with filtering by category, station, active status, low-stock, and search text")
    public ResponseEntity<PageResponse<InventoryItemResponseDTO>> listItems(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID stationId,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) Boolean lowStock,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "sku", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(inventoryService.getItems(category, stationId, isActive, lowStock, search, pageable));
    }

    @GetMapping("/low-stock")
    @Operation(summary = "List low stock items", description = "Retrieves all inventory items where quantity on hand is below or equal to the minimum reorder threshold")
    public ResponseEntity<PageResponse<InventoryItemResponseDTO>> listLowStockItems(
            @PageableDefault(size = 20, sort = "quantityOnHand", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(inventoryService.getLowStockItems(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get inventory item by ID", description = "Retrieves complete inventory item details by UUID for the current tenant")
    public ResponseEntity<InventoryItemResponseDTO> getItemById(@PathVariable UUID id) {
        return ResponseEntity.ok(inventoryService.getItem(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update inventory metadata", description = "Updates mutable item attributes (name, category, description, threshold, location, batch, active state). Does NOT modify quantity on hand.")
    public ResponseEntity<InventoryItemResponseDTO> updateItem(
            @PathVariable UUID id,
            @Valid @RequestBody InventoryItemUpdateRequest request) {
        return ResponseEntity.ok(inventoryService.updateItem(id, request));
    }

    @PostMapping("/{id}/receive")
    @Operation(summary = "Receive stock", description = "Intake/restock operation increasing quantity on hand with an auditable RESTOCK movement record")
    public ResponseEntity<StockMovementResponseDTO> receiveStock(
            @PathVariable UUID id,
            @Valid @RequestBody StockReceiveRequest request) {
        return ResponseEntity.ok(inventoryService.receiveStock(id, request));
    }

    @PostMapping("/{id}/issue")
    @Operation(summary = "Issue stock", description = "Dispenses stock for field deployment or incident response with an auditable ISSUE movement record")
    public ResponseEntity<StockMovementResponseDTO> issueStock(
            @PathVariable UUID id,
            @Valid @RequestBody StockIssueRequest request) {
        return ResponseEntity.ok(inventoryService.issueStock(id, request));
    }

    @PostMapping("/{id}/return")
    @Operation(summary = "Return stock", description = "Returns unused equipment or recovered gear back into store with an auditable RETURN movement record")
    public ResponseEntity<StockMovementResponseDTO> returnStock(
            @PathVariable UUID id,
            @Valid @RequestBody StockReturnRequest request) {
        return ResponseEntity.ok(inventoryService.returnStock(id, request));
    }

    @PostMapping("/{id}/adjust")
    @Operation(summary = "Adjust stock quantity", description = "Performs an audited cycle count delta or write-off adjustment (positive or negative) without allowing negative stock")
    public ResponseEntity<StockMovementResponseDTO> adjustStock(
            @PathVariable UUID id,
            @Valid @RequestBody StockAdjustmentRequest request) {
        return ResponseEntity.ok(inventoryService.adjustStock(id, request));
    }

    @GetMapping("/{id}/movements")
    @Operation(summary = "Get item stock movements", description = "Retrieves the chronological audit ledger of stock movements for a specific inventory item")
    public ResponseEntity<PageResponse<StockMovementResponseDTO>> getItemMovements(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(inventoryService.getItemMovements(id, pageable));
    }
}
