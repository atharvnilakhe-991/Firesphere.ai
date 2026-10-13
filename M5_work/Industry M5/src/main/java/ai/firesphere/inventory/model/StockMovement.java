package ai.firesphere.inventory.model;

import ai.firesphere.common.audit.BaseAuditableEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "inventory_stock_movements", indexes = {
        @Index(name = "idx_movements_item", columnList = "item_id"),
        @Index(name = "idx_movements_incident", columnList = "incident_id"),
        @Index(name = "idx_movements_tenant_item", columnList = "tenant_id, item_id"),
        @Index(name = "idx_movements_tenant_time", columnList = "tenant_id, created_at")
})
public class StockMovement extends BaseAuditableEntity {

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private StockMovementType movementType;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "resulting_quantity")
    private Integer resultingQuantity;

    @Column(name = "incident_id")
    private UUID incidentId;

    @Column(name = "destination_or_source", length = 100)
    private String destinationOrSource;

    @Column(name = "remarks", length = 500)
    private String remarks;

    public StockMovement() {
    }

    public StockMovement(UUID itemId, StockMovementType movementType, Integer quantity, Integer resultingQuantity,
                         UUID incidentId, String destinationOrSource, String remarks) {
        this.itemId = itemId;
        this.movementType = movementType;
        this.quantity = quantity;
        this.resultingQuantity = resultingQuantity;
        this.incidentId = incidentId;
        this.destinationOrSource = destinationOrSource;
        this.remarks = remarks;
    }

    public UUID getItemId() {
        return itemId;
    }

    public void setItemId(UUID itemId) {
        this.itemId = itemId;
    }

    public StockMovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(StockMovementType movementType) {
        this.movementType = movementType;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getResultingQuantity() {
        return resultingQuantity;
    }

    public void setResultingQuantity(Integer resultingQuantity) {
        this.resultingQuantity = resultingQuantity;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(UUID incidentId) {
        this.incidentId = incidentId;
    }

    public String getDestinationOrSource() {
        return destinationOrSource;
    }

    public void setDestinationOrSource(String destinationOrSource) {
        this.destinationOrSource = destinationOrSource;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
