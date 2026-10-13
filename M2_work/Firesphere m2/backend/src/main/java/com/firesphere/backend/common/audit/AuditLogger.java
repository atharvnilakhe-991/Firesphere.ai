package com.firesphere.backend.common.audit;

import java.util.UUID;

public interface AuditLogger {
    void log(AuditEventType type, UUID targetId, String details);
}
