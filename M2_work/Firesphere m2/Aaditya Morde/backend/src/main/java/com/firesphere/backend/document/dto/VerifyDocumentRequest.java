package com.firesphere.backend.document.dto;

import jakarta.validation.constraints.NotNull;

public record VerifyDocumentRequest(
        @NotNull Boolean approved,
        String rejectionReason
) {}
