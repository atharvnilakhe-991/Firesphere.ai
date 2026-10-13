package com.firesphere.backend.noc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateNocRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        @NotBlank @Size(max = 255) String buildingName,
        @NotBlank String address
) {}
