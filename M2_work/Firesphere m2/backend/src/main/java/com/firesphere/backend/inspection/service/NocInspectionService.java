package com.firesphere.backend.inspection.service;

import com.firesphere.backend.auth.entity.User;
import com.firesphere.backend.auth.repository.UserRepository;
import com.firesphere.backend.common.audit.AuditEventType;
import com.firesphere.backend.common.audit.AuditLogger;
import com.firesphere.backend.common.security.Role;
import com.firesphere.backend.common.security.TenantContext;
import com.firesphere.backend.inspection.dto.InspectionResponse;
import com.firesphere.backend.inspection.dto.OfficerAssignmentResponse;
import com.firesphere.backend.inspection.dto.ScheduleInspectionRequest;
import com.firesphere.backend.inspection.dto.UpdateInspectionRequest;
import com.firesphere.backend.inspection.entity.InspectionStatus;
import com.firesphere.backend.inspection.entity.NocInspection;
import com.firesphere.backend.inspection.repository.NocInspectionRepository;
import com.firesphere.backend.noc.entity.NocApplication;
import com.firesphere.backend.noc.entity.NocStatus;
import com.firesphere.backend.noc.repository.NocApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NocInspectionService {

    private final NocInspectionRepository inspectionRepository;
    private final NocApplicationRepository nocRepository;
    private final UserRepository userRepository;
    private final AuditLogger auditLogger;

    public NocInspectionService(NocInspectionRepository inspectionRepository, NocApplicationRepository nocRepository, UserRepository userRepository, AuditLogger auditLogger) {
        this.inspectionRepository = inspectionRepository;
        this.nocRepository = nocRepository;
        this.userRepository = userRepository;
        this.auditLogger = auditLogger;
    }

    public InspectionResponse scheduleInspection(UUID applicationId, ScheduleInspectionRequest request) {
        UUID orgId = TenantContext.getCurrentOrganizationId();
        Long internalOrgId = TenantContext.getCurrentInternalOrgId();
        NocApplication noc = nocRepository.findByUuid(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (internalOrgId != null && !noc.getOrgId().equals(internalOrgId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }

        if (noc.getStatus() != NocStatus.INSPECTION_SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inspection can only be scheduled when application is in INSPECTION_SCHEDULED state");
        }

        User assignedOfficer = userRepository.findByUuid(request.assignedOfficerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assigned officer not found"));

        if (!assignedOfficer.getOrgId().equals(noc.getOrgId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assigned officer does not belong to the same organization");
        }
        
        if (!"ACTIVE".equals(assignedOfficer.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assigned officer is inactive");
        }

        if (!Role.OFFICER.name().equals(assignedOfficer.getRole()) && !Role.ADMIN.name().equals(assignedOfficer.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assigned user is not an officer");
        }

        NocInspection inspection = new NocInspection();
        inspection.setNocApplicationId(noc.getId());
        inspection.setApplicationId(applicationId);
        inspection.setOrgId(noc.getOrgId());
        inspection.setOrganizationId(orgId);
        inspection.setInspectionType(request.inspectionType());
        inspection.setScheduledAt(request.scheduledAt());
        inspection.setAssignedOfficer(assignedOfficer.getUuid());

        inspection = inspectionRepository.save(inspection);
        auditLogger.log(AuditEventType.INSPECTION_SCHEDULED, inspection.getUuid(), "Inspection scheduled");

        return toDto(inspection);
    }

    public List<InspectionResponse> listInspections(UUID applicationId) {
        Long internalOrgId = TenantContext.getCurrentInternalOrgId();
        NocApplication noc = nocRepository.findByUuid(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (internalOrgId != null && !noc.getOrgId().equals(internalOrgId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant access prohibited");
        }

        if (noc.getApplicantId() != null && !noc.getApplicantId().equals(TenantContext.getCurrentUserId())
                && !TenantContext.hasRole(Role.OFFICER.name()) && !TenantContext.hasRole(Role.ADMIN.name())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to view inspections");
        }

        return inspectionRepository.findAllByNocApplicationIdAndOrgId(noc.getId(), noc.getOrgId()).stream()
                .peek(i -> i.setApplicationId(noc.getUuid()))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public InspectionResponse getInspection(UUID applicationId, UUID inspectionId) {
        Long internalOrgId = TenantContext.getCurrentInternalOrgId();
        NocApplication noc = nocRepository.findByUuid(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (internalOrgId != null && !noc.getOrgId().equals(internalOrgId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant access prohibited");
        }

        if (noc.getApplicantId() != null && !noc.getApplicantId().equals(TenantContext.getCurrentUserId())
                && !TenantContext.hasRole(Role.OFFICER.name()) && !TenantContext.hasRole(Role.ADMIN.name())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to view inspection");
        }

        NocInspection inspection = inspectionRepository.findByUuidAndOrgId(inspectionId, noc.getOrgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inspection not found"));

        inspection.setApplicationId(noc.getUuid());
        return toDto(inspection);
    }

    public InspectionResponse startInspection(UUID applicationId, UUID inspectionId) {
        Long internalOrgId = TenantContext.getCurrentInternalOrgId();
        NocApplication noc = nocRepository.findByUuid(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (internalOrgId != null && !noc.getOrgId().equals(internalOrgId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant access prohibited");
        }

        if (noc.getStatus() != NocStatus.INSPECTION_IN_PROGRESS) {
             throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application must be in INSPECTION_IN_PROGRESS state to start inspection");
        }

        NocInspection inspection = inspectionRepository.findByUuidAndOrgId(inspectionId, noc.getOrgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inspection not found"));

        if (inspection.getAssignedOfficer() != null && !inspection.getAssignedOfficer().equals(TenantContext.getCurrentUserId())
                && !TenantContext.hasRole(Role.ADMIN.name())) {
             throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned officer can start this inspection");
        }

        inspection.setStatus(InspectionStatus.IN_PROGRESS);
        inspection.setStartedAt(OffsetDateTime.now());

        inspection = inspectionRepository.save(inspection);
        auditLogger.log(AuditEventType.INSPECTION_STARTED, inspection.getUuid(), "Inspection started");

        inspection.setApplicationId(noc.getUuid());
        return toDto(inspection);
    }

    public InspectionResponse completeInspection(UUID applicationId, UUID inspectionId, UpdateInspectionRequest request) {
        Long internalOrgId = TenantContext.getCurrentInternalOrgId();
        NocApplication noc = nocRepository.findByUuid(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (internalOrgId != null && !noc.getOrgId().equals(internalOrgId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant access prohibited");
        }

        if (noc.getStatus() != NocStatus.INSPECTION_COMPLETED && noc.getStatus() != NocStatus.INSPECTION_IN_PROGRESS) {
             throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application must be in INSPECTION_COMPLETED state to complete inspection record");
        }

        NocInspection inspection = inspectionRepository.findByUuidAndOrgId(inspectionId, noc.getOrgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inspection not found"));

        if (inspection.getAssignedOfficer() != null && !inspection.getAssignedOfficer().equals(TenantContext.getCurrentUserId())
                && !TenantContext.hasRole(Role.ADMIN.name())) {
             throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned officer can complete this inspection");
        }
        
        if (inspection.getStatus() != InspectionStatus.IN_PROGRESS) {
             throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inspection must be in progress to be completed");
        }

        inspection.setStatus(InspectionStatus.COMPLETED);
        inspection.setCompletedAt(OffsetDateTime.now());
        if (request.remarks() != null) inspection.setRemarks(request.remarks());
        if (request.location() != null) inspection.setLocation(request.location());

        inspection = inspectionRepository.save(inspection);
        auditLogger.log(AuditEventType.INSPECTION_COMPLETED, inspection.getUuid(), "Inspection completed");

        inspection.setApplicationId(noc.getUuid());
        return toDto(inspection);
    }

    private InspectionResponse toDto(NocInspection inspection) {
        return new InspectionResponse(
                inspection.getUuid(),
                inspection.getApplicationId(),
                inspection.getInspectionType(),
                inspection.getScheduledAt(),
                inspection.getAssignedOfficer(),
                inspection.getStatus(),
                inspection.getStartedAt(),
                inspection.getCompletedAt(),
                inspection.getRemarks(),
                inspection.getLocation()
        );
    }

    public List<OfficerAssignmentResponse> getAssignmentsForCurrentOfficer() {
        UUID currentUserId = TenantContext.getCurrentUserId();
        Long internalOrgId = TenantContext.getCurrentInternalOrgId();
        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated");
        }

        List<NocInspection> inspections;
        if (internalOrgId != null) {
            inspections = inspectionRepository.findAllByAssignedOfficerAndOrgId(currentUserId, internalOrgId);
        } else {
            inspections = inspectionRepository.findAllByAssignedOfficer(currentUserId);
        }

        return inspections.stream().map(this::toAssignmentDto).collect(Collectors.toList());
    }

    public List<OfficerAssignmentResponse> getAllInspectionsForOrganization() {
        Long internalOrgId = TenantContext.getCurrentInternalOrgId();
        List<NocInspection> inspections;
        if (internalOrgId != null) {
            inspections = inspectionRepository.findAllByOrgId(internalOrgId);
        } else {
            inspections = inspectionRepository.findAll();
        }
        return inspections.stream().map(this::toAssignmentDto).collect(Collectors.toList());
    }

    private OfficerAssignmentResponse toAssignmentDto(NocInspection inspection) {
        String reference = inspection.getInspectionNumber() != null ? inspection.getInspectionNumber() : "INSP-" + inspection.getUuid().toString().substring(0, 8).toUpperCase();
        UUID applicationId = inspection.getApplicationId();
        String applicationNumber = null;
        String title = "Fire Safety Inspection";
        String address = "Premises Address";
        String propertyType = "Commercial";

        if (inspection.getNocApplicationId() != null) {
            java.util.Optional<NocApplication> nocOpt = nocRepository.findById(inspection.getNocApplicationId());
            if (nocOpt.isPresent()) {
                NocApplication noc = nocOpt.get();
                applicationId = noc.getUuid();
                applicationNumber = "NOC-" + noc.getUuid().toString().substring(0, 8).toUpperCase();
                if (noc.getTitle() != null) title = noc.getTitle();
                if (noc.getAddress() != null) address = noc.getAddress();
                if (noc.getBuildingName() != null) propertyType = noc.getBuildingName();
            }
        }

        String status = inspection.getInspectionStatus() != null ? inspection.getInspectionStatus() : (inspection.getStatus() != null ? inspection.getStatus().name() : "ASSIGNED");
        OffsetDateTime scheduledAt = inspection.getScheduledAt() != null ? inspection.getScheduledAt() : (inspection.getScheduledDate() != null ? inspection.getScheduledDate().atStartOfDay().atOffset(java.time.ZoneOffset.UTC) : null);

        return new OfficerAssignmentResponse(
                inspection.getUuid(),
                reference,
                applicationId,
                applicationNumber,
                title,
                address,
                propertyType,
                inspection.getInspectionType() != null ? inspection.getInspectionType() : "ROUTINE",
                status,
                scheduledAt,
                "Routine",
                "SYNCED"
        );
    }
}
