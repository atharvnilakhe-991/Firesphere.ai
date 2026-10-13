package com.firesphere.backend.ai.client;

import com.firesphere.backend.ai.dto.ExternalAiRequest;
import com.firesphere.backend.ai.dto.ExternalAiResponse;

public interface AiAnalysisClient {
    ExternalAiResponse analyze(ExternalAiRequest request);
}
