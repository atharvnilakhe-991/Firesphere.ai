package com.firesphere.backend.document.controller;

import com.firesphere.backend.document.dto.DocumentResponse;
import com.firesphere.backend.document.dto.VerifyDocumentRequest;
import com.firesphere.backend.document.service.NocDocumentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/noc/applications/{applicationId}/documents")
public class DocumentController {

    private final NocDocumentService documentService;

    public DocumentController(NocDocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('noc:update') or hasRole('CITIZEN')")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @PathVariable UUID applicationId,
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(documentService.uploadDocument(applicationId, documentType, file));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('noc:read') or hasRole('CITIZEN')")
    public ResponseEntity<List<DocumentResponse>> listDocuments(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(documentService.listDocuments(applicationId));
    }

    @PostMapping("/{documentId}/verify")
    @PreAuthorize("hasAuthority('noc:update')")
    public ResponseEntity<DocumentResponse> verifyDocument(
            @PathVariable UUID applicationId,
            @PathVariable UUID documentId,
            @Valid @RequestBody VerifyDocumentRequest request) {
        return ResponseEntity.ok(documentService.verifyDocument(applicationId, documentId, request));
    }
}
