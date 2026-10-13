package com.firesphere.backend.checklist.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "checklist_rules")
public class ChecklistRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "checklist_type", nullable = false)
    private String checklistType;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Boolean required;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getChecklistType() {
        return checklistType;
    }

    public void setChecklistType(String checklistType) {
        this.checklistType = checklistType;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
