package com.firesphere.backend.inspection.service;

import com.firesphere.backend.common.audit.AuditEventType;
import com.firesphere.backend.common.audit.AuditLogger;
import com.firesphere.backend.common.security.Role;
import com.firesphere.backend.common.security.TenantContext;
import com.firesphere.backend.document.service.EvidenceStorageService;
import com.firesphere.backend.inspection.dto.EvidenceResponse;
import com.firesphere.backend.inspection.entity.NocInspection;
import com.firesphere.backend.inspection.entity.NocInspectionEvidence;
import com.firesphere.backend.inspection.repository.NocInspectionEvidenceRepository;
import com.firesphere.backend.inspection.repository.NocInspectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NocInspectionEvidenceService {

    private static final Logger log = LoggerFactory.getLogger(NocInspectionEvidenceService.class);
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );

    private final NocInspectionEvidenceRepository evidenceRepository;
    private final NocInspectionRepository inspectionRepository;
    private final EvidenceStorageService storageService;
    private final AuditLogger auditLogger;

    public NocInspectionEvidenceService(
            NocInspectionEvidenceRepository evidenceRepository,
            NocInspectionRepository inspectionRepository,
            EvidenceStorageService storageService,
            AuditLogger auditLogger) {
        this.evidenceRepository = evidenceRepository;
        this.inspectionRepository = inspectionRepository;
        this.storageService = storageService;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public EvidenceResponse uploadEvidence(
            UUID inspectionId,
            MultipartFile file,
            String captureTimestampStr,
            Double gpsLatitude,
            Double gpsLongitude) {

        UUID orgId = TenantContext.getCurrentOrganizationId();
        Long internalOrgId = TenantContext.getCurrentInternalOrgId();
        if (orgId == null && internalOrgId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tenant context missing");
        }

        NocInspection inspection = inspectionRepository.findByUuid(inspectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inspection not found"));

        if (internalOrgId != null && !inspection.getOrgId().equals(internalOrgId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant inspection access prohibited");
        }

        boolean isOfficer = TenantContext.hasRole(Role.OFFICER.name()) || TenantContext.hasRole(Role.ADMIN.name());
        if (!isOfficer) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only certified officers can upload inspection evidence");
        }

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evidence image payload cannot be null or empty");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evidence exceeds maximum size limit of 10MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image media type: " + contentType);
        }

        byte[] bytes;
        String sha256Hex;
        try {
            bytes = file.getBytes();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            sha256Hex = HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to compute image cryptographic hash", e);
        }

        Long effectiveOrgId = inspection.getOrgId();

        // Deduplication check: if this file was already uploaded for this organization, return existing record
        Optional<NocInspectionEvidence> existing = evidenceRepository.findByOrgIdAndSha256Hash(effectiveOrgId, sha256Hex);
        if (existing.isPresent()) {
            log.info("Deduplicated evidence upload for SHA-256: {}", sha256Hex);
            NocInspectionEvidence ex = existing.get();
            ex.setInspectionUuid(inspection.getUuid());
            return toDto(ex);
        }

        UUID evidenceUuid = UUID.randomUUID();
        String storageKey = "tenants/" + effectiveOrgId + "/evidence/" + evidenceUuid + ".jpg";

        try {
            storageService.storeEvidence(storageKey, contentType, new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            log.error("Storage persistence error for evidence {}: {}", evidenceUuid, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to persist evidence asset to storage", e);
        }

        OffsetDateTime captureTimestamp = null;
        if (captureTimestampStr != null && !captureTimestampStr.isBlank()) {
            try {
                captureTimestamp = OffsetDateTime.parse(captureTimestampStr);
            } catch (DateTimeParseException ignored) {
                captureTimestamp = OffsetDateTime.now();
            }
        }

        String rawFileName = file.getOriginalFilename();
        String safeFileName;
        if (rawFileName != null && !rawFileName.isBlank()) {
            String base = java.nio.file.Paths.get(rawFileName).getFileName().toString();
            safeFileName = base.replaceAll("[\\r\\n\"\\\\]", "_").trim();
            if (safeFileName.isBlank()) {
                safeFileName = evidenceUuid + ".jpg";
            }
        } else {
            safeFileName = evidenceUuid + ".jpg";
        }

        NocInspectionEvidence evidence = new NocInspectionEvidence();
        evidence.setUuid(evidenceUuid);
        evidence.setOrgId(effectiveOrgId);
        evidence.setInspectionId(inspection.getId());
        evidence.setInspectionUuid(inspection.getUuid());
        evidence.setFileName(safeFileName);
        evidence.setContentType(contentType);
        evidence.setFileSizeBytes(file.getSize());
        evidence.setStorageKey(storageKey);
        evidence.setSha256Hash(sha256Hex);
        evidence.setCaptureTimestamp(captureTimestamp);
        evidence.setGpsLatitude(gpsLatitude);
        evidence.setGpsLongitude(gpsLongitude);
        evidence.setCapturedByUserId(TenantContext.getCurrentInternalUserId());
        evidence.setStatus("ACTIVE");

        evidence = evidenceRepository.save(evidence);
        auditLogger.log(AuditEventType.INSPECTION_COMPLETED, evidence.getUuid(), "Inspection evidence photo uploaded");

        return toDto(evidence);
    }

    public List<EvidenceResponse> listEvidenceForInspection(UUID inspectionId) {
        UUID orgId = TenantContext.getCurrentOrganizationId();
        Long internalOrgId = TenantContext.getCurrentInternalOrgId();
        if (orgId == null && internalOrgId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tenant context missing");
        }

        NocInspection inspection = inspectionRepository.findByUuid(inspectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inspection not found"));

        if (inspection.getOrgId() != null && (internalOrgId == null || !inspection.getOrgId().equals(internalOrgId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant access prohibited");
        }

        return evidenceRepository.findAllByInspectionIdAndOrgId(inspection.getId(), inspection.getOrgId()).stream()
                .peek(e -> e.setInspectionUuid(inspection.getUuid()))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<UUID> resolveEvidenceUuidsForInspection(UUID inspectionId, UUID orgId) {
        UUID currentOrgId = TenantContext.getCurrentOrganizationId();
        Long internalOrgId = TenantContext.getCurrentInternalOrgId();
        if (currentOrgId == null && internalOrgId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tenant context missing");
        }

        NocInspection inspection = inspectionRepository.findByUuid(inspectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inspection not found"));

        if (inspection.getOrgId() != null && (internalOrgId == null || !inspection.getOrgId().equals(internalOrgId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant inspection access prohibited");
        }

        return evidenceRepository.findAllByInspectionId(inspection.getId()).stream()
                .filter(e -> "ACTIVE".equalsIgnoreCase(e.getStatus()))
                .map(NocInspectionEvidence::getUuid)
                .collect(Collectors.toList());
    }

    public record EvidenceContent(byte[] data, String contentType, String fileName) {}

    public EvidenceContent getEvidenceContent(UUID inspectionId, UUID evidenceId) {
        UUID orgId = TenantContext.getCurrentOrganizationId();
        Long internalOrgId = TenantContext.getCurrentInternalOrgId();
        if (orgId == null && internalOrgId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tenant context missing");
        }

        NocInspection inspection = inspectionRepository.findByUuid(inspectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inspection not found"));

        if (inspection.getOrgId() != null && (internalOrgId == null || !inspection.getOrgId().equals(internalOrgId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant access prohibited");
        }

        NocInspectionEvidence evidence = evidenceRepository.findByUuid(evidenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found"));

        if (!evidence.getInspectionId().equals(inspection.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evidence does not belong to specified inspection");
        }

        if (!storageService.evidenceExists(evidence.getStorageKey())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence asset not found in storage");
        }

        byte[] bytes = storageService.retrieveEvidence(evidence.getStorageKey());
        return new EvidenceContent(bytes, evidence.getContentType(), evidence.getFileName());
    }

    private EvidenceResponse toDto(NocInspectionEvidence entity) {
        return new EvidenceResponse(
                entity.getUuid(),
                entity.getInspectionUuid(),
                entity.getFileName(),
                entity.getContentType(),
                entity.getFileSizeBytes(),
                entity.getStorageKey(),
                entity.getSha256Hash(),
                entity.getStatus(),
                entity.getCaptureTimestamp(),
                entity.getGpsLatitude(),
                entity.getGpsLongitude(),
                entity.getCreatedAt()
        );
    }
}
