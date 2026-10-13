package ai.firesphere.inventory;

import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.inventory.dto.*;
import ai.firesphere.inventory.model.InventoryItem;
import ai.firesphere.inventory.model.StockMovementType;
import ai.firesphere.inventory.repository.InventoryItemRepository;
import ai.firesphere.inventory.repository.StockMovementRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InventoryItemRepository itemRepository;

    @Autowired
    private StockMovementRepository movementRepository;

    private final UUID tenantA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID tenantB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID stationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantA);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("End-to-End: Register, Receive, Issue, Return, Adjust, Audit Movements & Low-Stock")
    void testFullInventoryLifecycleAndAuditLedger() throws Exception {
        String sku = "FS-NOZ-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        // 1. Register item with initialQuantity = 20
        InventoryItemCreateRequest createRequest = new InventoryItemCreateRequest(
                sku, "Pistol Grip Fire Nozzle", "HOSE_NOZZLE", stationId,
                20, 10, "UNITS", "Selectable gallonage nozzle 475 LPM", "BIN-4", "BATCH-2026-N1"
        );

        String createJson = mockMvc.perform(post("/api/v1/inventory/items")
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.sku").value(sku))
                .andExpect(jsonPath("$.quantityOnHand").value(20))
                .andExpect(jsonPath("$.lowStockAlert").value(false))
                .andReturn().getResponse().getContentAsString();

        UUID itemId = UUID.fromString(objectMapper.readTree(createJson).get("id").asText());

        // 2. Receive stock (+10) -> 30
        StockReceiveRequest receiveReq = new StockReceiveRequest(10, "Supply Depot", "Scheduled restock", "BATCH-2026-N2");
        mockMvc.perform(post("/api/v1/inventory/items/{id}/receive", itemId)
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(receiveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movementType").value("RESTOCK"))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.resultingQuantity").value(30));

        // 3. Issue stock (-15) -> 15
        UUID incidentId = UUID.randomUUID();
        StockIssueRequest issueReq = new StockIssueRequest(itemId, 15, incidentId, "Engine 10", "Dispatched to structural fire");
        mockMvc.perform(post("/api/v1/inventory/items/{id}/issue", itemId)
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(issueReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movementType").value("ISSUE"))
                .andExpect(jsonPath("$.quantity").value(15))
                .andExpect(jsonPath("$.resultingQuantity").value(15));

        // 4. Return stock (+5) -> 20
        StockReturnRequest returnReq = new StockReturnRequest(itemId, 5, incidentId, "Engine 10", "Returned unburned equipment");
        mockMvc.perform(post("/api/v1/inventory/items/{id}/return", itemId)
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movementType").value("RETURN"))
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.resultingQuantity").value(20));

        // 5. Adjust stock (-12) -> 8 (enters low-stock <= 10)
        StockAdjustmentRequest adjustReq = new StockAdjustmentRequest(-12, StockMovementType.SCRAP, "Hydraulic pressure failure write-off", "Scrapped");
        mockMvc.perform(post("/api/v1/inventory/items/{id}/adjust", itemId)
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adjustReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movementType").value("SCRAP"))
                .andExpect(jsonPath("$.quantity").value(12))
                .andExpect(jsonPath("$.resultingQuantity").value(8));

        // 6. Verify final item state & low stock trigger
        mockMvc.perform(get("/api/v1/inventory/items/{id}", itemId)
                        .header("X-Tenant-ID", tenantA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityOnHand").value(8))
                .andExpect(jsonPath("$.lowStockAlert").value(true));

        // 7. Verify low-stock query endpoint returns this item
        mockMvc.perform(get("/api/v1/inventory/items/low-stock")
                        .header("X-Tenant-ID", tenantA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + itemId + "')].lowStockAlert").value(true));

        // 8. Verify Movement History ledger (1 initial + 1 receive + 1 issue + 1 return + 1 adjust = 5 movements)
        mockMvc.perform(get("/api/v1/inventory/items/{id}/movements", itemId)
                        .header("X-Tenant-ID", tenantA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    @DisplayName("Stock Invariants: Reject insufficient stock issue and negative adjustment")
    void testStockInvariants_NoNegativeStock() throws Exception {
        String sku = "FS-AXE-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        InventoryItemCreateRequest createRequest = new InventoryItemCreateRequest(
                sku, "Fire Pick Head Axe", "RESCUE_TOOL", stationId,
                5, 2, "UNITS", null, null, null
        );

        String createJson = mockMvc.perform(post("/api/v1/inventory/items")
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID itemId = UUID.fromString(objectMapper.readTree(createJson).get("id").asText());

        // 1. Issue exceeding quantityOnHand (request 10, have 5) -> 422
        StockIssueRequest overIssue = new StockIssueRequest(itemId, 10, null, "Field", "Exceeding available");
        mockMvc.perform(post("/api/v1/inventory/items/{id}/issue", itemId)
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overIssue)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));

        // 2. Adjustment causing negative quantity (delta -10, have 5) -> 422
        StockAdjustmentRequest negAdjust = new StockAdjustmentRequest(-10, null, "Cycle count error", null);
        mockMvc.perform(post("/api/v1/inventory/items/{id}/adjust", itemId)
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negAdjust)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));

        // 3. Verify stock quantity remains exactly 5
        InventoryItem item = itemRepository.findByIdAndTenantId(itemId, tenantA).orElseThrow();
        assertEquals(5, item.getQuantityOnHand());
    }

    @Test
    @DisplayName("Duplicate SKU Protection: Rejects duplicate SKU within same tenant (409)")
    void testDuplicateSku_ThrowsConflict() throws Exception {
        String sku = "FS-FOAM-AFFF";

        InventoryItemCreateRequest req1 = new InventoryItemCreateRequest(
                sku, "AFFF Foam Concentrate 3%", "FOAM_AGENT", stationId,
                200, 50, "LITERS", null, null, null
        );
        mockMvc.perform(post("/api/v1/inventory/items")
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // Second registration with same SKU in Tenant A -> 409 Conflict
        InventoryItemCreateRequest req2 = new InventoryItemCreateRequest(
                sku, "Duplicate Foam", "FOAM_AGENT", stationId,
                100, 20, "LITERS", null, null, null
        );
        mockMvc.perform(post("/api/v1/inventory/items")
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STATE_CONFLICT"));
    }

    @Test
    @DisplayName("Tenant Isolation: Tenant B cannot access or mutate Tenant A inventory")
    void testTenantIsolation() throws Exception {
        String sku = "FS-SCBA-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        InventoryItemCreateRequest createRequest = new InventoryItemCreateRequest(
                sku, "Self-Contained Breathing Apparatus", "BREATHING_AIR", stationId,
                10, 3, "UNITS", null, null, null
        );
        String createJson = mockMvc.perform(post("/api/v1/inventory/items")
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID itemId = UUID.fromString(objectMapper.readTree(createJson).get("id").asText());

        // Tenant B cannot retrieve -> 404
        mockMvc.perform(get("/api/v1/inventory/items/{id}", itemId)
                        .header("X-Tenant-ID", tenantB.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // Tenant B cannot update metadata -> 404
        mockMvc.perform(put("/api/v1/inventory/items/{id}", itemId)
                        .header("X-Tenant-ID", tenantB.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InventoryItemUpdateRequest("Hijack", null, null, null, null, null, null, null))))
                .andExpect(status().isNotFound());

        // Tenant B cannot receive stock -> 404
        mockMvc.perform(post("/api/v1/inventory/items/{id}/receive", itemId)
                        .header("X-Tenant-ID", tenantB.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StockReceiveRequest(5, "Vendor", "Delivery", null))))
                .andExpect(status().isNotFound());

        // Tenant B cannot issue stock -> 404
        mockMvc.perform(post("/api/v1/inventory/items/{id}/issue", itemId)
                        .header("X-Tenant-ID", tenantB.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StockIssueRequest(itemId, 2, null, "Field", "Theft"))))
                .andExpect(status().isNotFound());

        // Tenant B cannot view movements -> 404
        mockMvc.perform(get("/api/v1/inventory/items/{id}/movements", itemId)
                        .header("X-Tenant-ID", tenantB.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Inactive Item: Deactivated inventory item cannot be issued")
    void testInactiveItem_CannotBeIssued() throws Exception {
        String sku = "FS-DEACT-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        InventoryItemCreateRequest createRequest = new InventoryItemCreateRequest(
                sku, "Decommissioned Ladder", "RESCUE_TOOL", stationId,
                4, 1, "UNITS", null, null, null
        );
        String createJson = mockMvc.perform(post("/api/v1/inventory/items")
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID itemId = UUID.fromString(objectMapper.readTree(createJson).get("id").asText());

        // Deactivate item
        InventoryItemUpdateRequest deactReq = new InventoryItemUpdateRequest(null, null, "Damaged rungs", null, null, null, null, false);
        mockMvc.perform(put("/api/v1/inventory/items/{id}", itemId)
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));

        // Attempt issue -> 422
        StockIssueRequest issueReq = new StockIssueRequest(itemId, 1, null, "Field", "Trying to issue inactive");
        mockMvc.perform(post("/api/v1/inventory/items/{id}/issue", itemId)
                        .header("X-Tenant-ID", tenantA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(issueReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }
}
