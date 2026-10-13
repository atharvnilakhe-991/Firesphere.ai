package com.firesphere.backend.checklist.dto;

import java.util.UUID;

public record CreateInspectionChecklistRequest(
        UUID templateId
) {}
