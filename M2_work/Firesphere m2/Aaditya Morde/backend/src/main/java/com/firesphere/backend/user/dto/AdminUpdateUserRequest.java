package com.firesphere.backend.user.dto;

import jakarta.validation.constraints.Pattern;

public record AdminUpdateUserRequest(
        String role,
        @Pattern(regexp = "^(ACTIVE|INACTIVE|SUSPENDED)$") String status
) {}
