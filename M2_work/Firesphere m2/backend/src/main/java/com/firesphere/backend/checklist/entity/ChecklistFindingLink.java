package com.firesphere.backend.checklist.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "checklist_finding_links", schema = "firesphere",
        uniqueConstraints = {@jakarta.persistence.UniqueConstraint(name = "uq_finding_link_resp_finding", columnNames = {"checklist_response_id", "finding_id"})})
public class ChecklistFindingLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_response_id", nullable = false)
    @JsonIgnore
    private InspectionChecklistResponse checklistResponse;

    @Column(name = "finding_id", nullable = false)
    private Long findingId;

    @Transient
    private UUID findingUuid;

    @Column(name = "source_provenance", nullable = false)
    private String sourceProvenance = "CROSS_VALIDATED";

    @Column(name = "mapping_reason", columnDefinition = "TEXT")
    private String mappingReason;

    @Column(name = "suggested_compliance", nullable = false)
    private String suggestedCompliance = "NEEDS_FURTHER_INSPECTION";

    @Column(nullable = false)
    private Double confidence = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_provenance", nullable = false)
    private LinkProvenance linkProvenance = LinkProvenance.AI_SUGGESTION;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_status", nullable = false)
    private FindingLinkStatus linkStatus = FindingLinkStatus.SUGGESTED;

    @Column(name = "review_status", nullable = false)
    private String reviewStatus = "PENDING_REVIEW";

    @Column(name = "officer_comment", columnDefinition = "TEXT")
    private String officerComment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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
        if (this.linkProvenance == null) {
            this.linkProvenance = LinkProvenance.AI_SUGGESTION;
        }
        if (this.linkStatus == null) {
            this.linkStatus = FindingLinkStatus.SUGGESTED;
        }
        if (this.reviewStatus == null) {
            this.reviewStatus = "PENDING_REVIEW";
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

    public InspectionChecklistResponse getChecklistResponse() { return checklistResponse; }
    public void setChecklistResponse(InspectionChecklistResponse checklistResponse) { this.checklistResponse = checklistResponse; }

    public Long getFindingId() { return findingId; }
    public void setFindingId(Long findingId) { this.findingId = findingId; }

    public UUID getFindingUuid() { return findingUuid; }
    public void setFindingUuid(UUID findingUuid) { this.findingUuid = findingUuid; }

    public String getSourceProvenance() { return sourceProvenance; }
    public void setSourceProvenance(String sourceProvenance) { this.sourceProvenance = sourceProvenance; }

    public String getMappingReason() { return mappingReason; }
    public void setMappingReason(String mappingReason) { this.mappingReason = mappingReason; }

    public String getSuggestedCompliance() { return suggestedCompliance; }
    public void setSuggestedCompliance(String suggestedCompliance) { this.suggestedCompliance = suggestedCompliance; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public LinkProvenance getLinkProvenance() { return linkProvenance; }
    public void setLinkProvenance(LinkProvenance linkProvenance) { this.linkProvenance = linkProvenance; }

    public FindingLinkStatus getLinkStatus() { return linkStatus; }
    public void setLinkStatus(FindingLinkStatus linkStatus) { this.linkStatus = linkStatus; }

    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }

    public String getOfficerComment() { return officerComment; }
    public void setOfficerComment(String officerComment) { this.officerComment = officerComment; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
