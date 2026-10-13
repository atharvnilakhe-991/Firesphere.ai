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
@Table(name = "ai_finding_audits", schema = "firesphere")
public class AiFindingAudit {

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

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(nullable = false)
    private String action;

    @Column(name = "previous_state")
    private String previousState;

    @Column(name = "new_state", nullable = false)
    private String newState;

    @Column(name = "performed_by_user_id")
    private Long performedByUserId;

    @Transient
    private UUID performedByUserUuid;

    @Column(name = "performed_at", nullable = false)
    private OffsetDateTime performedAt;

    @PrePersist
    protected void onCreate() {
        if (this.uuid == null) {
            this.uuid = UUID.randomUUID();
        }
        if (this.performedAt == null) {
            this.performedAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }

    public AiAnalysisFinding getFinding() { return finding; }
    public void setFinding(AiAnalysisFinding finding) { this.finding = finding; }

    public UUID getCorrelationId() { return correlationId; }
    public void setCorrelationId(UUID correlationId) { this.correlationId = correlationId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getPreviousState() { return previousState; }
    public void setPreviousState(String previousState) { this.previousState = previousState; }

    public String getNewState() { return newState; }
    public void setNewState(String newState) { this.newState = newState; }

    public Long getPerformedByUserId() { return performedByUserId; }
    public void setPerformedByUserId(Long performedByUserId) { this.performedByUserId = performedByUserId; }

    public UUID getPerformedByUserUuid() { return performedByUserUuid; }
    public void setPerformedByUserUuid(UUID performedByUserUuid) { this.performedByUserUuid = performedByUserUuid; }

    public void setPerformedByUserId(UUID performedByUserUuid) { this.performedByUserUuid = performedByUserUuid; }

    public OffsetDateTime getPerformedAt() { return performedAt; }
    public void setPerformedAt(OffsetDateTime performedAt) { this.performedAt = performedAt; }
}
