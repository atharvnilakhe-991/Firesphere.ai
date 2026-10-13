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
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_officer_reviews", schema = "firesphere")
public class AiOfficerReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finding_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private AiAnalysisFinding finding;

    @Column(name = "officer_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Long officerId;

    @Transient
    private UUID officerUuid;

    public UUID getFindingId() {
        return finding != null ? finding.getUuid() : null;
    }

    public UUID getReviewId() {
        return uuid;
    }

    @Column(name = "review_status", nullable = false)
    private String reviewStatus; // CONFIRMED, DISMISSED, MODIFIED

    @Column(name = "officer_notes", columnDefinition = "TEXT")
    private String officerNotes;

    @Column(name = "hazard_verified", nullable = false)
    private Boolean hazardVerified = false;

    @Column(name = "reviewed_at", nullable = false)
    private OffsetDateTime reviewedAt;

    @PrePersist
    protected void onCreate() {
        if (this.uuid == null) {
            this.uuid = UUID.randomUUID();
        }
        if (this.reviewedAt == null) {
            this.reviewedAt = OffsetDateTime.now();
        }
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }

    public AiAnalysisFinding getFinding() { return finding; }
    public void setFinding(AiAnalysisFinding finding) { this.finding = finding; }

    public Long getOfficerId() { return officerId; }
    public void setOfficerId(Long officerId) { this.officerId = officerId; }

    public UUID getOfficerUuid() { return officerUuid; }
    public void setOfficerUuid(UUID officerUuid) { this.officerUuid = officerUuid; }

    public void setOfficerId(UUID officerUuid) { this.officerUuid = officerUuid; }

    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }

    public String getOfficerNotes() { return officerNotes; }
    public void setOfficerNotes(String officerNotes) { this.officerNotes = officerNotes; }

    public Boolean getHazardVerified() { return hazardVerified; }
    public void setHazardVerified(Boolean hazardVerified) { this.hazardVerified = hazardVerified; }

    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
