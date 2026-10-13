package com.firesphere.backend.certificate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "noc_certificates", schema = "firesphere")
public class NocCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "certificate_number", nullable = false)
    private String certificateNumber;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "qr_code_token", nullable = false)
    private String qrCodeToken;

    @Column(name = "certificate_pdf_path")
    private String certificatePdfPath;

    @Column(name = "is_revoked", nullable = false)
    private Boolean isRevoked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // Transient fields for compatibility with existing M2 DTOs
    @Transient
    private String publicCertificateId;

    @Transient
    private UUID applicationUuid;

    @Transient
    private UUID organizationId;

    @Transient
    private String status;

    @Transient
    private OffsetDateTime issuedAt;

    @Transient
    private String artifactReference;

    @Transient
    private String checksum;

    @Transient
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.uuid == null) {
            this.uuid = UUID.randomUUID();
        }
        if (this.certificateNumber == null) {
            this.certificateNumber = "CERT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        if (this.issueDate == null) {
            this.issueDate = LocalDate.now();
        }
        if (this.expiryDate == null) {
            this.expiryDate = this.issueDate.plusYears(1);
        }
        if (this.qrCodeToken == null) {
            this.qrCodeToken = UUID.randomUUID().toString();
        }
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }

    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public void setApplicationId(UUID applicationUuid) { this.applicationUuid = applicationUuid; }

    public String getCertificateNumber() { return certificateNumber; }
    public void setCertificateNumber(String certificateNumber) { this.certificateNumber = certificateNumber; }

    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public String getQrCodeToken() { return qrCodeToken; }
    public void setQrCodeToken(String qrCodeToken) { this.qrCodeToken = qrCodeToken; }

    public String getCertificatePdfPath() { return certificatePdfPath; }
    public void setCertificatePdfPath(String certificatePdfPath) { this.certificatePdfPath = certificatePdfPath; }

    public Boolean getIsRevoked() { return isRevoked; }
    public void setIsRevoked(Boolean isRevoked) { this.isRevoked = isRevoked; }

    public String getPublicCertificateId() {
        return certificateNumber != null ? certificateNumber : (publicCertificateId != null ? publicCertificateId : ("CERT-" + uuid));
    }
    public void setPublicCertificateId(String publicCertificateId) {
        this.publicCertificateId = publicCertificateId;
        this.certificateNumber = publicCertificateId;
    }

    public UUID getApplicationUuid() { return applicationUuid; }
    public void setApplicationUuid(UUID applicationUuid) { this.applicationUuid = applicationUuid; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public void setOrganizationId(Long orgId) { this.orgId = orgId; }

    public String getStatus() {
        if (status != null) return status;
        return Boolean.TRUE.equals(isRevoked) ? "REVOKED" : "ISSUED";
    }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getIssuedAt() {
        if (issuedAt != null) return issuedAt;
        return issueDate != null ? issueDate.atStartOfDay().atOffset(OffsetDateTime.now().getOffset()) : createdAt;
    }
    public void setIssuedAt(OffsetDateTime issuedAt) { this.issuedAt = issuedAt; }

    public String getArtifactReference() { return artifactReference != null ? artifactReference : certificatePdfPath; }
    public void setArtifactReference(String artifactReference) {
        this.artifactReference = artifactReference;
        this.certificatePdfPath = artifactReference;
    }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
