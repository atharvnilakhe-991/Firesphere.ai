package com.firesphere.backend.certificate.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "noc_certificates")
public class NocCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // A separate, safe-to-expose public identifier
    @Column(nullable = false, unique = true)
    private String publicCertificateId;

    @Column(nullable = false, unique = true)
    private UUID applicationId;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private OffsetDateTime issuedAt;

    private String artifactReference;
    private String checksum;

    @Column(nullable = false)
    private OffsetDateTime createdAt;
    
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
        if (publicCertificateId == null) {
            publicCertificateId = "CERT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        if (issuedAt == null) {
            issuedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getPublicCertificateId() { return publicCertificateId; }
    public void setPublicCertificateId(String publicCertificateId) { this.publicCertificateId = publicCertificateId; }
    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(OffsetDateTime issuedAt) { this.issuedAt = issuedAt; }
    public String getArtifactReference() { return artifactReference; }
    public void setArtifactReference(String artifactReference) { this.artifactReference = artifactReference; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
