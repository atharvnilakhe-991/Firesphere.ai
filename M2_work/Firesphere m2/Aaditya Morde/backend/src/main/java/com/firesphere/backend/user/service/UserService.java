package com.firesphere.backend.user.service;

import com.firesphere.backend.auth.entity.User;
import com.firesphere.backend.auth.repository.UserRepository;
import com.firesphere.backend.common.security.Role;
import com.firesphere.backend.common.security.TenantContext;
import com.firesphere.backend.user.dto.AdminUpdateUserRequest;
import com.firesphere.backend.user.dto.CreateUserRequest;
import com.firesphere.backend.user.dto.UpdateUserRequest;
import com.firesphere.backend.user.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.firesphere.backend.common.audit.AuditLogger auditLogger;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, com.firesphere.backend.common.audit.AuditLogger auditLogger) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogger = auditLogger;
    }

    public UserResponse getCurrentUser() {
        UUID currentUserId = TenantContext.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return toDto(user);
    }

    public UserResponse updateCurrentUser(UpdateUserRequest request) {
        UUID currentUserId = TenantContext.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        userRepository.save(user);

        return toDto(user);
    }

    public Page<UserResponse> findAllUsersInMyOrganization(Pageable pageable) {
        UUID currentOrgId = TenantContext.getCurrentOrganizationId();
        if (currentOrgId == null) {
            return Page.empty(pageable);
        }
        return userRepository.findAllByOrganizationId(currentOrgId, pageable)
                .map(this::toDto);
    }

    public UserResponse findUserByIdInMyOrganization(UUID userId) {
        UUID currentOrgId = TenantContext.getCurrentOrganizationId();
        if (currentOrgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization context missing");
        }
        User user = userRepository.findByIdAndOrganizationId(userId, currentOrgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found in organization"));
        return toDto(user);
    }

    public UserResponse createUserInMyOrganization(CreateUserRequest request) {
        UUID currentOrgId = TenantContext.getCurrentOrganizationId();
        if (currentOrgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization context missing");
        }

        if (userRepository.existsByEmailIgnoreCase(request.email().trim().toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        try {
            Role.valueOf(request.role()); // Validate role
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role");
        }

        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setStatus("ACTIVE");
        user.setRole(request.role());
        user.setOrganizationId(currentOrgId);

        userRepository.save(user);

        auditLogger.log(com.firesphere.backend.common.audit.AuditEventType.USER_CREATED, user.getId(), "User created by admin with role: " + user.getRole());

        return toDto(user);
    }

    public UserResponse adminUpdateUserInMyOrganization(UUID userId, AdminUpdateUserRequest request) {
        UUID currentOrgId = TenantContext.getCurrentOrganizationId();
        if (currentOrgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization context missing");
        }

        User user = userRepository.findByIdAndOrganizationId(userId, currentOrgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found in organization"));

        boolean roleChanged = false;
        boolean statusChanged = false;
        String oldRole = user.getRole();
        String oldStatus = user.getStatus();

        if (request.role() != null && !request.role().isBlank()) {
            try {
                Role.valueOf(request.role());
                if (!request.role().equals(oldRole)) {
                    user.setRole(request.role());
                    roleChanged = true;
                }
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role");
            }
        }

        if (request.status() != null && !request.status().isBlank()) {
            if (!request.status().equals(oldStatus)) {
                user.setStatus(request.status());
                statusChanged = true;
            }
        }

        userRepository.save(user);

        if (roleChanged) {
            auditLogger.log(com.firesphere.backend.common.audit.AuditEventType.USER_ROLE_CHANGED, user.getId(), "Role changed from " + oldRole + " to " + user.getRole());
        }
        if (statusChanged) {
            auditLogger.log(com.firesphere.backend.common.audit.AuditEventType.USER_STATUS_CHANGED, user.getId(), "Status changed from " + oldStatus + " to " + user.getStatus());
        }

        return toDto(user);
    }

    private UserResponse toDto(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getStatus(),
                user.getRole(),
                user.getOrganizationId(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
