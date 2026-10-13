package ai.firesphere.dispatch.model;

import ai.firesphere.common.audit.BaseAuditableEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Auditable status transition history record for dispatch orders.
 */
@Entity
@Table(name = "dispatch_status_history", indexes = {
        @Index(name = "idx_dispatch_history_order", columnList = "dispatch_order_id"),
        @Index(name = "idx_dispatch_history_tenant_time", columnList = "tenant_id, transitioned_at")
})
public class DispatchStatusHistory extends BaseAuditableEntity {

    @Column(name = "dispatch_order_id", nullable = false)
    private UUID dispatchOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30)
    private DispatchStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 30)
    private DispatchStatus newStatus;

    @Column(name = "transitioned_at", nullable = false)
    private Instant transitionedAt;

    @Column(name = "transitioned_by", nullable = false, length = 100)
    private String transitionedBy;

    @Column(name = "reason", length = 500)
    private String reason;

    public DispatchStatusHistory() {
        this.transitionedAt = Instant.now();
    }

    public DispatchStatusHistory(UUID dispatchOrderId, DispatchStatus previousStatus, DispatchStatus newStatus,
                                 String transitionedBy, String reason) {
        this.dispatchOrderId = dispatchOrderId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.transitionedBy = (transitionedBy != null && !transitionedBy.isBlank()) ? transitionedBy : "DISPATCHER";
        this.reason = reason;
        this.transitionedAt = Instant.now();
    }

    public UUID getDispatchOrderId() {
        return dispatchOrderId;
    }

    public void setDispatchOrderId(UUID dispatchOrderId) {
        this.dispatchOrderId = dispatchOrderId;
    }

    public DispatchStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(DispatchStatus previousStatus) {
        this.previousStatus = previousStatus;
    }

    public DispatchStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(DispatchStatus newStatus) {
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
