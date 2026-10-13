package com.firesphere.backend.organization.service;

import com.firesphere.backend.organization.dto.OrganizationResponse;
import com.firesphere.backend.organization.dto.UpdateOrganizationRequest;
import com.firesphere.backend.organization.entity.Organization;
import com.firesphere.backend.organization.repository.OrganizationRepository;
import com.firesphere.backend.common.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final com.firesphere.backend.common.audit.AuditLogger auditLogger;

    public OrganizationService(OrganizationRepository organizationRepository, com.firesphere.backend.common.audit.AuditLogger auditLogger) {
        this.organizationRepository = organizationRepository;
        this.auditLogger = auditLogger;
    }

    public OrganizationResponse getMyOrganization() {
        UUID currentOrgId = TenantContext.getCurrentOrganizationId();
        Long internalOrgId = TenantContext.getCurrentInternalOrgId();
        if (currentOrgId == null && internalOrgId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not associated with any organization");
        }

        Organization org = null;
        if (currentOrgId != null) {
            org = organizationRepository.findByUuid(currentOrgId).orElse(null);
        }
        if (org == null && internalOrgId != null) {
            org = organizationRepository.findById(internalOrgId).orElse(null);
        }
        if (org == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
        }

        return toDto(org);
    }

    public OrganizationResponse updateMyOrganization(UpdateOrganizationRequest request) {
        UUID currentOrgId = TenantContext.getCurrentOrganizationId();
        Long internalOrgId = TenantContext.getCurrentInternalOrgId();
        if (currentOrgId == null && internalOrgId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not associated with any organization");
        }

        Organization org = null;
        if (currentOrgId != null) {
            org = organizationRepository.findByUuid(currentOrgId).orElse(null);
        }
        if (org == null && internalOrgId != null) {
            org = organizationRepository.findById(internalOrgId).orElse(null);
        }
        if (org == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
        }

        org.setName(request.name());
        organizationRepository.save(org);

        auditLogger.log(com.firesphere.backend.common.audit.AuditEventType.ORGANIZATION_UPDATED, org.getUuid(), "Organization updated");

        return toDto(org);
    }

    private OrganizationResponse toDto(Organization org) {
        return new OrganizationResponse(
                org.getUuid(),
                org.getName(),
                org.getStatus(),
                org.getCreatedAt(),
                org.getUpdatedAt()
        );
    }
}
