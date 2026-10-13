package ai.firesphere.fleet.model;

import ai.firesphere.common.audit.BaseAuditableEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Auditable status transition history record for fleet apparatus.
 */
@Entity
@Table(name = "vehicle_status_history", indexes = {
        @Index(name = "idx_vehicle_history_vehicle", columnList = "vehicle_id"),
        @Index(name = "idx_vehicle_history_tenant_time", columnList = "tenant_id, transitioned_at")
})
public class VehicleStatusHistory extends BaseAuditableEntity {

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30)
    private VehicleStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 30)
    private VehicleStatus newStatus;

    @Column(name = "transitioned_at", nullable = false)
    private Instant transitionedAt;

    @Column(name = "transitioned_by", nullable = false, length = 100)
    private String transitionedBy;

    @Column(name = "reason", length = 500)
    private String reason;

    public VehicleStatusHistory() {
        this.transitionedAt = Instant.now();
    }

    public VehicleStatusHistory(UUID vehicleId, VehicleStatus previousStatus, VehicleStatus newStatus,
                                String transitionedBy, String reason) {
        this.vehicleId = vehicleId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.transitionedBy = (transitionedBy != null && !transitionedBy.isBlank()) ? transitionedBy : "FLEET_OFFICER";
        this.reason = reason;
        this.transitionedAt = Instant.now();
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(UUID vehicleId) {
        this.vehicleId = vehicleId;
    }

    public VehicleStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(VehicleStatus previousStatus) {
        this.previousStatus = previousStatus;
    }

    public VehicleStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(VehicleStatus newStatus) {
        this.newStatus = newStatus;
    }

    public Instant getTransitionedAt() {
        return transitionedAt;
    }

    public void setTransitionedAt(Instant transitionedAt) {
        this.transitionedAt = transitionedAt;
    }

    public String getTransitionedBy() {
        return transitionedBy;
    }

    public void setTransitionedBy(String transitionedBy) {
        this.transitionedBy = transitionedBy;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
