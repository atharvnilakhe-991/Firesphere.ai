package com.firesphere.backend.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_findings", schema = "firesphere")
public class AiAnalysisFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private AiAnalysisRun run;

    @Column(name = "evidence_id")
    private Long evidenceId;

    @Transient
    private UUID evidenceUuid;

    @Column(name = "finding_type", nullable = false)
    private String findingType = "OBSERVATION";

    @Transient
    private String finding;

    @Column(nullable = false)
    private Double confidence = 0.0;

    @Column(nullable = false)
    private String severity = "MEDIUM";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bounding_box", columnDefinition = "jsonb")
    private String boundingBoxJson;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "verification_guidance", columnDefinition = "TEXT")
    private String verificationGuidance;

    @Column(name = "is_advisory", nullable = false)
    private Boolean isAdvisory = true;

    @Column(name = "review_status", nullable = false)
    private String reviewStatus = "PENDING_REVIEW";

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
        this.updatedAt = this.createdAt;
        if (this.isAdvisory == null) {
            this.isAdvisory = true;
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

    public AiAnalysisRun getRun() { return run; }
    public void setRun(AiAnalysisRun run) { this.run = run; }

    public Long getEvidenceId() { return evidenceId; }
    public void setEvidenceId(Long evidenceId) { this.evidenceId = evidenceId; }

    public UUID getEvidenceUuid() { return evidenceUuid; }
    public void setEvidenceUuid(UUID evidenceUuid) { this.evidenceUuid = evidenceUuid; }

    // Overload for UUID callers
    public void setEvidenceId(UUID evidenceUuid) { this.evidenceUuid = evidenceUuid; }

    public String getFindingType() { return findingType; }
    public void setFindingType(String findingType) { this.findingType = findingType; }

    public String getFinding() {
        return finding != null ? finding : (description != null ? description : findingType);
    }
    public void setFinding(String finding) {
        this.finding = finding;
        if (this.description == null) {
            this.description = finding;
        }
    }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getBoundingBoxJson() { return boundingBoxJson; }
    public void setBoundingBoxJson(String boundingBoxJson) { this.boundingBoxJson = boundingBoxJson; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVerificationGuidance() { return verificationGuidance; }
    public void setVerificationGuidance(String verificationGuidance) { this.verificationGuidance = verificationGuidance; }

    public Boolean getIsAdvisory() { return isAdvisory; }
    public void setIsAdvisory(Boolean isAdvisory) { this.isAdvisory = isAdvisory; }

    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
