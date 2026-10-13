package com.firesphere.backend.common.audit;

import com.firesphere.backend.common.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SimpleAuditLogger implements AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(SimpleAuditLogger.class);

    @Override
    public void log(AuditEventType type, UUID targetId, String details) {
        UUID actorId = TenantContext.getCurrentUserId();
        UUID tenantId = TenantContext.getCurrentOrganizationId();
        
        log.info("AUDIT EVENT: [{}] Actor: [{}] Tenant: [{}] Target: [{}] Details: {}", 
                type, 
                actorId != null ? actorId : "SYSTEM", 
                tenantId != null ? tenantId : "SYSTEM", 
                targetId, 
                details != null ? details : "");
    }
}
