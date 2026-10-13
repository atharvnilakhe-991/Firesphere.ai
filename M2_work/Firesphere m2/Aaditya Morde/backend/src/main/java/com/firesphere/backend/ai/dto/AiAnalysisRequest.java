package com.firesphere.backend.ai.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AiAnalysisRequest(
        @NotNull UUID documentId,
        String analysisType
) {}
