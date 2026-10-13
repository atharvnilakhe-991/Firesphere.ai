package com.firesphere.backend.common.security;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("tenantSecurity")
public class TenantSecurity {

    public boolean isTenantMember(UUID orgId) {
        if (orgId == null) {
            return false;
        }
        UUID currentOrgId = TenantContext.getCurrentOrganizationId();
        return orgId.equals(currentOrgId);
    }
}
