package com.firesphere.backend.organization.controller;

import com.firesphere.backend.organization.dto.OrganizationResponse;
import com.firesphere.backend.organization.dto.UpdateOrganizationRequest;
import com.firesphere.backend.organization.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organizations", description = "Endpoints for managing the current user's organization")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PreAuthorize("hasAuthority('organization:read')")
    @GetMapping("/me")
    @Operation(summary = "Get the current authenticated user's organization profile")
    public ResponseEntity<OrganizationResponse> getMyOrganization() {
        return ResponseEntity.ok(organizationService.getMyOrganization());
    }

    @PreAuthorize("hasAuthority('organization:write')")
    @PatchMapping("/me")
    @Operation(summary = "Update the current organization's profile")
    public ResponseEntity<OrganizationResponse> updateMyOrganization(@Valid @RequestBody UpdateOrganizationRequest request) {
        return ResponseEntity.ok(organizationService.updateMyOrganization(request));
    }
}
