package com.firesphere.backend.noc.dto;

import jakarta.validation.constraints.Size;

public record UpdateNocRequest(
        @Size(max = 255) String title,
        String description,
        @Size(max = 255) String buildingName,
        String address
) {}
