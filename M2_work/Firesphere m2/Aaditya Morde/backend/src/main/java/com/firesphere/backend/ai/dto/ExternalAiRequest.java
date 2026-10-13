package com.firesphere.backend.ai.dto;

import java.util.UUID;

public record ExternalAiRequest(
        UUID runId,
        UUID applicationId,
        String documentReference,
        String analysisType
) {}
