package com.firesphere.backend.checklist.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateChecklistItemRequest(
        @NotBlank String status,
        String remarks
) {}
