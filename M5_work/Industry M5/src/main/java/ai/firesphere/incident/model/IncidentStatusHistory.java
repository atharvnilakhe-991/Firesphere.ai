package ai.firesphere.incident.model;

import ai.firesphere.common.audit.BaseAuditableEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Auditable status transition history for emergency incidents.
 */
@Entity
@Table(name = "incident_status_history", indexes = {
        @Index(name = "idx_history_incident", columnList = "incident_id"),
        @Index(name = "idx_history_tenant_time", columnList = "tenant_id, transitioned_at")
})
public class IncidentStatusHistory extends BaseAuditableEntity {

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30)
    private IncidentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 30)
    private IncidentStatus newStatus;

    @Column(name = "transitioned_at", nullable = false)
    private Instant transitionedAt;

    @Column(name = "transitioned_by", nullable = false, length = 100)
    private String transitionedBy;

    @Column(name = "reason", length = 500)
    private String reason;

    public IncidentStatusHistory() {
        this.transitionedAt = Instant.now();
    }

    public IncidentStatusHistory(UUID incidentId, IncidentStatus previousStatus, IncidentStatus newStatus,
                                 String transitionedBy, String reason) {
        this.incidentId = incidentId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.transitionedBy = (transitionedBy != null && !transitionedBy.isBlank()) ? transitionedBy : "SYSTEM";
        this.reason = reason;
        this.transitionedAt = Instant.now();
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(UUID incidentId) {
        this.incidentId = incidentId;
    }

    public IncidentStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(IncidentStatus previousStatus) {
        this.previousStatus = previousStatus;
    }

    public IncidentStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(IncidentStatus newStatus) {
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
