package ai.firesphere.inventory.model;

import ai.firesphere.common.audit.BaseAuditableEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "inventory_items", indexes = {
        @Index(name = "idx_inventory_tenant_sku", columnList = "tenant_id, sku", unique = true),
        @Index(name = "idx_inventory_station", columnList = "tenant_id, station_id"),
        @Index(name = "idx_inventory_tenant_cat", columnList = "tenant_id, category"),
        @Index(name = "idx_inventory_tenant_active", columnList = "tenant_id, is_active")
})
public class InventoryItem extends BaseAuditableEntity {

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "category", nullable = false, length = 60)
    private String category; // e.g. PPE, FOAM_AGENT, HOSE_NOZZLE, MEDICAL, BREATHING_AIR, RESCUE_TOOL

    @Column(name = "station_id", nullable = false)
    private UUID stationId;

    @Column(name = "quantity_on_hand", nullable = false)
    private Integer quantityOnHand;

    @Column(name = "minimum_threshold", nullable = false)
    private Integer minimumThreshold;

    @Column(name = "unit_of_measure", nullable = false, length = 30)
    private String unitOfMeasure; // e.g. UNITS, LITERS, CYLINDERS, METERS, SETS

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "location_in_store", length = 100)
    private String locationInStore; // e.g. BAY-1/SHELF-A, LOCKER-3

    @Column(name = "batch_number", length = 60)
    private String batchNumber;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    public InventoryItem() {
        this.quantityOnHand = 0;
        this.minimumThreshold = 5;
        this.unitOfMeasure = "UNITS";
        this.isActive = true;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public UUID getStationId() {
        return stationId;
    }

    public void setStationId(UUID stationId) {
        this.stationId = stationId;
    }

    public Integer getQuantityOnHand() {
        return quantityOnHand;
    }

    public void setQuantityOnHand(Integer quantityOnHand) {
        this.quantityOnHand = quantityOnHand;
    }

    public Integer getMinimumThreshold() {
        return minimumThreshold;
    }

    public void setMinimumThreshold(Integer minimumThreshold) {
        this.minimumThreshold = minimumThreshold;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(String unitOfMeasure) {
        this.unitOfMeasure = unitOfMeasure;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocationInStore() {
        return locationInStore;
    }

    public void setLocationInStore(String locationInStore) {
        this.locationInStore = locationInStore;
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public void setBatchNumber(String batchNumber) {
        this.batchNumber = batchNumber;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(isActive);
    }

    public boolean isLowStock() {
        return quantityOnHand <= minimumThreshold;
    }
}
