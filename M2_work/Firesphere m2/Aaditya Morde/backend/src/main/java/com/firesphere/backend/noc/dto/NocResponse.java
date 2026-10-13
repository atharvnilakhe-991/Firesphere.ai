package com.firesphere.backend.noc.dto;

import com.firesphere.backend.noc.entity.NocStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NocResponse(
        UUID id,
        UUID applicantId,
        UUID organizationId,
        String title,
        String description,
        String buildingName,
        String address,
        NocStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
