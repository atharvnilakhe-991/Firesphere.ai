package com.firesphere.backend.auth.entity;

import com.firesphere.backend.common.security.Role;
import com.firesphere.backend.organization.entity.Organization;
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

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "firesphere")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(name = "org_id", nullable = false)
    private Long orgId = 1L;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "org_id", insertable = false, updatable = false)
    private Organization organization;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName = "System";

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName = "User";

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String status = "ACTIVE";

    @Column(name = "designation")
    private String designation;

    @Column(name = "is_super_admin")
    private Boolean isSuperAdmin = false;

    @Transient
    private String role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.uuid == null) {
            this.uuid = UUID.randomUUID();
        }
        if (this.orgId == null) {
            this.orgId = 1L;
        }
        if (this.firstName == null) {
            this.firstName = "System";
        }
        if (this.lastName == null) {
            this.lastName = "User";
        }
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setId(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
        if (organization != null) {
            this.orgId = organization.getId();
        }
    }

    public UUID getOrganizationUuid() {
        return organization != null ? organization.getUuid() : null;
    }

    public Long getOrganizationId() {
        return orgId;
    }

    public void setOrganizationId(Long organizationId) {
        this.orgId = organizationId;
    }

    public void setOrganizationId(UUID organizationUuid) {
        // Legacy test compatibility overload
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return passwordHash;
    }

    public void setPassword(String password) {
        this.passwordHash = password;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public Boolean getIsSuperAdmin() {
        return isSuperAdmin;
    }

    public void setIsSuperAdmin(Boolean isSuperAdmin) {
        this.isSuperAdmin = isSuperAdmin;
    }

    public String getRole() {
        if (this.role != null) {
            return this.role;
        }
        if (Boolean.TRUE.equals(this.isSuperAdmin)) {
            return Role.ADMIN.name();
        }
        if (this.designation != null) {
            String d = this.designation.toUpperCase();
            if (d.contains("ADMIN") || d.contains("SUPER")) {
                return Role.ADMIN.name();
            }
            if (d.contains("APPROVAL") || d.contains("CHIEF") || d.contains("CFO")) {
                return Role.APPROVAL_AUTHORITY.name();
            }
            if (d.contains("OFFICER") || d.contains("INSPECTOR") || d.contains("SO") || d.contains("STATION")) {
                return Role.OFFICER.name();
            }
            try {
                return Role.valueOf(d).name();
            } catch (Exception ignored) {
            }
        }
        return Role.CITIZEN.name();
    }

    public void setRole(String role) {
        this.role = role;
        if (this.designation == null && role != null) {
            this.designation = role;
        }
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
