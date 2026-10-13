package com.firesphere.backend.user.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String status,
        String role,
        UUID organizationId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
