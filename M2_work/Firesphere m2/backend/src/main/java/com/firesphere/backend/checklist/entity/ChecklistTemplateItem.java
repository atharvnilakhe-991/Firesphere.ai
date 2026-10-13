package com.firesphere.backend.checklist.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inspection_checklist_items", schema = "firesphere")
public class ChecklistTemplateItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_id", nullable = false)
    @JsonIgnore
    private ChecklistTemplate template;

    @Column(name = "item_sequence", nullable = false)
    private Integer itemSequence;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(name = "item_title", nullable = false)
    private String itemTitle;

    @Column(name = "parameter_description", columnDefinition = "TEXT", nullable = false)
    private String parameterDescription;

    @Column(name = "requirement_type", nullable = false)
    private String requirementType = "MANDATORY";

    @Column(name = "verification_method")
    private String verificationMethod = "PHYSICAL_INSPECTION";

    @Column(name = "evidence_required", nullable = false)
    private Boolean evidenceRequired = false;

    @Column(name = "ai_category_tag")
    private String aiCategoryTag;

    @Column(name = "penalty_points")
    private Integer penaltyPoints = 0;

    @Column(name = "help_guidance", columnDefinition = "TEXT")
    private String helpGuidance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.uuid == null) {
            this.uuid = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
        if (this.requirementType == null) {
            this.requirementType = "MANDATORY";
        }
        if (this.evidenceRequired == null) {
            this.evidenceRequired = false;
        }
        if (this.verificationMethod == null) {
            this.verificationMethod = "PHYSICAL_INSPECTION";
        }
        if (this.penaltyPoints == null) {
            this.penaltyPoints = 0;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }

    public ChecklistTemplate getTemplate() { return template; }
    public void setTemplate(ChecklistTemplate template) { this.template = template; }

    public Integer getItemSequence() { return itemSequence; }
    public void setItemSequence(Integer itemSequence) { this.itemSequence = itemSequence; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public String getItemTitle() { return itemTitle; }
    public void setItemTitle(String itemTitle) { this.itemTitle = itemTitle; }

    public String getParameterDescription() { return parameterDescription; }
    public void setParameterDescription(String parameterDescription) { this.parameterDescription = parameterDescription; }

    public String getRequirementType() { return requirementType; }
    public void setRequirementType(String requirementType) { this.requirementType = requirementType; }

    public String getVerificationMethod() { return verificationMethod; }
    public void setVerificationMethod(String verificationMethod) { this.verificationMethod = verificationMethod; }

    public Boolean getEvidenceRequired() { return evidenceRequired; }
    public void setEvidenceRequired(Boolean evidenceRequired) { this.evidenceRequired = evidenceRequired; }

    public String getAiCategoryTag() { return aiCategoryTag; }
    public void setAiCategoryTag(String aiCategoryTag) { this.aiCategoryTag = aiCategoryTag; }

    public Integer getPenaltyPoints() { return penaltyPoints; }
    public void setPenaltyPoints(Integer penaltyPoints) { this.penaltyPoints = penaltyPoints; }

    public String getHelpGuidance() { return helpGuidance; }
    public void setHelpGuidance(String helpGuidance) { this.helpGuidance = helpGuidance; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isMandatory() {
        return "MANDATORY".equalsIgnoreCase(this.requirementType);
    }
}
