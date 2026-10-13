package com.firesphere.backend.certificate.service;

import com.firesphere.backend.certificate.dto.CertificateResponse;
import com.firesphere.backend.certificate.entity.NocCertificate;
import com.firesphere.backend.certificate.repository.NocCertificateRepository;
import com.firesphere.backend.common.audit.AuditEventType;
import com.firesphere.backend.common.audit.AuditLogger;
import com.firesphere.backend.common.security.TenantContext;
import com.firesphere.backend.noc.entity.NocApplication;
import com.firesphere.backend.noc.entity.NocStatus;
import com.firesphere.backend.noc.repository.NocApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class CertificateService {

    private final NocCertificateRepository certificateRepository;
    private final NocApplicationRepository nocRepository;
    private final com.firesphere.backend.workflow.service.WorkflowService workflowService;
    private final AuditLogger auditLogger;
    private final PdfGeneratorService pdfGeneratorService;
    private final com.firesphere.backend.document.service.DocumentStorageService documentStorageService;

    public CertificateService(NocCertificateRepository certificateRepository,
                              NocApplicationRepository nocRepository,
                              @org.springframework.context.annotation.Lazy com.firesphere.backend.workflow.service.WorkflowService workflowService,
                              AuditLogger auditLogger,
                              PdfGeneratorService pdfGeneratorService,
                              com.firesphere.backend.document.service.DocumentStorageService documentStorageService) {
        this.certificateRepository = certificateRepository;
        this.nocRepository = nocRepository;
        this.workflowService = workflowService;
        this.auditLogger = auditLogger;
        this.pdfGeneratorService = pdfGeneratorService;
        this.documentStorageService = documentStorageService;
    }

    @Transactional
    public CertificateResponse generateCertificate(UUID applicationId) {
        UUID orgId = TenantContext.getCurrentOrganizationId();
        Long internalOrgId = TenantContext.getCurrentInternalOrgId();
        if (orgId == null && internalOrgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing organization context");
        }

        NocApplication noc = nocRepository.findByUuid(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if ((internalOrgId != null && !noc.getOrgId().equals(internalOrgId))
                || (noc.getOrganizationId() != null && orgId != null && !noc.getOrganizationId().equals(orgId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }

        // If it's not already generated, perform the authoritative transition
        if (noc.getStatus() != NocStatus.CERTIFICATE_GENERATED) {
            if (noc.getStatus() != NocStatus.APPROVED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application must be APPROVED to generate a certificate");
            }
            // Authoritative state transition via WorkflowService
            workflowService.performTransition(applicationId, 
                new com.firesphere.backend.workflow.dto.WorkflowActionRequest(
                    com.firesphere.backend.workflow.model.WorkflowAction.GENERATE_CERTIFICATE, null), null);
        }

        // Idempotency constraint - if one exists, return it, or fail safe.
        Optional<NocCertificate> existingCert = certificateRepository.findByApplicationId(noc.getId());
        if (existingCert.isPresent()) {
            return mapToResponse(existingCert.get());
        }

        NocCertificate certificate = new NocCertificate();
        certificate.setApplicationId(noc.getId());
        certificate.setApplicationUuid(noc.getUuid());
        certificate.setOrgId(noc.getOrgId() != null ? noc.getOrgId() : 1L);
        certificate.setOrganizationId(orgId);
        certificate.setStatus("ACTIVE");
        certificate.setIssuedAt(OffsetDateTime.now());
        
        // We need to generate a public certificate ID first so we can put it in the PDF
        String publicId = "CERT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        certificate.setPublicCertificateId(publicId);

        // Generate PDF
        byte[] pdfBytes = pdfGeneratorService.generateCertificatePdf(noc, certificate);

        // Calculate SHA-256 checksum
        String checksum = calculateChecksum(pdfBytes);
        certificate.setChecksum(checksum);

        // Store Artifact
        String filename = "cert-" + publicId + ".pdf";
        String artifactRef = documentStorageService.storeDocument(filename, "application/pdf", new java.io.ByteArrayInputStream(pdfBytes));
        certificate.setArtifactReference(artifactRef);

        try {
            certificate = certificateRepository.save(certificate);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Concurrency safe handling: Another thread inserted the certificate simultaneously
            existingCert = certificateRepository.findByApplicationId(noc.getId());
            if (existingCert.isPresent()) {
                return mapToResponse(existingCert.get());
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to resolve certificate concurrency conflict");
        }

        auditLogger.log(AuditEventType.CERTIFICATE_GENERATED, certificate.getUuid(), "Certificate generated: " + certificate.getPublicCertificateId());

        return mapToResponse(certificate);
    }

    public CertificateResponse getCertificate(UUID applicationId) {
        UUID orgId = TenantContext.getCurrentOrganizationId();
        Long internalOrgId = TenantContext.getCurrentInternalOrgId();
        if (orgId == null && internalOrgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing organization context");
        }

        NocApplication noc = nocRepository.findByUuid(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if ((internalOrgId != null && !noc.getOrgId().equals(internalOrgId))
                || (noc.getOrganizationId() != null && orgId != null && !noc.getOrganizationId().equals(orgId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }

        if (!TenantContext.hasRole("ADMIN") && !TenantContext.hasRole("OFFICER") && !TenantContext.hasRole("APPROVAL_AUTHORITY")) {
            // Must be citizen, verify ownership
            if (noc.getApplicantId() != null && !noc.getApplicantId().equals(TenantContext.getCurrentUserId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to access this certificate");
            }
        }

        NocCertificate certificate = certificateRepository.findByApplicationId(noc.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Certificate not found"));

        return mapToResponse(certificate);
    }

    private CertificateResponse mapToResponse(NocCertificate certificate) {
        return new CertificateResponse(
                certificate.getPublicCertificateId(),
                certificate.getApplicationUuid() != null ? certificate.getApplicationUuid() : certificate.getUuid(),
                certificate.getStatus(),
                certificate.getIssuedAt(),
                certificate.getArtifactReference(),
                certificate.getChecksum()
        );
    }

    private String calculateChecksum(byte[] data) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate checksum", e);
        }
    }
}
