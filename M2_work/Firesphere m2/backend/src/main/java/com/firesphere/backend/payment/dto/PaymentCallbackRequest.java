package com.firesphere.backend.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentCallbackRequest(
        @NotBlank String referenceId,
        @NotNull Boolean success,
        String transactionId
) {}
