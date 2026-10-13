package ai.firesphere.operations.officer.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Validates security principal permissions and badge ownership for officer operations.
 * Allows supervisory roles (ADMIN, DISPATCHER, COMMANDER, CHIEF) full access,
 * while restricting officers to their own assigned tasks and workload metrics.
 */
@Component
public class OfficerSecurityValidator {

    private static final Logger log = LoggerFactory.getLogger(OfficerSecurityValidator.class);

    private static final Set<String> SUPERVISORY_ROLES = Set.of(
            "ROLE_ADMIN",
            "ROLE_DISPATCHER",
            "ROLE_COMMANDER",
            "ROLE_CHIEF",
            "ROLE_STATION_CAPTAIN"
    );

    /**
     * Asserts that the currently authenticated principal is allowed to access
     * tasks or workload for the target officer badge.
     *
     * @param targetOfficerBadge the badge of the officer being accessed
     * @throws AccessDeniedException if the principal is not allowed
     */
    public void validateOfficerAccess(String targetOfficerBadge) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            log.warn("[OFFICER SECURITY] Unauthenticated access attempt to officer records for badge '{}'", targetOfficerBadge);
            throw new AccessDeniedException("Full authentication is required to access officer operations");
        }

        boolean isSupervisor = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(SUPERVISORY_ROLES::contains);

        if (isSupervisor) {
            return;
        }

        String principalName = auth.getName();
        if (targetOfficerBadge != null && !targetOfficerBadge.isBlank()) {
            boolean matchesBadge = principalName.equalsIgnoreCase(targetOfficerBadge)
                    || principalName.equalsIgnoreCase("officer-" + targetOfficerBadge)
                    || ("officer-" + principalName).equalsIgnoreCase(targetOfficerBadge);

            if (!matchesBadge) {
                log.warn("[OFFICER SECURITY] Principal '{}' attempted to access data for badge '{}'",
                        principalName, targetOfficerBadge);
                throw new AccessDeniedException(
                        "Access denied: Principal '" + principalName + "' is not authorized to access records for badge '" + targetOfficerBadge + "'"
                );
            }
        }
    }

    /**
     * Asserts that the currently authenticated principal holds a supervisory role.
     *
     * @param action the privileged action being attempted
     * @throws AccessDeniedException if the principal does not possess supervisory authority
     */
    public void validateSupervisorAccess(String action) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            log.warn("[OFFICER SECURITY] Unauthenticated attempt to perform privileged action '{}'", action);
            throw new AccessDeniedException("Full authentication with supervisory authority is required to perform: " + action);
        }

        boolean isSupervisor = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(SUPERVISORY_ROLES::contains);

        if (!isSupervisor) {
            String principalName = auth.getName();
            log.warn("[OFFICER SECURITY] Principal '{}' attempted privileged action '{}' without supervisory role",
                    principalName, action);
            throw new AccessDeniedException(
                    "Access denied: Principal '" + principalName + "' is not authorized to perform '" + action + "'"
            );
        }
    }
}
