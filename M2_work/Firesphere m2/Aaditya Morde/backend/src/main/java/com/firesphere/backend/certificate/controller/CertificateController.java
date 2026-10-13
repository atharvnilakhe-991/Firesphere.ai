package com.firesphere.backend.certificate.controller;

import com.firesphere.backend.certificate.dto.CertificateResponse;
import com.firesphere.backend.certificate.service.CertificateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/noc/applications/{applicationId}/certificate")
@Tag(name = "Certificates", description = "Endpoints for managing Fire NOC certificates")
public class CertificateController {

    private final CertificateService certificateService;

    public CertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('noc:certificate:generate')")
    @Operation(summary = "Generate a new Fire NOC certificate for an approved application")
    public ResponseEntity<CertificateResponse> generateCertificate(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(certificateService.generateCertificate(applicationId));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('noc:read')")
    @Operation(summary = "Get the generated Fire NOC certificate for an application")
    public ResponseEntity<CertificateResponse> getCertificate(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(certificateService.getCertificate(applicationId));
    }
}
