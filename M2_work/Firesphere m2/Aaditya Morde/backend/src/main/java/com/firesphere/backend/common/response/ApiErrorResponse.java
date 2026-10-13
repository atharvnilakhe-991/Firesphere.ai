package com.firesphere.backend.common.response;

import java.time.OffsetDateTime;

public record ApiErrorResponse(
    OffsetDateTime timestamp,
    int status,
    String code,
    String message,
    String path,
    String traceId
) {}
