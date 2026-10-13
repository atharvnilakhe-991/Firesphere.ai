package com.firesphere.backend.checklist.dto;

import jakarta.validation.constraints.NotBlank;

public record GenerateChecklistRequest(
        @NotBlank String checklistType
) {}
