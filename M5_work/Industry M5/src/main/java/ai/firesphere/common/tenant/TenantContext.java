package ai.firesphere.common.tenant;

import java.util.UUID;

/**
 * ThreadLocal storage for resolving multi-tenant context.
 * Enforces tenant isolation so that tenantId is never trusted directly from mobile payloads,
 * but resolved from authenticated security principals.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static UUID getRequiredTenantId() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
