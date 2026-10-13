# FireSphere.ai — Phase 3B: Identifier Mapping Review

**Author**: Senior Database Architect, Spring Boot Persistence Engineer  
**Status**: PRE-IMPLEMENTATION DESIGN / APPROVAL GATE  
**Date**: October 2026  
**Phase**: Phase 3B (Controlled Database & Evidence Integration)  

---

## 1. Executive Summary

This document validates the **Dual-Key Architecture (Strategy C)** across Member 2's Spring Boot backend (`M2_work/Firesphere m2/backend`) and Member 3's PostgreSQL database (`M3_work/IP_DB`).

### Core Architectural Principles
1. **Internal Relational Primary Key (`id BIGINT`)**:
   - High-performance, 8-byte integer keys used exclusively for relational joins (`FOREIGN KEY REFERENCES ... (id)`) and B-Tree indexing.
2. **Public Business Identifier (`uuid UUID`)**:
   - Cryptographically random, unguessable 128-bit identifiers exposed across external interfaces (REST endpoints, JWT tokens, mobile clients, and M4 AI service payloads).
3. **M2 Resolution Boundary**:
   - M2 accepts public `UUID` from HTTP requests, resolves the entity via `findByUuidAndOrganizationId()`, uses internal `Long id` for relational persistence, and maps `entity.getUuid()` back to `dto.id` in responses.
4. **Zero Public API Regressions**:
   - Public JSON contracts maintain `UUID` identifiers identically. No client-facing breaking changes are introduced.

---

## 2. Comprehensive Inventory of Affected M2 Entities & Mappings

Below is the exhaustive specification of every entity affected by the Dual-Key refactoring in Phase 3B:

| Entity Class | Current M2 Mapping | Target Dual-Key Mapping | Affected Repository Methods |
| :--- | :--- | :--- | :--- |
| **`NocApplication`** | `@Id UUID id` | `@Id Long id`<br/>`@Column(name="uuid") UUID uuid` | `Optional<NocApplication> findByUuidAndOrganizationId(UUID uuid, Long orgId);`<br/>`boolean existsByUuid(UUID uuid);` |
| **`NocInspection`** | `@Id UUID id`<br/>`UUID applicationId` | `@Id Long id`<br/>`@Column(name="uuid") UUID uuid`<br/>`Long applicationId` | `Optional<NocInspection> findByUuidAndApplicationIdAndOrganizationId(UUID uuid, Long appId, Long orgId);`<br/>`List<NocInspection> findAllByApplicationId(Long appId);` |
| **`NocDocument`** | `@Id UUID id`<br/>`UUID applicationId` | `@Id Long id`<br/>`@Column(name="uuid") UUID uuid`<br/>`Long applicationId` | `Optional<NocDocument> findByUuidAndApplicationId(UUID uuid, Long appId);`<br/>`List<NocDocument> findAllByApplicationId(Long appId);` |
| **`NocChecklist`** | `@Id UUID id`<br/>`UUID applicationId` | `@Id Long id`<br/>`@Column(name="uuid") UUID uuid`<br/>`Long applicationId` | `Optional<NocChecklist> findByApplicationId(Long appId);`<br/>`Optional<NocChecklist> findByUuid(UUID uuid);` |
| **`ChecklistRule`** | `@Id UUID id` | `@Id Long id`<br/>`@Column(name="uuid") UUID uuid` | `Optional<ChecklistRule> findByUuid(UUID uuid);` |
| **`NocCertificate`**| `@Id UUID id`<br/>`UUID applicationId` | `@Id Long id`<br/>`@Column(name="uuid") UUID uuid`<br/>`Long applicationId` | `Optional<NocCertificate> findByUuid(UUID uuid);`<br/>`Optional<NocCertificate> findByApplicationId(Long appId);` |
| **`PaymentRecord`** | `@Id UUID id`<br/>`UUID applicationId` | `@Id Long id`<br/>`@Column(name="uuid") UUID uuid`<br/>`Long applicationId` | `Optional<PaymentRecord> findByUuid(UUID uuid);`<br/>`List<PaymentRecord> findAllByApplicationId(Long appId);` |
| **`User`** | `@Id UUID id`<br/>`UUID organizationId`| `@Id Long id`<br/>`@Column(name="uuid") UUID uuid`<br/>`Long organizationId` | `Optional<User> findByUuid(UUID uuid);`<br/>`Optional<User> findByEmail(String email);` |
| **`Organization`** | `@Id UUID id` | `@Id Long id`<br/>`@Column(name="uuid") UUID uuid` | `Optional<Organization> findByUuid(UUID uuid);` |
| **`IdempotencyRecord`** | `@Id UUID id` | `@Id Long id`<br/>`@Column(name="uuid") UUID uuid`<br/>`@Column(name="key") String key` | `Optional<IdempotencyRecord> findByKeyAndOrganizationId(String key, Long orgId);` |
| **`AiAnalysisRun`** | `@Id UUID id` | `@Id Long id`<br/>`@Column(name="uuid") UUID uuid`<br/>`Long inspectionId` | `Optional<AiAnalysisRun> findByUuidAndOrganizationId(UUID uuid, Long orgId);`<br/>`List<AiAnalysisRun> findAllByInspectionId(Long inspId);` |
| **`AiFinding`** (NEW) | *(Embeddable)* | `@Id Long id`<br/>`@Column(name="uuid") UUID uuid`<br/>`Long runId`, `Long evidenceId` | `List<AiFinding> findAllByRunId(Long runId);`<br/>`Optional<AiFinding> findByUuid(UUID uuid);` |
| **`NocInspectionEvidence`** (NEW) | *(Non-existent)*| `@Id Long id`<br/>`@Column(name="uuid") UUID uuid`<br/>`Long inspectionId`, `Long orgId` | `List<NocInspectionEvidence> findAllByInspectionId(Long inspId);`<br/>`Optional<NocInspectionEvidence> findByUuid(UUID uuid);` |
| **`AiOfficerReview`** (NEW)| *(Non-existent)*| `@Id Long id`<br/>`@Column(name="uuid") UUID uuid`<br/>`Long findingId`, `Long officerId`| `Optional<AiOfficerReview> findByFindingId(Long findingId);` |
| **`AiFindingAudit`** (NEW) | *(Non-existent)*| `@Id Long id`<br/>`@Column(name="uuid") UUID uuid`<br/>`Long findingId` | `List<AiFindingAudit> findAllByFindingIdOrderByPerformedAtAsc(Long findingId);` |

