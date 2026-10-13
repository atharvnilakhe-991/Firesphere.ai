package ai.firesphere.inventory;

import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.inventory.dto.*;
import ai.firesphere.inventory.model.InventoryItem;
import ai.firesphere.inventory.model.StockMovement;
import ai.firesphere.inventory.model.StockMovementType;
import ai.firesphere.inventory.repository.InventoryItemRepository;
import ai.firesphere.inventory.repository.StockMovementRepository;
import ai.firesphere.inventory.service.InventoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class InventoryConcurrencyAndAtomicityTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryItemRepository itemRepository;

    @Autowired
    private StockMovementRepository movementRepository;

    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID stationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Concurrency: Simultaneous stock issues safely serialized without negative stock or lost updates")
    void testConcurrentStockIssues_PreventsNegativeStockAndLostUpdates() throws Exception {
        // 1. Register item with initial quantity = 10
        String sku = "FS-CONC-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        InventoryItemResponseDTO item = inventoryService.registerItem(new InventoryItemCreateRequest(
                sku, "Concurrent Test Hose", "HOSE_NOZZLE", stationId,
                10, 2, "METERS", "Initial 10 units"
        ));
        UUID itemId = item.id();

        // 2. Prepare 5 concurrent threads, each attempting to issue 3 units (total 15 requested, only 10 available)
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Simultaneous start
                    TenantContext.setTenantId(tenantId);
                    inventoryService.issueStock(itemId, new StockIssueRequest(
                            itemId, 3, null, "Concurrent Engine Crew", "Stress test issue"
                    ));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    TenantContext.clear();
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Release threads concurrently

        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // 3. Assertions on results:
        // Exactly 3 issues can succeed (3 * 3 = 9 units), remaining 2 must fail because 10 - 9 = 1 < 3
        assertEquals(3, successCount.get(), "Exactly 3 concurrent issues of 3 units should succeed from initial 10");
        assertEquals(2, failureCount.get(), "Exactly 2 concurrent issues must fail due to insufficient stock");

        // 4. Verify Database state:
        TenantContext.setTenantId(tenantId);
        InventoryItem freshItem = itemRepository.findByIdAndTenantId(itemId, tenantId).orElseThrow();
        assertEquals(1, freshItem.getQuantityOnHand(), "Remaining quantity must be exactly 10 - 9 = 1");
        assertTrue(freshItem.getQuantityOnHand() >= 0, "Quantity on hand must never be negative");

        // 5. Verify Ledger Movements:
        List<StockMovement> movements = movementRepository.findAllByItemIdAndTenantIdOrderByCreatedAtAsc(itemId, tenantId);
        // 1 initial restock + 3 successful issues = 4 movements
        assertEquals(4, movements.size(), "Ledger must contain 1 initial RESTOCK and exactly 3 ISSUE records");
        long issueMovements = movements.stream().filter(m -> m.getMovementType() == StockMovementType.ISSUE).count();
        assertEquals(3, issueMovements);
    }

    @Test
    @DisplayName("Inactive Item Rules: Inactive item cannot be issued or received, but can be returned")
    void testInactiveItemOperationalRules() {
        String sku = "FS-INACT-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        InventoryItemResponseDTO item = inventoryService.registerItem(new InventoryItemCreateRequest(
                sku, "Deactivated Breathing Cylinder", "BREATHING_AIR", stationId,
                5, 2, "CYLINDERS", "Deactivation test"
        ));
        UUID itemId = item.id();

        // Deactivate item
        inventoryService.updateItem(itemId, new InventoryItemUpdateRequest(null, null, null, null, null, null, null, false));

        // 1. Issue against inactive item is rejected
        assertThrows(RuntimeException.class, () -> inventoryService.issueStock(itemId,
                new StockIssueRequest(itemId, 1, null, "Crew", "Issue inactive")));

        // 2. Receive against inactive item is rejected
        assertThrows(RuntimeException.class, () -> inventoryService.receiveStock(itemId,
                new StockReceiveRequest(5, "Vendor", "Receive inactive", null)));

        // 3. Return from field is permitted (to recover damaged gear into store for scrapping)
        StockMovementResponseDTO returnResponse = inventoryService.returnStock(itemId,
                new StockReturnRequest(itemId, 2, null, "Field Crew", "Recovered damaged gear"));
        assertEquals(7, returnResponse.resultingQuantity());
    }

    @Test
    @DisplayName("Low Stock Evaluation: Correct boundary evaluation for >, ==, < threshold")
    void testLowStockThresholdBoundaries() {
        String sku = "FS-THRESH-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        // Threshold = 5
        InventoryItemResponseDTO item = inventoryService.registerItem(new InventoryItemCreateRequest(
                sku, "Threshold Foam Jug", "FOAM_AGENT", stationId,
                6, 5, "LITERS", "Threshold test"
        ));
        UUID itemId = item.id();

        // 1. Quantity (6) > Threshold (5) -> false
        assertFalse(inventoryService.getItem(itemId).lowStockAlert());

        // 2. Issue 1 -> Quantity (5) == Threshold (5) -> true
        inventoryService.issueStock(itemId, new StockIssueRequest(itemId, 1, null, "Field", "Issue to threshold"));
        assertTrue(inventoryService.getItem(itemId).lowStockAlert());

        // 3. Issue 1 -> Quantity (4) < Threshold (5) -> true
        inventoryService.issueStock(itemId, new StockIssueRequest(itemId, 1, null, "Field", "Issue below threshold"));
        assertTrue(inventoryService.getItem(itemId).lowStockAlert());
    }

    @Test
    @DisplayName("SKU Normalization: Case-insensitive duplicate rejection within tenant")
    void testSkuNormalizationAndUniqueness() {
        String baseSku = "fs-norm-" + UUID.randomUUID().toString().substring(0, 4);

        inventoryService.registerItem(new InventoryItemCreateRequest(
                baseSku.toLowerCase(), "Lowercase SKU Item", "PPE", stationId,
                1, 1, "UNITS", null
        ));

        // Attempting with uppercase version must throw duplicate conflict
        assertThrows(RuntimeException.class, () -> inventoryService.registerItem(new InventoryItemCreateRequest(
                baseSku.toUpperCase(), "Uppercase SKU Item", "PPE", stationId,
                1, 1, "UNITS", null
        )));
    }
}
