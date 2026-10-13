package com.firesphere.backend.document.service;

import com.firesphere.backend.common.audit.AuditEventType;
import com.firesphere.backend.common.audit.AuditLogger;
import com.firesphere.backend.common.security.Role;
import com.firesphere.backend.common.security.TenantContext;
import com.firesphere.backend.document.dto.DocumentResponse;
import com.firesphere.backend.document.dto.VerifyDocumentRequest;
import com.firesphere.backend.document.entity.DocumentStatus;
import com.firesphere.backend.document.entity.NocDocument;
import com.firesphere.backend.document.repository.NocDocumentRepository;
import com.firesphere.backend.noc.entity.NocApplication;
import com.firesphere.backend.noc.entity.NocStatus;
import com.firesphere.backend.noc.repository.NocApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NocDocumentService {

    private final NocDocumentRepository documentRepository;
    private final NocApplicationRepository nocRepository;
    private final DocumentStorageService storageService;
    private final AuditLogger auditLogger;

    public NocDocumentService(NocDocumentRepository documentRepository, NocApplicationRepository nocRepository, DocumentStorageService storageService, AuditLogger auditLogger) {
        this.documentRepository = documentRepository;
        this.nocRepository = nocRepository;
        this.storageService = storageService;
        this.auditLogger = auditLogger;
    }

    public DocumentResponse uploadDocument(UUID applicationId, String documentType, MultipartFile file) {
        UUID orgId = TenantContext.getCurrentOrganizationId();
        UUID userId = TenantContext.getCurrentUserId();

        NocApplication noc = nocRepository.findByIdAndOrganizationId(applicationId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (!noc.getApplicantId().equals(userId) && !TenantContext.hasRole(Role.ADMIN.name())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the applicant can upload documents");
        }

        if (noc.getStatus() != NocStatus.SUBMITTED && noc.getStatus() != NocStatus.CORRECTION_REQUIRED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application is not in a state accepting documents");
        }

        String storageRef;
        try {
            storageRef = storageService.storeDocument(file.getOriginalFilename(), file.getContentType(), file.getInputStream());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store document", e);
        }

        NocDocument doc = new NocDocument();
        doc.setApplicationId(applicationId);
        doc.setOrganizationId(orgId);
        doc.setDocumentType(documentType);
        doc.setOriginalFileName(file.getOriginalFilename());
        doc.setContentType(file.getContentType());
        doc.setFileSize(file.getSize());
        doc.setStorageReference(storageRef);
        doc.setUploadedBy(userId);

        documentRepository.save(doc);
        auditLogger.log(AuditEventType.DOCUMENT_UPLOADED, doc.getId(), "Document uploaded");

        return toDto(doc);
    }

    public List<DocumentResponse> listDocuments(UUID applicationId) {
        UUID orgId = TenantContext.getCurrentOrganizationId();
        NocApplication noc = nocRepository.findByIdAndOrganizationId(applicationId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (!noc.getApplicantId().equals(TenantContext.getCurrentUserId()) && !TenantContext.hasRole(Role.OFFICER.name()) && !TenantContext.hasRole(Role.ADMIN.name())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to view documents");
        }

        return documentRepository.findAllByApplicationIdAndOrganizationId(applicationId, orgId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public DocumentResponse verifyDocument(UUID applicationId, UUID documentId, VerifyDocumentRequest request) {
        UUID orgId = TenantContext.getCurrentOrganizationId();
        NocApplication noc = nocRepository.findByIdAndOrganizationId(applicationId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (noc.getStatus() != NocStatus.DOCUMENT_VERIFICATION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application is not in DOCUMENT_VERIFICATION state");
        }

        NocDocument doc = documentRepository.findByIdAndApplicationIdAndOrganizationId(documentId, applicationId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        if (request.approved()) {
            doc.setStatus(DocumentStatus.VERIFIED);
            doc.setVerifiedBy(TenantContext.getCurrentUserId());
            doc.setVerifiedAt(OffsetDateTime.now());
            doc.setRejectionReason(null);
            auditLogger.log(AuditEventType.DOCUMENT_VERIFIED, doc.getId(), "Document verified");
        } else {
            doc.setStatus(DocumentStatus.REJECTED);
            doc.setVerifiedBy(TenantContext.getCurrentUserId());
            doc.setVerifiedAt(OffsetDateTime.now());
            if (request.rejectionReason() == null || request.rejectionReason().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejection reason required");
            }
            doc.setRejectionReason(request.rejectionReason());
            auditLogger.log(AuditEventType.DOCUMENT_REJECTED, doc.getId(), "Document rejected");
        }

        documentRepository.save(doc);
        return toDto(doc);
    }

    private DocumentResponse toDto(NocDocument doc) {
        return new DocumentResponse(
                doc.getId(), doc.getApplicationId(), doc.getDocumentType(), doc.getOriginalFileName(),
                doc.getStorageReference(), doc.getContentType(), doc.getFileSize(), doc.getStatus(),
                doc.getUploadedBy(), doc.getUploadedAt(), doc.getVerifiedBy(), doc.getVerifiedAt(), doc.getRejectionReason()
        );
    }
}
