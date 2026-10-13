package com.firesphere.backend.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(min = 8) String password
) {}
