package com.firesphere.backend.common.idempotency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import java.time.OffsetDateTime;

import jakarta.persistence.Transient;
@Entity
@Table(name = "idempotency_records", uniqueConstraints = {
    @jakarta.persistence.UniqueConstraint(columnNames = {"organization_id", "user_id", "idempotency_key"})
})
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private java.util.UUID id;

    @Column(name = "organization_id", nullable = false)
    private java.util.UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private java.util.UUID userId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", length = 256)
    private String requestFingerprint;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public IdempotencyRecord() {}

    public IdempotencyRecord(java.util.UUID organizationId, java.util.UUID userId, String idempotencyKey, String requestFingerprint, String status, String responseBody, Integer statusCode) {
        this.organizationId = organizationId;
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.status = status;
        this.responseBody = responseBody;
        this.statusCode = statusCode;
    }

    public java.util.UUID getId() { return id; }
    public java.util.UUID getOrganizationId() { return organizationId; }
    public java.util.UUID getUserId() { return userId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String requestFingerprint) { this.requestFingerprint = requestFingerprint; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
    public Integer getStatusCode() { return statusCode; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    @jakarta.persistence.PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
