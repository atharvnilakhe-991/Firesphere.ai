package ai.firesphere.testing;

import ai.firesphere.common.tenant.TenantContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

/**
 * Test helper for establishing isolated security and tenant contexts during automated test runs.
 */
public final class MockSecurityHelper {

    private MockSecurityHelper() {}

    public static void setupSecurityContext(UUID tenantId, String username, List<String> roles) {
        TenantContext.setTenantId(tenantId);
        var authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority(r.startsWith("ROLE_") ? r : "ROLE_" + r))
                .toList();
        var auth = new UsernamePasswordAuthenticationToken(username, "N/A", authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    public static void clearSecurityContext() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }
}
