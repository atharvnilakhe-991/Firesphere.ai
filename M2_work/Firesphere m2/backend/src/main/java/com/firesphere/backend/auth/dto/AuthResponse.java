package com.firesphere.backend.auth.dto;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        UUID userId,
        String email,
        String status
) {
}
