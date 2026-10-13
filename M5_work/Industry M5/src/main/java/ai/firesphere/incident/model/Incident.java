package ai.firesphere.incident.model;

import ai.firesphere.common.audit.BaseAuditableEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "incidents", indexes = {
        @Index(name = "idx_incidents_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_incidents_tenant_created", columnList = "tenant_id, created_at"),
        @Index(name = "idx_incidents_number", columnList = "incident_number", unique = true)
})
public class Incident extends BaseAuditableEntity {

    @Column(name = "incident_number", nullable = false, unique = true, length = 32)
    private String incidentNumber;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", nullable = false, length = 50)
    private IncidentType incidentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 30)
    private IncidentSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private IncidentStatus status;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "address", nullable = false, length = 500)
    private String address;

    @Column(name = "caller_name", length = 100)
    private String callerName;

    @Column(name = "caller_phone", length = 30)
    private String callerPhone;

    @Column(name = "assigned_station_id")
    private UUID assignedStationId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "incident_assigned_resources", joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "resource_reference")
    private List<String> assignedResources = new ArrayList<>();

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "en_route_at")
    private Instant enRouteAt;

    @Column(name = "on_scene_at")
    private Instant onSceneAt;

    @Column(name = "under_control_at")
    private Instant underControlAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closure_remarks", length = 1000)
    private String closureRemarks;

    public Incident() {
        this.status = IncidentStatus.REPORTED;
        this.reportedAt = Instant.now();
    }

    public String getIncidentNumber() {
        return incidentNumber;
    }

    public void setIncidentNumber(String incidentNumber) {
        this.incidentNumber = incidentNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public IncidentType getIncidentType() {
        return incidentType;
    }

    public void setIncidentType(IncidentType incidentType) {
        this.incidentType = incidentType;
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(IncidentSeverity severity) {
        this.severity = severity;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCallerName() {
        return callerName;
    }

    public void setCallerName(String callerName) {
        this.callerName = callerName;
    }

    public String getCallerPhone() {
        return callerPhone;
    }

    public void setCallerPhone(String callerPhone) {
        this.callerPhone = callerPhone;
    }

    public UUID getAssignedStationId() {
        return assignedStationId;
    }

    public void setAssignedStationId(UUID assignedStationId) {
        this.assignedStationId = assignedStationId;
    }

    public List<String> getAssignedResources() {
        return assignedResources;
    }

    public void setAssignedResources(List<String> assignedResources) {
        this.assignedResources = assignedResources != null ? assignedResources : new ArrayList<>();
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public void setReportedAt(Instant reportedAt) {
        this.reportedAt = reportedAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public void setDispatchedAt(Instant dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }

    public Instant getEnRouteAt() {
        return enRouteAt;
    }

    public void setEnRouteAt(Instant enRouteAt) {
        this.enRouteAt = enRouteAt;
    }

    public Instant getOnSceneAt() {
        return onSceneAt;
    }

    public void setOnSceneAt(Instant onSceneAt) {
        this.onSceneAt = onSceneAt;
    }

    public Instant getUnderControlAt() {
        return underControlAt;
    }

    public void setUnderControlAt(Instant underControlAt) {
        this.underControlAt = underControlAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public String getClosureRemarks() {
        return closureRemarks;
    }

    public void setClosureRemarks(String closureRemarks) {
        this.closureRemarks = closureRemarks;
    }
}
