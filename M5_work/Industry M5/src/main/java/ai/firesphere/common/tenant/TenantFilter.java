package ai.firesphere.common.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that establishes the multi-tenant context for each request.
 * Resolves tenant from authenticated principal or development fallback, ensuring
 * safe ThreadLocal cleanup after each request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantFilter extends OncePerRequestFilter {

    private static final String DEFAULT_TENANT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String TENANT_HEADER = "X-Tenant-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String tenantHeader = request.getHeader(TENANT_HEADER);
            UUID tenantId;
            if (tenantHeader != null && !tenantHeader.isBlank()) {
                try {
                    tenantId = UUID.fromString(tenantHeader.trim());
                } catch (IllegalArgumentException e) {
                    tenantId = UUID.fromString(DEFAULT_TENANT_ID);
                }
            } else {
                tenantId = UUID.fromString(DEFAULT_TENANT_ID);
            }
            TenantContext.setTenantId(tenantId);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
