package ai.firesphere.inventory.service;

import ai.firesphere.common.dto.PageResponse;
import ai.firesphere.common.exception.BusinessRuleViolationException;
import ai.firesphere.common.exception.ConflictException;
import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.inventory.dto.*;
import ai.firesphere.inventory.model.InventoryItem;
import ai.firesphere.inventory.model.StockMovement;
import ai.firesphere.inventory.model.StockMovementType;
import ai.firesphere.inventory.repository.InventoryItemRepository;
import ai.firesphere.inventory.repository.InventorySpecification;
import ai.firesphere.inventory.repository.StockMovementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceImpl.class);

    private final InventoryItemRepository inventoryItemRepository;
    private final StockMovementRepository stockMovementRepository;

    public InventoryServiceImpl(InventoryItemRepository inventoryItemRepository,
                                StockMovementRepository stockMovementRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    @Override
    @Transactional
    public InventoryItemResponseDTO registerItem(InventoryItemCreateRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        String normalizedSku = request.sku().trim().toUpperCase();

        if (inventoryItemRepository.existsBySkuAndTenantId(normalizedSku, tenantId)) {
            throw new ConflictException("Inventory item with SKU already exists: " + normalizedSku);
        }

        InventoryItem item = new InventoryItem();
        item.setTenantId(tenantId);
        item.setSku(normalizedSku);
        item.setName(request.name().trim());
        item.setCategory(request.category().trim().toUpperCase());
        item.setStationId(request.stationId());
        item.setQuantityOnHand(request.initialQuantity() != null ? request.initialQuantity() : 0);
        item.setMinimumThreshold(request.minimumThreshold() != null ? request.minimumThreshold() : 5);
        item.setUnitOfMeasure(request.unitOfMeasure() != null ? request.unitOfMeasure().trim().toUpperCase() : "UNITS");
        item.setDescription(request.description());
        item.setLocationInStore(request.locationInStore());
        item.setBatchNumber(request.batchNumber());
        item.setIsActive(true);

        InventoryItem saved = inventoryItemRepository.save(item);
        log.info("Registered inventory item SKU {} for tenant {}", saved.getSku(), tenantId);

        // Record initial intake movement if stock > 0
        if (saved.getQuantityOnHand() > 0) {
            StockMovement initialMovement = new StockMovement(
                    saved.getId(),
                    StockMovementType.RESTOCK,
                    saved.getQuantityOnHand(),
                    saved.getQuantityOnHand(),
                    null,
                    "INITIAL_INTAKE",
                    "Initial stock intake on item registration"
            );
            initialMovement.setTenantId(tenantId);
            stockMovementRepository.save(initialMovement);
        }

        return InventoryItemResponseDTO.fromEntity(saved);
    }

    @Override
    public InventoryItemResponseDTO getItem(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        InventoryItem item = inventoryItemRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found: " + id));
        return InventoryItemResponseDTO.fromEntity(item);
    }

    @Override
    public PageResponse<InventoryItemResponseDTO> getItems(
            String category,
            UUID stationId,
            Boolean isActive,
            Boolean lowStock,
            String search,
            Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();
        Specification<InventoryItem> spec = InventorySpecification.withFilters(
                tenantId, category, stationId, isActive, lowStock, search);
        Page<InventoryItem> page = inventoryItemRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(InventoryItemResponseDTO::fromEntity));
    }

    @Override
    public PageResponse<InventoryItemResponseDTO> getItems(Pageable pageable) {
        return getItems(null, null, null, null, null, pageable);
    }

    @Override
    public PageResponse<InventoryItemResponseDTO> getLowStockItems(Pageable pageable) {
        return getItems(null, null, null, true, null, pageable);
    }

    @Override
    public List<InventoryItemResponseDTO> getItemsByStation(UUID stationId) {
        UUID tenantId = TenantContext.getTenantId();
        return inventoryItemRepository.findAllByTenantIdAndStationId(tenantId, stationId).stream()
                .map(InventoryItemResponseDTO::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public InventoryItemResponseDTO updateItem(UUID id, InventoryItemUpdateRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        InventoryItem item = inventoryItemRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found: " + id));

        if (request.name() != null && !request.name().isBlank()) {
            item.setName(request.name().trim());
        }
        if (request.category() != null && !request.category().isBlank()) {
            item.setCategory(request.category().trim().toUpperCase());
        }
        if (request.description() != null) {
            item.setDescription(request.description().trim());
        }
        if (request.minimumThreshold() != null) {
            item.setMinimumThreshold(request.minimumThreshold());
        }
        if (request.unitOfMeasure() != null && !request.unitOfMeasure().isBlank()) {
            item.setUnitOfMeasure(request.unitOfMeasure().trim().toUpperCase());
        }
        if (request.locationInStore() != null) {
            item.setLocationInStore(request.locationInStore().trim());
        }
        if (request.batchNumber() != null) {
            item.setBatchNumber(request.batchNumber().trim());
        }
        if (request.isActive() != null) {
            item.setIsActive(request.isActive());
        }

        InventoryItem updated = inventoryItemRepository.save(item);
        log.info("Updated inventory metadata for SKU {} [Tenant: {}]", updated.getSku(), tenantId);
        return InventoryItemResponseDTO.fromEntity(updated);
    }

    @Override
    @Transactional
    public StockMovementResponseDTO receiveStock(UUID id, StockReceiveRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new BusinessRuleViolationException("Receive quantity must be strictly greater than 0");
        }

        InventoryItem item = inventoryItemRepository.findByIdAndTenantIdForUpdate(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found: " + id));

        if (!item.isActive()) {
            throw new BusinessRuleViolationException("Cannot receive stock for inactive inventory item SKU: " + item.getSku());
        }

        int newQuantity = item.getQuantityOnHand() + request.quantity();
        item.setQuantityOnHand(newQuantity);
        if (request.batchNumber() != null && !request.batchNumber().isBlank()) {
            item.setBatchNumber(request.batchNumber().trim());
        }
        inventoryItemRepository.save(item);

        StockMovement movement = new StockMovement(
                item.getId(),
                StockMovementType.RESTOCK,
                request.quantity(),
                newQuantity,
                null,
                request.source() != null ? request.source().trim() : "VENDOR_DELIVERY",
                request.remarks()
        );
        movement.setTenantId(tenantId);
        StockMovement savedMovement = stockMovementRepository.save(movement);

        log.info("Received {} units for SKU {}. New quantity on hand: {} [Tenant: {}]",
                request.quantity(), item.getSku(), newQuantity, tenantId);
        return StockMovementResponseDTO.fromEntity(savedMovement);
    }

    @Override
    @Transactional
    public StockMovementResponseDTO issueStock(UUID id, StockIssueRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new BusinessRuleViolationException("Issue quantity must be strictly greater than 0");
        }

        InventoryItem item = inventoryItemRepository.findByIdAndTenantIdForUpdate(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found: " + id));

        if (!item.isActive()) {
            throw new BusinessRuleViolationException("Cannot issue inactive inventory item SKU: " + item.getSku());
        }

        if (item.getQuantityOnHand() < request.quantity()) {
            throw new BusinessRuleViolationException("Insufficient stock on hand for SKU " + item.getSku()
                    + ": available " + item.getQuantityOnHand() + ", requested " + request.quantity());
        }

        int newQuantity = item.getQuantityOnHand() - request.quantity();
        item.setQuantityOnHand(newQuantity);
        inventoryItemRepository.save(item);

        StockMovement movement = new StockMovement(
                item.getId(),
                StockMovementType.ISSUE,
                request.quantity(),
                newQuantity,
                request.incidentId(),
                request.destination() != null ? request.destination().trim() : "FIELD_DEPLOYMENT",
                request.remarks()
        );
        movement.setTenantId(tenantId);
        StockMovement savedMovement = stockMovementRepository.save(movement);

        log.info("Issued {} units for SKU {}. New quantity on hand: {} [Tenant: {}]",
                request.quantity(), item.getSku(), newQuantity, tenantId);
        return StockMovementResponseDTO.fromEntity(savedMovement);
    }

    @Override
    @Transactional
    public StockMovementResponseDTO issueStock(StockIssueRequest request) {
        if (request.itemId() == null) {
            throw new BusinessRuleViolationException("Item ID is required for stock issue");
        }
        return issueStock(request.itemId(), request);
    }

    @Override
    @Transactional
    public StockMovementResponseDTO returnStock(UUID id, StockReturnRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new BusinessRuleViolationException("Return quantity must be strictly greater than 0");
        }

        InventoryItem item = inventoryItemRepository.findByIdAndTenantIdForUpdate(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found: " + id));

        int newQuantity = item.getQuantityOnHand() + request.quantity();
        item.setQuantityOnHand(newQuantity);
        inventoryItemRepository.save(item);

        StockMovement movement = new StockMovement(
                item.getId(),
                StockMovementType.RETURN,
                request.quantity(),
                newQuantity,
                request.incidentId(),
                request.source() != null ? request.source().trim() : "FIELD_RETURN",
                request.remarks()
        );
        movement.setTenantId(tenantId);
        StockMovement savedMovement = stockMovementRepository.save(movement);

        log.info("Returned {} units for SKU {}. New quantity on hand: {} [Tenant: {}]",
                request.quantity(), item.getSku(), newQuantity, tenantId);
        return StockMovementResponseDTO.fromEntity(savedMovement);
    }

    @Override
    @Transactional
    public StockMovementResponseDTO returnStock(StockReturnRequest request) {
        if (request.itemId() == null) {
            throw new BusinessRuleViolationException("Item ID is required for stock return");
        }
        return returnStock(request.itemId(), request);
    }

    @Override
    @Transactional
    public StockMovementResponseDTO adjustStock(UUID id, StockAdjustmentRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        if (request.quantityDelta() == null || request.quantityDelta() == 0) {
            throw new BusinessRuleViolationException("Stock adjustment delta cannot be zero");
        }

        InventoryItem item = inventoryItemRepository.findByIdAndTenantIdForUpdate(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found: " + id));

        int currentQuantity = item.getQuantityOnHand();
        int newQuantity = currentQuantity + request.quantityDelta();

        if (newQuantity < 0) {
            throw new BusinessRuleViolationException("Stock adjustment would result in negative quantity for SKU "
                    + item.getSku() + ": current " + currentQuantity + ", delta " + request.quantityDelta());
        }

        item.setQuantityOnHand(newQuantity);
        inventoryItemRepository.save(item);

        StockMovementType resolvedType = request.movementType() != null
                ? request.movementType()
                : (request.quantityDelta() < 0 ? StockMovementType.SCRAP : StockMovementType.INSPECTION_ADJUSTMENT);

        StockMovement movement = new StockMovement(
                item.getId(),
                resolvedType,
                Math.abs(request.quantityDelta()),
                newQuantity,
                null,
                "STOCK_ADJUSTMENT",
                "[" + request.reason().trim() + "] " + (request.remarks() != null ? request.remarks().trim() : "")
        );
        movement.setTenantId(tenantId);
        StockMovement savedMovement = stockMovementRepository.save(movement);

        log.info("Adjusted SKU {} by delta {}. New quantity on hand: {} [Tenant: {}]",
                item.getSku(), request.quantityDelta(), newQuantity, tenantId);
        return StockMovementResponseDTO.fromEntity(savedMovement);
    }

    @Override
    public PageResponse<StockMovementResponseDTO> getItemMovements(UUID itemId, Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();
        if (!inventoryItemRepository.existsByIdAndTenantId(itemId, tenantId)) {
            throw new ResourceNotFoundException("Inventory item not found: " + itemId);
        }

        Page<StockMovement> page = stockMovementRepository.findAllByItemIdAndTenantIdOrderByCreatedAtDesc(
                itemId, tenantId, pageable);
        return PageResponse.from(page.map(StockMovementResponseDTO::fromEntity));
    }
}
