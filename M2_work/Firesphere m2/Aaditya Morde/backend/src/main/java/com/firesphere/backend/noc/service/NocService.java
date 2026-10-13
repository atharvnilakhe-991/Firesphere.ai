package com.firesphere.backend.noc.service;

import com.firesphere.backend.common.audit.AuditEventType;
import com.firesphere.backend.common.audit.AuditLogger;
import com.firesphere.backend.common.security.Role;
import com.firesphere.backend.common.security.TenantContext;
import com.firesphere.backend.noc.dto.CreateNocRequest;
import com.firesphere.backend.noc.dto.NocResponse;
import com.firesphere.backend.noc.dto.UpdateNocRequest;
import com.firesphere.backend.noc.entity.NocApplication;
import com.firesphere.backend.noc.entity.NocStatus;
import com.firesphere.backend.noc.repository.NocApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
public class NocService {

    private final NocApplicationRepository nocRepository;
    private final AuditLogger auditLogger;

    public NocService(NocApplicationRepository nocRepository, AuditLogger auditLogger) {
        this.nocRepository = nocRepository;
        this.auditLogger = auditLogger;
    }

    public NocResponse createApplication(CreateNocRequest request) {
        UUID applicantId = TenantContext.getCurrentUserId();
        UUID orgId = TenantContext.getCurrentOrganizationId();

        if (applicantId == null || orgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing security context");
        }

        NocApplication noc = new NocApplication();
        noc.setApplicantId(applicantId);
        noc.setOrganizationId(orgId);
        noc.setTitle(request.title());
        noc.setDescription(request.description());
        noc.setBuildingName(request.buildingName());
        noc.setAddress(request.address());
        noc.setStatus(NocStatus.DRAFT);

        nocRepository.save(noc);
        auditLogger.log(AuditEventType.NOC_CREATED, noc.getId(), "NOC Application Created in DRAFT");

        return toDto(noc);
    }

    public NocResponse getApplication(UUID id) {
        NocApplication noc = getSecuredNoc(id);
        return toDto(noc);
    }

    public Page<NocResponse> listApplications(Pageable pageable) {
        UUID orgId = TenantContext.getCurrentOrganizationId();
        if (orgId == null) {
            return Page.empty(pageable);
        }

        Page<NocApplication> apps;
        if (TenantContext.hasRole(Role.CITIZEN.name())) {
            // Citizen sees only their own applications
            apps = nocRepository.findAllByApplicantIdAndOrganizationId(TenantContext.getCurrentUserId(), orgId, pageable);
        } else {
            // Officers/Admins see all in the org
            apps = nocRepository.findAllByOrganizationId(orgId, pageable);
        }
        
        return apps.map(this::toDto);
    }

    public NocResponse updateDraft(UUID id, UpdateNocRequest request) {
        NocApplication noc = getSecuredNoc(id);

        if (!noc.getApplicantId().equals(TenantContext.getCurrentUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the applicant can update the draft");
        }

        if (noc.getStatus() != NocStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application is not in DRAFT state and cannot be edited");
        }

        if (request.title() != null && !request.title().isBlank()) noc.setTitle(request.title());
        if (request.description() != null) noc.setDescription(request.description());
        if (request.buildingName() != null && !request.buildingName().isBlank()) noc.setBuildingName(request.buildingName());
        if (request.address() != null && !request.address().isBlank()) noc.setAddress(request.address());

        nocRepository.save(noc);
        auditLogger.log(AuditEventType.NOC_UPDATED, noc.getId(), "NOC Application Draft Updated");

        return toDto(noc);
    }



    private NocApplication getSecuredNoc(UUID id) {
        UUID orgId = TenantContext.getCurrentOrganizationId();
        if (orgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing organization context");
        }

        NocApplication noc = nocRepository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (TenantContext.hasRole(Role.CITIZEN.name())) {
            UUID currentUserId = TenantContext.getCurrentUserId();
            if (!noc.getApplicantId().equals(currentUserId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to access this application");
            }
        }

        return noc;
    }

    private NocResponse toDto(NocApplication noc) {
        return new NocResponse(
                noc.getId(),
                noc.getApplicantId(),
                noc.getOrganizationId(),
                noc.getTitle(),
                noc.getDescription(),
                noc.getBuildingName(),
                noc.getAddress(),
                noc.getStatus(),
                noc.getCreatedAt(),
                noc.getUpdatedAt()
        );
    }
}
