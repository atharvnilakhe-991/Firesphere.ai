package com.firesphere.backend.organization.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String name,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
