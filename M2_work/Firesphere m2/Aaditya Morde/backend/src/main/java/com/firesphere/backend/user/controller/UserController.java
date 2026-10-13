package com.firesphere.backend.user.controller;

import com.firesphere.backend.user.dto.AdminUpdateUserRequest;
import com.firesphere.backend.user.dto.CreateUserRequest;
import com.firesphere.backend.user.dto.UpdateUserRequest;
import com.firesphere.backend.user.dto.UserResponse;
import com.firesphere.backend.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Endpoints for managing users and roles within an organization")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PreAuthorize("hasAuthority('user:read')")
    @GetMapping("/me")
    @Operation(summary = "Get the current authenticated user's profile")
    public ResponseEntity<UserResponse> getCurrentUser() {
        return ResponseEntity.ok(userService.getCurrentUser());
    }

    @PreAuthorize("hasAuthority('user:write')")
    @PatchMapping("/me")
    @Operation(summary = "Update the current user's profile")
    public ResponseEntity<UserResponse> updateCurrentUser(@Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateCurrentUser(request));
    }

    @PreAuthorize("hasAuthority('organization:read')") // Typically Officer+
    @GetMapping
    @Operation(summary = "List all users in the current user's organization")
    public ResponseEntity<Page<UserResponse>> getAllUsersInMyOrganization(@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(userService.findAllUsersInMyOrganization(pageable));
    }

    @PreAuthorize("hasAuthority('organization:read')")
    @GetMapping("/{userId}")
    @Operation(summary = "Get a specific user by ID within the organization")
    public ResponseEntity<UserResponse> getUserByIdInMyOrganization(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.findUserByIdInMyOrganization(userId));
    }

    @PreAuthorize("hasAuthority('organization:write')") // Typically Admin
    @PostMapping
    @Operation(summary = "Create a new user within the organization")
    public ResponseEntity<UserResponse> createUserInMyOrganization(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(userService.createUserInMyOrganization(request));
    }

    @PreAuthorize("hasAuthority('organization:write')")
    @PatchMapping("/{userId}")
    @Operation(summary = "Update an existing user's role or status")
    public ResponseEntity<UserResponse> adminUpdateUserInMyOrganization(@PathVariable UUID userId, @Valid @RequestBody AdminUpdateUserRequest request) {
        return ResponseEntity.ok(userService.adminUpdateUserInMyOrganization(userId, request));
    }
}
