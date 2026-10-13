package com.firesphere.backend.checklist.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Table(name = "inspection_checklist_responses", schema = "firesphere",
        uniqueConstraints = {@jakarta.persistence.UniqueConstraint(name = "uq_checklist_resp_item", columnNames = {"checklist_instance_id", "template_item_id"})})
public class InspectionChecklistResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_instance_id", nullable = false)
    @JsonIgnore
    private InspectionChecklist checklistInstance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_item_id", nullable = false)
    private ChecklistTemplateItem templateItem;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_status", nullable = false)
    private ChecklistResponseStatus responseStatus = ChecklistResponseStatus.NOT_REVIEWED;

    @Column(name = "officer_remarks", columnDefinition = "TEXT")
    private String officerRemarks;

    @Column(name = "officer_id")
    private Long officerId;

    @Transient
    private UUID officerUuid;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "checklistResponse", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ChecklistFindingLink> aiFindingLinks = new ArrayList<>();

    @OneToMany(mappedBy = "response", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ChecklistResponseEvidence> linkedEvidence = new ArrayList<>();

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
        if (this.responseStatus == null) {
            this.responseStatus = ChecklistResponseStatus.NOT_REVIEWED;
        }
        if (this.revisionNumber == null) {
            this.revisionNumber = 1;
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

    public InspectionChecklist getChecklistInstance() { return checklistInstance; }
    public void setChecklistInstance(InspectionChecklist checklistInstance) { this.checklistInstance = checklistInstance; }

    public ChecklistTemplateItem getTemplateItem() { return templateItem; }
    public void setTemplateItem(ChecklistTemplateItem templateItem) {
        this.templateItem = templateItem;
        if (templateItem != null && (this.itemCode == null || this.itemCode.isBlank())) {
            this.itemCode = templateItem.getItemCode();
        }
    }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public ChecklistResponseStatus getResponseStatus() { return responseStatus; }
    public void setResponseStatus(ChecklistResponseStatus responseStatus) { this.responseStatus = responseStatus; }

    public String getOfficerRemarks() { return officerRemarks; }
    public void setOfficerRemarks(String officerRemarks) { this.officerRemarks = officerRemarks; }

    public Long getOfficerId() { return officerId; }
    public void setOfficerId(Long officerId) { this.officerId = officerId; }

    public UUID getOfficerUuid() { return officerUuid; }
    public void setOfficerUuid(UUID officerUuid) { this.officerUuid = officerUuid; }

    public OffsetDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(OffsetDateTime respondedAt) { this.respondedAt = respondedAt; }

    public Integer getRevisionNumber() { return revisionNumber; }
    public void setRevisionNumber(Integer revisionNumber) { this.revisionNumber = revisionNumber; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<ChecklistFindingLink> getAiFindingLinks() { return aiFindingLinks; }
    public void setAiFindingLinks(List<ChecklistFindingLink> aiFindingLinks) { this.aiFindingLinks = aiFindingLinks; }

    public List<ChecklistResponseEvidence> getLinkedEvidence() { return linkedEvidence; }
    public void setLinkedEvidence(List<ChecklistResponseEvidence> linkedEvidence) { this.linkedEvidence = linkedEvidence; }

    public void addAiFindingLink(ChecklistFindingLink link) {
        this.aiFindingLinks.add(link);
        link.setChecklistResponse(this);
    }

    public void addLinkedEvidence(ChecklistResponseEvidence evidenceLink) {
        this.linkedEvidence.add(evidenceLink);
        evidenceLink.setResponse(this);
    }
}
