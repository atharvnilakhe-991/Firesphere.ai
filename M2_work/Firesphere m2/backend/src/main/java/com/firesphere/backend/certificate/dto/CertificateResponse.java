package com.firesphere.backend.certificate.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CertificateResponse(
        String publicCertificateId,
        UUID applicationId,
        String status,
        OffsetDateTime issuedAt,
        String artifactReference,
        String checksum
) {}
