package com.firesphere.backend.checklist.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "noc_checklist_items")
public class ChecklistItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_id", nullable = false)
    private NocChecklist checklist;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Boolean required;

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, PASSED, FAILED, N/A

    private String remarks;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public NocChecklist getChecklist() { return checklist; }
    public void setChecklist(NocChecklist checklist) { this.checklist = checklist; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
