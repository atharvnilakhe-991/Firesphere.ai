package ai.firesphere.inventory;

import ai.firesphere.common.dto.PageResponse;
import ai.firesphere.common.exception.BusinessRuleViolationException;
import ai.firesphere.common.exception.ConflictException;
import ai.firesphere.common.exception.GlobalExceptionHandler;
import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.inventory.controller.InventoryController;
import ai.firesphere.inventory.dto.*;
import ai.firesphere.inventory.model.StockMovementType;
import ai.firesphere.inventory.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = InventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryService inventoryService;

    private final UUID itemId = UUID.randomUUID();
    private final UUID stationId = UUID.randomUUID();

    private InventoryItemResponseDTO sampleItem() {
        return new InventoryItemResponseDTO(
                itemId, "FS-HOSE-65MM", "Attack Fire Hose 65mm", "HOSE_NOZZLE",
                stationId, 25, 10, "METERS", "High pressure 40 bar hose", "RACK-1",
                "BATCH-01", true, false, Instant.now(), Instant.now()
        );
    }

    private StockMovementResponseDTO sampleMovement(StockMovementType type, int qty, int resultingQty) {
        return new StockMovementResponseDTO(
                UUID.randomUUID(), itemId, type, qty, resultingQty,
                null, "TEST_PARTY", "Test movement remarks", "OFFICER_A", Instant.now()
        );
    }

    @Test
    @DisplayName("POST /api/v1/inventory/items returns 201 Created")
    void registerItem_Valid_Returns201() throws Exception {
        InventoryItemCreateRequest request = new InventoryItemCreateRequest(
                "FS-HOSE-65MM", "Attack Fire Hose 65mm", "HOSE_NOZZLE",
                stationId, 25, 10, "METERS", null, null, null
        );

        when(inventoryService.registerItem(any())).thenReturn(sampleItem());

        mockMvc.perform(post("/api/v1/inventory/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(itemId.toString()))
                .andExpect(jsonPath("$.sku").value("FS-HOSE-65MM"))
                .andExpect(jsonPath("$.quantityOnHand").value(25));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/items returns 400 when missing mandatory fields")
    void registerItem_MissingFields_Returns400() throws Exception {
        String invalidJson = """
                {
                    "name": "Missing SKU item"
                }
                """;

        mockMvc.perform(post("/api/v1/inventory/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/items returns 409 on duplicate SKU")
    void registerItem_Duplicate_Returns409() throws Exception {
        InventoryItemCreateRequest request = new InventoryItemCreateRequest(
                "FS-HOSE-65MM", "Attack Fire Hose 65mm", "HOSE_NOZZLE",
                stationId, 25, 10, "METERS", null, null, null
        );

        when(inventoryService.registerItem(any()))
                .thenThrow(new ConflictException("Inventory item with SKU already exists: FS-HOSE-65MM"));

        mockMvc.perform(post("/api/v1/inventory/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STATE_CONFLICT"));
    }

    @Test
    @DisplayName("GET /api/v1/inventory/items/{id} returns 200 OK")
    void getItemById_Exists_Returns200() throws Exception {
        when(inventoryService.getItem(itemId)).thenReturn(sampleItem());

        mockMvc.perform(get("/api/v1/inventory/items/{id}", itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itemId.toString()))
                .andExpect(jsonPath("$.sku").value("FS-HOSE-65MM"));
    }

    @Test
    @DisplayName("GET /api/v1/inventory/items/{id} returns 404 Not Found")
    void getItemById_NotFound_Returns404() throws Exception {
        when(inventoryService.getItem(itemId))
                .thenThrow(new ResourceNotFoundException("Inventory item not found: " + itemId));

        mockMvc.perform(get("/api/v1/inventory/items/{id}", itemId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/inventory/items returns 200 with PageResponse")
    void listItems_Returns200() throws Exception {
        PageResponse<InventoryItemResponseDTO> page = new PageResponse<>(
                List.of(sampleItem()), 0, 20, 1, 1, true
        );
        when(inventoryService.getItems(any(), any(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/inventory/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/inventory/items/low-stock returns 200")
    void listLowStockItems_Returns200() throws Exception {
        PageResponse<InventoryItemResponseDTO> page = new PageResponse<>(
                List.of(sampleItem()), 0, 20, 1, 1, true
        );
        when(inventoryService.getLowStockItems(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/inventory/items/low-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/inventory/items/{id} returns 200 OK on metadata update")
    void updateItem_Returns200() throws Exception {
        InventoryItemUpdateRequest updateRequest = new InventoryItemUpdateRequest(
                "Upgraded Hose", null, "New spec", 15, null, "RACK-2", null, true
        );
        when(inventoryService.updateItem(eq(itemId), any())).thenReturn(sampleItem());

        mockMvc.perform(put("/api/v1/inventory/items/{id}", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/inventory/items/{id}/receive returns 200 OK")
    void receiveStock_Returns200() throws Exception {
        StockReceiveRequest request = new StockReceiveRequest(10, "Supplier", "Delivery", "BATCH-9");
        when(inventoryService.receiveStock(eq(itemId), any()))
                .thenReturn(sampleMovement(StockMovementType.RESTOCK, 10, 35));

        mockMvc.perform(post("/api/v1/inventory/items/{id}/receive", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movementType").value("RESTOCK"))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.resultingQuantity").value(35));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/items/{id}/issue returns 200 on valid issue")
    void issueStock_Valid_Returns200() throws Exception {
        StockIssueRequest request = new StockIssueRequest(itemId, 5, null, "Field Engine", "Emergency call");
        when(inventoryService.issueStock(eq(itemId), any()))
                .thenReturn(sampleMovement(StockMovementType.ISSUE, 5, 20));

        mockMvc.perform(post("/api/v1/inventory/items/{id}/issue", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movementType").value("ISSUE"))
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.resultingQuantity").value(20));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/items/{id}/issue returns 422 on insufficient stock")
    void issueStock_Insufficient_Returns422() throws Exception {
        StockIssueRequest request = new StockIssueRequest(itemId, 50, null, "Field Engine", "Excessive issue");
        when(inventoryService.issueStock(eq(itemId), any()))
                .thenThrow(new BusinessRuleViolationException("Insufficient stock on hand"));

        mockMvc.perform(post("/api/v1/inventory/items/{id}/issue", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/items/{id}/return returns 200 OK")
    void returnStock_Returns200() throws Exception {
        StockReturnRequest request = new StockReturnRequest(itemId, 2, null, "Field Engine", "Returned unused");
        when(inventoryService.returnStock(eq(itemId), any()))
                .thenReturn(sampleMovement(StockMovementType.RETURN, 2, 27));

        mockMvc.perform(post("/api/v1/inventory/items/{id}/return", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movementType").value("RETURN"))
                .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/items/{id}/adjust returns 200 OK")
    void adjustStock_Returns200() throws Exception {
        StockAdjustmentRequest request = new StockAdjustmentRequest(-2, StockMovementType.SCRAP, "Damaged in field", "Scrapped");
        when(inventoryService.adjustStock(eq(itemId), any()))
                .thenReturn(sampleMovement(StockMovementType.SCRAP, 2, 23));

        mockMvc.perform(post("/api/v1/inventory/items/{id}/adjust", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movementType").value("SCRAP"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.resultingQuantity").value(23));
    }

    @Test
    @DisplayName("GET /api/v1/inventory/items/{id}/movements returns 200 PageResponse")
    void getItemMovements_Returns200() throws Exception {
        PageResponse<StockMovementResponseDTO> page = new PageResponse<>(
                List.of(sampleMovement(StockMovementType.RESTOCK, 25, 25)), 0, 20, 1, 1, true
        );
        when(inventoryService.getItemMovements(eq(itemId), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/inventory/items/{id}/movements", itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }
}
