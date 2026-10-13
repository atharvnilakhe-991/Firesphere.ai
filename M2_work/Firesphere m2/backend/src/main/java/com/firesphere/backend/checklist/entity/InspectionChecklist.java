package com.firesphere.backend.checklist.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inspection_checklist_instances", schema = "firesphere",
        uniqueConstraints = {@jakarta.persistence.UniqueConstraint(name = "uq_checklist_inst_insp", columnNames = {"inspection_id"})})
public class InspectionChecklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "inspection_id", nullable = false)
    private Long inspectionId;

    @Column(name = "noc_application_id")
    private Long nocApplicationId;

    @Transient
    private UUID inspectionUuid;

    @Transient
    private UUID applicationUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ChecklistTemplate template;

    @Column(name = "template_version", nullable = false)
    private Integer templateVersion = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false)
    private ChecklistLifecycleStatus lifecycleStatus = ChecklistLifecycleStatus.DRAFT;

    @Column(name = "summary_notes", columnDefinition = "TEXT")
    private String summaryNotes;

    @Column(name = "completed_by_officer_id")
    private Long completedByOfficerId;

    @Transient
    private UUID completedByOfficerUuid;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "checklistInstance", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<InspectionChecklistResponse> responses = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (this.uuid == null) {
            this.uuid = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = this.createdAt;
        }
        if (this.lifecycleStatus == null) {
            this.lifecycleStatus = ChecklistLifecycleStatus.DRAFT;
        }
        if (this.templateVersion == null && this.template != null) {
            this.templateVersion = this.template.getVersion();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }

    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }

    public Long getInspectionId() { return inspectionId; }
    public void setInspectionId(Long inspectionId) { this.inspectionId = inspectionId; }

    public Long getNocApplicationId() { return nocApplicationId; }
    public void setNocApplicationId(Long nocApplicationId) { this.nocApplicationId = nocApplicationId; }

    public UUID getInspectionUuid() { return inspectionUuid; }
    public void setInspectionUuid(UUID inspectionUuid) { this.inspectionUuid = inspectionUuid; }

    public UUID getApplicationUuid() { return applicationUuid; }
    public void setApplicationUuid(UUID applicationUuid) { this.applicationUuid = applicationUuid; }

    public ChecklistTemplate getTemplate() { return template; }
    public void setTemplate(ChecklistTemplate template) {
        this.template = template;
        if (template != null && this.templateVersion == null) {
            this.templateVersion = template.getVersion();
        }
    }

    public Integer getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(Integer templateVersion) { this.templateVersion = templateVersion; }

    public ChecklistLifecycleStatus getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(ChecklistLifecycleStatus lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }

    public String getSummaryNotes() { return summaryNotes; }
    public void setSummaryNotes(String summaryNotes) { this.summaryNotes = summaryNotes; }

    public Long getCompletedByOfficerId() { return completedByOfficerId; }
    public void setCompletedByOfficerId(Long completedByOfficerId) { this.completedByOfficerId = completedByOfficerId; }

    public UUID getCompletedByOfficerUuid() { return completedByOfficerUuid; }
    public void setCompletedByOfficerUuid(UUID completedByOfficerUuid) { this.completedByOfficerUuid = completedByOfficerUuid; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<InspectionChecklistResponse> getResponses() { return responses; }
    public void setResponses(List<InspectionChecklistResponse> responses) { this.responses = responses; }

    public void addResponse(InspectionChecklistResponse response) {
        this.responses.add(response);
        response.setChecklistInstance(this);
    }
}
