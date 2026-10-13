package com.firesphere.backend.inspection.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inspections", schema = "firesphere")
public class NocInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "noc_application_id")
    private Long nocApplicationId;

    @Column(name = "inspection_number")
    private String inspectionNumber;

    @Column(name = "building_id")
    private Long buildingId;

    @Column(name = "inspection_type")
    private String inspectionType = "ROUTINE";

    @Column(name = "inspection_status")
    private String inspectionStatus = "SCHEDULED";

    @Column(name = "overall_compliance_status")
    private String overallComplianceStatus;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "conducted_at")
    private OffsetDateTime conductedAt;

    @Column(name = "summary_report")
    private String summaryReport;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // Transient fields for existing controller/DTO compatibility
    @Transient
    private UUID applicationId;

    @Transient
    private UUID organizationId;

    @Column(name = "assigned_officer")
    private UUID assignedOfficer;

    @Transient
    private InspectionStatus status;

    @Transient
    private OffsetDateTime scheduledAt;

    @Transient
    private OffsetDateTime startedAt;

    @Transient
    private OffsetDateTime completedAt;

    @Transient
    private String remarks;

    @Transient
    private String location;

    @PrePersist
    protected void onCreate() {
        if (this.uuid == null) {
            this.uuid = UUID.randomUUID();
        }
        if (this.inspectionNumber == null) {
            this.inspectionNumber = "INSP-" + System.currentTimeMillis();
        }
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = this.createdAt;
        }
        if (this.scheduledDate == null && this.scheduledAt != null) {
            this.scheduledDate = this.scheduledAt.toLocalDate();
        }
        if (this.status != null) {
            this.inspectionStatus = this.status.name();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
        if (this.status != null) {
            this.inspectionStatus = this.status.name();
        }
        if (this.remarks != null) {
            this.summaryReport = this.remarks;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }

    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }

    public Long getNocApplicationId() { return nocApplicationId; }
    public void setNocApplicationId(Long nocApplicationId) { this.nocApplicationId = nocApplicationId; }

    public String getInspectionNumber() { return inspectionNumber; }
    public void setInspectionNumber(String inspectionNumber) { this.inspectionNumber = inspectionNumber; }

    public Long getBuildingId() { return buildingId; }
    public void setBuildingId(Long buildingId) { this.buildingId = buildingId; }

    public String getInspectionType() { return inspectionType; }
    public void setInspectionType(String inspectionType) { this.inspectionType = inspectionType; }

    public String getInspectionStatus() { return inspectionStatus; }
    public void setInspectionStatus(String inspectionStatus) { this.inspectionStatus = inspectionStatus; }

    public String getOverallComplianceStatus() { return overallComplianceStatus; }
    public void setOverallComplianceStatus(String overallComplianceStatus) { this.overallComplianceStatus = overallComplianceStatus; }

    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }

    public OffsetDateTime getConductedAt() { return conductedAt; }
    public void setConductedAt(OffsetDateTime conductedAt) { this.conductedAt = conductedAt; }

    public String getSummaryReport() { return summaryReport; }
    public void setSummaryReport(String summaryReport) { this.summaryReport = summaryReport; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }
    public void setApplicationId(Long applicationId) {
        this.nocApplicationId = applicationId;
        if (applicationId != null && this.applicationId == null) {
            this.applicationId = new UUID(0, applicationId);
        }
    }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public void setOrganizationId(Long organizationId) {
        this.orgId = organizationId;
        if (organizationId != null && this.organizationId == null) {
            this.organizationId = new UUID(0, organizationId);
        }
    }

    public UUID getAssignedOfficer() { return assignedOfficer; }
    public void setAssignedOfficer(UUID assignedOfficer) { this.assignedOfficer = assignedOfficer; }
    public void setAssignedOfficer(Long officerId) {
        if (officerId != null && this.assignedOfficer == null) {
            this.assignedOfficer = new UUID(0, officerId);
        }
    }

    public InspectionStatus getStatus() {
        if (this.status != null) {
            return this.status;
        }
        if (this.inspectionStatus != null) {
            try {
                return InspectionStatus.valueOf(this.inspectionStatus);
            } catch (Exception ignored) {
            }
        }
        return InspectionStatus.SCHEDULED;
    }

    public void setStatus(InspectionStatus status) {
        this.status = status;
        if (status != null) {
            this.inspectionStatus = status.name();
        }
    }

    public OffsetDateTime getScheduledAt() {
        if (this.scheduledAt != null) {
            return this.scheduledAt;
        }
        if (this.scheduledDate != null) {
            return this.scheduledDate.atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        }
        return null;
    }

    public void setScheduledAt(OffsetDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
        if (scheduledAt != null) {
            this.scheduledDate = scheduledAt.toLocalDate();
        }
    }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getCompletedAt() {
        return completedAt != null ? completedAt : conductedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
        this.conductedAt = completedAt;
    }

    public String getRemarks() {
        return remarks != null ? remarks : summaryReport;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
        this.summaryReport = remarks;
    }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
