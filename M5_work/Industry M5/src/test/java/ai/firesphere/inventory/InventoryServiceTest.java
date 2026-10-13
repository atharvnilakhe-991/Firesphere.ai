package ai.firesphere.inventory;

import ai.firesphere.common.exception.BusinessRuleViolationException;
import ai.firesphere.common.exception.ConflictException;
import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.inventory.dto.*;
import ai.firesphere.inventory.model.InventoryItem;
import ai.firesphere.inventory.model.StockMovement;
import ai.firesphere.inventory.model.StockMovementType;
import ai.firesphere.inventory.repository.InventoryItemRepository;
import ai.firesphere.inventory.repository.StockMovementRepository;
import ai.firesphere.inventory.service.InventoryServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID itemId = UUID.randomUUID();
    private final UUID stationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private InventoryItem createMockItem(int quantity, int threshold) {
        InventoryItem item = new InventoryItem();
        item.setId(itemId);
        item.setTenantId(tenantId);
        item.setSku("FS-PPE-HELM-01");
        item.setName("Structural Fire Helmet");
        item.setCategory("PPE");
        item.setStationId(stationId);
        item.setQuantityOnHand(quantity);
        item.setMinimumThreshold(threshold);
        item.setUnitOfMeasure("UNITS");
        item.setDescription("EN 443 certified fire helmet");
        item.setLocationInStore("LOCKER-B1");
        item.setIsActive(true);
        return item;
    }

    @Test
    @DisplayName("registerItem: registers new item and creates initial RESTOCK movement if initialQuantity > 0")
    void testRegisterItem_WithInitialQuantity_Success() {
        InventoryItemCreateRequest request = new InventoryItemCreateRequest(
                "fs-ppe-helm-01", "Structural Fire Helmet", "PPE", stationId,
                10, 3, "UNITS", "Initial helmets", "LOCKER-B1", "BATCH-2026-A"
        );

        when(inventoryItemRepository.existsBySkuAndTenantId("FS-PPE-HELM-01", tenantId)).thenReturn(false);
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> {
            InventoryItem i = inv.getArgument(0);
            i.setId(itemId);
            return i;
        });

        InventoryItemResponseDTO response = inventoryService.registerItem(request);

        assertNotNull(response);
        assertEquals("FS-PPE-HELM-01", response.sku());
        assertEquals(10, response.quantityOnHand());
        assertFalse(response.lowStockAlert());

        verify(stockMovementRepository, times(1)).save(any(StockMovement.class));
    }

    @Test
    @DisplayName("registerItem: duplicate SKU within same tenant throws ConflictException (409)")
    void testRegisterItem_DuplicateSku_ThrowsConflict() {
        InventoryItemCreateRequest request = new InventoryItemCreateRequest(
                "FS-PPE-HELM-01", "Structural Fire Helmet", "PPE", stationId,
                5, 2, "UNITS", null, null, null
        );

        when(inventoryItemRepository.existsBySkuAndTenantId("FS-PPE-HELM-01", tenantId)).thenReturn(true);

        assertThrows(ConflictException.class, () -> inventoryService.registerItem(request));
        verify(inventoryItemRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("getItem: retrieves item by ID for current tenant")
    void testGetItem_Success() {
        InventoryItem item = createMockItem(8, 3);
        when(inventoryItemRepository.findByIdAndTenantId(itemId, tenantId)).thenReturn(Optional.of(item));

        InventoryItemResponseDTO response = inventoryService.getItem(itemId);

        assertNotNull(response);
        assertEquals(itemId, response.id());
        assertEquals("FS-PPE-HELM-01", response.sku());
    }

    @Test
    @DisplayName("getItem: throws ResourceNotFoundException (404) when not found")
    void testGetItem_NotFound_Throws404() {
        when(inventoryItemRepository.findByIdAndTenantId(itemId, tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> inventoryService.getItem(itemId));
    }

    @Test
    @DisplayName("updateItem: updates mutable metadata without altering stock quantity or SKU")
    void testUpdateItem_MetadataSuccess() {
        InventoryItem item = createMockItem(15, 5);
        when(inventoryItemRepository.findByIdAndTenantId(itemId, tenantId)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryItemUpdateRequest updateRequest = new InventoryItemUpdateRequest(
                "Upgraded Helmet Pro", "PPE_ADVANCED", "Updated spec description",
                8, "SETS", "ROOM-4", "BATCH-99", false
        );

        InventoryItemResponseDTO response = inventoryService.updateItem(itemId, updateRequest);

        assertEquals("Upgraded Helmet Pro", response.name());
        assertEquals("PPE_ADVANCED", response.category());
        assertEquals(8, response.minimumThreshold());
        assertEquals("FS-PPE-HELM-01", response.sku()); // SKU untouched
        assertEquals(15, response.quantityOnHand()); // Quantity untouched
        assertFalse(response.isActive());
    }

    @Test
    @DisplayName("receiveStock: increases quantity and records RESTOCK movement")
    void testReceiveStock_Success() {
        InventoryItem item = createMockItem(10, 5);
        when(inventoryItemRepository.findByIdAndTenantIdForUpdate(itemId, tenantId)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(inv -> {
            StockMovement sm = inv.getArgument(0);
            sm.setId(UUID.randomUUID());
            return sm;
        });

        StockReceiveRequest request = new StockReceiveRequest(5, "Vendor shipment", "Weekly restock", "BATCH-2");
        StockMovementResponseDTO response = inventoryService.receiveStock(itemId, request);

        assertEquals(15, item.getQuantityOnHand());
        assertEquals(StockMovementType.RESTOCK, response.movementType());
        assertEquals(5, response.quantity());
        assertEquals(15, response.resultingQuantity());
    }

    @Test
    @DisplayName("receiveStock: rejects non-positive quantities with BusinessRuleViolationException")
    void testReceiveStock_InvalidQuantity_ThrowsException() {
        StockReceiveRequest zeroRequest = new StockReceiveRequest(0, "Vendor", "Invalid", null);
        assertThrows(BusinessRuleViolationException.class, () -> inventoryService.receiveStock(itemId, zeroRequest));
    }

    @Test
    @DisplayName("issueStock: decreases quantity and records ISSUE movement")
    void testIssueStock_Success() {
        InventoryItem item = createMockItem(10, 5);
        when(inventoryItemRepository.findByIdAndTenantIdForUpdate(itemId, tenantId)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(inv -> {
            StockMovement sm = inv.getArgument(0);
            sm.setId(UUID.randomUUID());
            return sm;
        });

        StockIssueRequest request = new StockIssueRequest(itemId, 4, UUID.randomUUID(), "Engine 1 Crew", "Incident deployment");
        StockMovementResponseDTO response = inventoryService.issueStock(itemId, request);

        assertEquals(6, item.getQuantityOnHand());
        assertEquals(StockMovementType.ISSUE, response.movementType());
        assertEquals(4, response.quantity());
        assertEquals(6, response.resultingQuantity());
    }

    @Test
    @DisplayName("issueStock: rejects issue when requested quantity exceeds quantity on hand (no negative stock)")
    void testIssueStock_InsufficientStock_Throws422() {
        InventoryItem item = createMockItem(3, 5);
        when(inventoryItemRepository.findByIdAndTenantIdForUpdate(itemId, tenantId)).thenReturn(Optional.of(item));

        StockIssueRequest request = new StockIssueRequest(itemId, 5, null, "Field", "Over-issue attempt");

        assertThrows(BusinessRuleViolationException.class, () -> inventoryService.issueStock(itemId, request));
        assertEquals(3, item.getQuantityOnHand()); // Unchanged
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("issueStock: rejects issue for inactive items with BusinessRuleViolationException")
    void testIssueStock_InactiveItem_Throws422() {
        InventoryItem item = createMockItem(10, 5);
        item.setIsActive(false);
        when(inventoryItemRepository.findByIdAndTenantIdForUpdate(itemId, tenantId)).thenReturn(Optional.of(item));

        StockIssueRequest request = new StockIssueRequest(itemId, 2, null, "Field", "Issue inactive");

        assertThrows(BusinessRuleViolationException.class, () -> inventoryService.issueStock(itemId, request));
    }

    @Test
    @DisplayName("returnStock: increases quantity and records RETURN movement")
    void testReturnStock_Success() {
        InventoryItem item = createMockItem(6, 5);
        when(inventoryItemRepository.findByIdAndTenantIdForUpdate(itemId, tenantId)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(inv -> {
            StockMovement sm = inv.getArgument(0);
            sm.setId(UUID.randomUUID());
            return sm;
        });

        StockReturnRequest request = new StockReturnRequest(itemId, 3, null, "Engine 1 Crew", "Returned after incident");
        StockMovementResponseDTO response = inventoryService.returnStock(itemId, request);

        assertEquals(9, item.getQuantityOnHand());
        assertEquals(StockMovementType.RETURN, response.movementType());
        assertEquals(3, response.quantity());
        assertEquals(9, response.resultingQuantity());
    }

    @Test
    @DisplayName("adjustStock: positive delta increases stock and creates INSPECTION_ADJUSTMENT movement")
    void testAdjustStock_PositiveDelta_Success() {
        InventoryItem item = createMockItem(10, 5);
        when(inventoryItemRepository.findByIdAndTenantIdForUpdate(itemId, tenantId)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(inv -> {
            StockMovement sm = inv.getArgument(0);
            sm.setId(UUID.randomUUID());
            return sm;
        });

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                2, StockMovementType.INSPECTION_ADJUSTMENT, "Cycle count surplus found", "Audited"
        );
        StockMovementResponseDTO response = inventoryService.adjustStock(itemId, request);

        assertEquals(12, item.getQuantityOnHand());
        assertEquals(StockMovementType.INSPECTION_ADJUSTMENT, response.movementType());
        assertEquals(2, response.quantity());
        assertEquals(12, response.resultingQuantity());
    }

    @Test
    @DisplayName("adjustStock: negative delta decreases stock and creates SCRAP movement")
    void testAdjustStock_NegativeDelta_Success() {
        InventoryItem item = createMockItem(10, 5);
        when(inventoryItemRepository.findByIdAndTenantIdForUpdate(itemId, tenantId)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(inv -> {
            StockMovement sm = inv.getArgument(0);
            sm.setId(UUID.randomUUID());
            return sm;
        });

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                -3, StockMovementType.SCRAP, "Damaged in heat test", "Condemned"
        );
        StockMovementResponseDTO response = inventoryService.adjustStock(itemId, request);

        assertEquals(7, item.getQuantityOnHand());
        assertEquals(StockMovementType.SCRAP, response.movementType());
        assertEquals(3, response.quantity());
        assertEquals(7, response.resultingQuantity());
    }

    @Test
    @DisplayName("adjustStock: rejects negative adjustment that causes negative stock")
    void testAdjustStock_CausesNegativeStock_Throws422() {
        InventoryItem item = createMockItem(2, 5);
        when(inventoryItemRepository.findByIdAndTenantIdForUpdate(itemId, tenantId)).thenReturn(Optional.of(item));

        StockAdjustmentRequest request = new StockAdjustmentRequest(-5, null, "Defective write-off", null);

        assertThrows(BusinessRuleViolationException.class, () -> inventoryService.adjustStock(itemId, request));
        assertEquals(2, item.getQuantityOnHand());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("adjustStock: rejects zero delta")
    void testAdjustStock_ZeroDelta_Throws422() {
        StockAdjustmentRequest request = new StockAdjustmentRequest(0, null, "No delta", null);
        assertThrows(BusinessRuleViolationException.class, () -> inventoryService.adjustStock(itemId, request));
    }
}