---

## 3. Entity Implementation Pattern (Before vs. After)

### 3.1 Current Problematic Entity Pattern (M2)
```java
// CURRENT VULNERABLE ENTITY (e.g. NocInspection.java)
@Entity
@Table(name = "noc_inspections") // Missing schema = "firesphere"
public class NocInspection {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id; // Conflicts with M3 BIGSERIAL PRIMARY KEY!

    @Column(nullable = false)
    private UUID applicationId; // Cannot join with applications(id BIGINT)
    // ...
}
```

### 3.2 Target Dual-Key Entity Pattern (Phase 3B)
```java
// TARGET APPROVED ENTITY (NocInspection.java)
@Entity
@Table(name = "inspections", schema = "firesphere")
public class NocInspection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id; // Relational Primary Key (BIGINT)

    @Column(name = "uuid", nullable = false, unique = true, updatable = false)
    private UUID uuid; // Public Canonical Business Key

    @Column(name = "application_id", nullable = false)
    private Long applicationId; // Relational Foreign Key to applications(id)

    @Column(name = "org_id", nullable = false)
    private Long organizationId; // Multi-tenant isolation key

    @PrePersist
    protected void onPrePersist() {
        if (this.uuid == null) {
            this.uuid = UUID.randomUUID();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }
    // ...
}
```

---

## 4. Resolution of Identifiers at Service Boundaries

### 4.1 Inbound Request Handling (DTO -> Service -> Database)
When an external client calls `POST /api/v1/inspections/{inspectionUuid}/ai-analysis`:
```java
public AiAnalysisResponse requestAnalysis(UUID inspectionUuid, AiAnalysisRequest request) {
    // 1. Resolve Tenant internal ID from JWT
    Long internalOrgId = tenantResolutionService.resolveInternalOrgId(TenantContext.getCurrentOrganizationId());

    // 2. Resolve Inspection entity by Public UUID
    NocInspection inspection = inspectionRepository
            .findByUuidAndOrganizationId(inspectionUuid, internalOrgId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inspection not found"));

    // 3. Resolve Evidence IDs: query database using internal inspection.getId()
    List<NocInspectionEvidence> evidenceList = evidenceRepository
            .findAllByInspectionIdAndOrgId(inspection.getId(), internalOrgId);

    // 4. Extract public UUIDs for M4 contract dispatch
    List<UUID> publicEvidenceUuids = evidenceList.stream()
            .map(NocInspectionEvidence::getUuid)
            .collect(Collectors.toList());

    // 5. Send public UUIDs to M4 (Zero internal database IDs exposed to network)
    ExternalAiRequest m4Request = new ExternalAiRequest(
            inspection.getUuid(),
            publicEvidenceUuids,
            false,
            "BUILDING",
            "UNAVAILABLE",
            TenantContext.getCurrentOrganizationId().toString()
    );
    // ...
}
```

### 4.2 Outbound Response Handling (Entity -> DTO)
When returning data to API clients:
```java
private InspectionResponse toDto(NocInspection inspection, UUID publicApplicationUuid) {
    return new InspectionResponse(
            inspection.getUuid(), // Map entity UUID to DTO id
            publicApplicationUuid,
            inspection.getInspectionType(),
            inspection.getScheduledAt(),
            // ...
    );
}
```

---

## 5. Why Blanket Search-and-Replace is Prohibited

1. **Foreign Key Dependency Cascades**: Changing `UUID` to `Long` in a blind find-and-replace will corrupt controller signatures, break JWT claim decoders, and introduce compile errors in DTO records across the entire project.
2. **Loss of Unguessable Security Keys**: Blindly exposing `Long id` over HTTP endpoints introduces Insecure Direct Object Reference (IDOR) vulnerabilities, allowing enumeration attacks (`GET /api/v1/inspections/1`, `/2`, `/3`).
3. **M4 Contract Incompatibility**: Phase 2 established that M4's FastAPI endpoints strictly validate Pydantic UUID formats (`uuid.UUID`). Transmitting `Long` identifiers breaks M4 validation schemas instantly.
4. **Controlled Step-by-Step Migration**: Entities must be converted one domain aggregate at a time, backed by strict integration tests asserting schema validation.
