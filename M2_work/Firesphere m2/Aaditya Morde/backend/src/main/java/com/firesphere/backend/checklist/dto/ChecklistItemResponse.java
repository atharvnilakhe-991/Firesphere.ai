package com.firesphere.backend.checklist.dto;

import java.util.UUID;

public record ChecklistItemResponse(
        UUID id,
        String code,
        String description,
        Boolean required,
        String status,
        String remarks
) {}
