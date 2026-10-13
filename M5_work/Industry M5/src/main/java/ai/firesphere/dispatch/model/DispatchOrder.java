package ai.firesphere.dispatch.model;

import ai.firesphere.common.audit.BaseAuditableEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "dispatch_orders", indexes = {
        @Index(name = "idx_dispatch_incident", columnList = "incident_id"),
        @Index(name = "idx_dispatch_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_dispatch_tenant_priority", columnList = "tenant_id, priority")
})
public class DispatchOrder extends BaseAuditableEntity {

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "station_id", nullable = false)
    private UUID stationId;

    @Column(name = "dispatch_code", nullable = false, unique = true, length = 32)
    private String dispatchCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DispatchStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 30)
    private DispatchPriority priority;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dispatch_assigned_vehicles", joinColumns = @JoinColumn(name = "dispatch_id"))
    @Column(name = "vehicle_id")
    private List<UUID> assignedVehicleIds = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dispatch_assigned_responders", joinColumns = @JoinColumn(name = "dispatch_id"))
    @Column(name = "responder_id")
    private List<UUID> assignedResponderIds = new ArrayList<>();

    @Column(name = "dispatched_at", nullable = false)
    private Instant dispatchedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "en_route_at")
    private Instant enRouteAt;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "operational_notes", length = 1000)
    private String operationalNotes;

    public DispatchOrder() {
        this.status = DispatchStatus.CREATED;
        this.priority = DispatchPriority.MEDIUM;
        this.dispatchedAt = Instant.now();
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(UUID incidentId) {
        this.incidentId = incidentId;
    }

    public UUID getStationId() {
        return stationId;
    }

    public void setStationId(UUID stationId) {
        this.stationId = stationId;
    }

    public String getDispatchCode() {
        return dispatchCode;
    }

    public void setDispatchCode(String dispatchCode) {
        this.dispatchCode = dispatchCode;
    }

    public DispatchStatus getStatus() {
        return status;
    }

    public void setStatus(DispatchStatus status) {
        this.status = status;
    }

    public DispatchPriority getPriority() {
        return priority;
    }

    public void setPriority(DispatchPriority priority) {
        this.priority = priority;
    }

    public List<UUID> getAssignedVehicleIds() {
        return assignedVehicleIds;
    }

    public void setAssignedVehicleIds(List<UUID> assignedVehicleIds) {
        this.assignedVehicleIds = assignedVehicleIds;
    }

    public List<UUID> getAssignedResponderIds() {
        return assignedResponderIds;
    }

    public void setAssignedResponderIds(List<UUID> assignedResponderIds) {
        this.assignedResponderIds = assignedResponderIds;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public void setDispatchedAt(Instant dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(Instant acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public Instant getEnRouteAt() {
        return enRouteAt;
    }

    public void setEnRouteAt(Instant enRouteAt) {
        this.enRouteAt = enRouteAt;
    }

    public Instant getArrivedAt() {
        return arrivedAt;
    }

    public void setArrivedAt(Instant arrivedAt) {
        this.arrivedAt = arrivedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public String getOperationalNotes() {
        return operationalNotes;
    }

    public void setOperationalNotes(String operationalNotes) {
        this.operationalNotes = operationalNotes;
    }
}
