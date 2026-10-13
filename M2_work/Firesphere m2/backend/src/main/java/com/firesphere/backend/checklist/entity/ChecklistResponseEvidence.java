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
import jakarta.persistence.Transient;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "checklist_response_evidence", schema = "firesphere")
public class ChecklistResponseEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "response_id", nullable = false)
    @JsonIgnore
    private InspectionChecklistResponse response;

    @Column(name = "evidence_id", nullable = false)
    private Long evidenceId;

    @Transient
    private UUID evidenceUuid;

    @Column(name = "attached_at", nullable = false)
    private OffsetDateTime attachedAt;

    @Column(name = "notes")
    private String notes;

    @PrePersist
    protected void onCreate() {
        if (this.attachedAt == null) {
            this.attachedAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public InspectionChecklistResponse getResponse() { return response; }
    public void setResponse(InspectionChecklistResponse response) { this.response = response; }

    public Long getEvidenceId() { return evidenceId; }
    public void setEvidenceId(Long evidenceId) { this.evidenceId = evidenceId; }

    public UUID getEvidenceUuid() { return evidenceUuid; }
    public void setEvidenceUuid(UUID evidenceUuid) { this.evidenceUuid = evidenceUuid; }

    public OffsetDateTime getAttachedAt() { return attachedAt; }
    public void setAttachedAt(OffsetDateTime attachedAt) { this.attachedAt = attachedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
