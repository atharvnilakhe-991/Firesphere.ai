package com.firesphere.backend.workflow.service;

import com.firesphere.backend.common.audit.AuditEventType;
import com.firesphere.backend.common.audit.AuditLogger;
import com.firesphere.backend.common.security.Role;
import com.firesphere.backend.common.security.TenantContext;
import com.firesphere.backend.noc.entity.NocApplication;
import com.firesphere.backend.noc.entity.NocStatus;
import com.firesphere.backend.noc.repository.NocApplicationRepository;
import com.firesphere.backend.workflow.dto.WorkflowActionRequest;
import com.firesphere.backend.workflow.dto.WorkflowTransitionResponse;
import com.firesphere.backend.workflow.model.WorkflowAction;
import com.firesphere.backend.common.idempotency.entity.IdempotencyRecord;
import com.firesphere.backend.common.idempotency.repository.IdempotencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.Optional;

@Service
public class WorkflowService {

    private final NocApplicationRepository nocRepository;
    private final AuditLogger auditLogger;
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public WorkflowService(NocApplicationRepository nocRepository, AuditLogger auditLogger, IdempotencyRepository idempotencyRepository, ObjectMapper objectMapper) {
        this.nocRepository = nocRepository;
        this.auditLogger = auditLogger;
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    @org.springframework.transaction.annotation.Transactional
    public WorkflowTransitionResponse performTransition(UUID applicationId, WorkflowActionRequest request, String idempotencyKey) {
        UUID orgId = TenantContext.getCurrentOrganizationId();
        UUID currentUser = TenantContext.getCurrentUserId();
        if (orgId == null || currentUser == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing context");
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String requestFingerprint = null;
            try {
                requestFingerprint = objectMapper.writeValueAsString(request);
            } catch (Exception ex) {
                // ignore
            }

            Optional<IdempotencyRecord> existing = idempotencyRepository.findByOrganizationIdAndUserIdAndIdempotencyKey(orgId, currentUser, idempotencyKey);
            if (existing.isPresent()) {
                IdempotencyRecord record = existing.get();
                if (record.getRequestFingerprint() != null && !record.getRequestFingerprint().equals(requestFingerprint)) {
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key reused for different request");
                }
                if ("COMPLETED".equals(record.getStatus()) && record.getResponseBody() != null) {
                    try {
                        return objectMapper.readValue(record.getResponseBody(), WorkflowTransitionResponse.class);
                    } catch (Exception ex) {
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading idempotent response");
                    }
                }
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Concurrent request processing");
            }

            try {
                idempotencyRepository.insertRecord(orgId, currentUser, idempotencyKey, requestFingerprint);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Concurrent request processing");
            }
        }

        WorkflowTransitionResponse response = performInternalTransition(applicationId, request.action(), request.reason(), orgId, currentUser);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            IdempotencyRecord record = idempotencyRepository.findByOrganizationIdAndUserIdAndIdempotencyKey(orgId, currentUser, idempotencyKey)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Idempotency record lost"));
            try {
                record.setResponseBody(objectMapper.writeValueAsString(response));
                record.setStatusCode(200);
                record.setStatus("COMPLETED");
                idempotencyRepository.save(record);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return response;
    }

    public WorkflowTransitionResponse performSystemTransition(UUID applicationId, WorkflowAction action, String reason) {
        return performInternalTransition(applicationId, action, reason, null, null);
    }

    private WorkflowTransitionResponse performInternalTransition(UUID applicationId, WorkflowAction action, String reason, UUID orgId, UUID currentUser) {

        Long internalOrgId = TenantContext.getCurrentInternalOrgId();
        NocApplication noc = nocRepository.findByUuid(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if ((internalOrgId != null && !noc.getOrgId().equals(internalOrgId))
                || (noc.getOrganizationId() != null && orgId != null && !noc.getOrganizationId().equals(orgId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }

        NocStatus previousState = noc.getStatus();
        NocStatus nextState = calculateNextState(noc, action, currentUser);

        noc.setStatus(nextState);
        nocRepository.save(noc);

        AuditEventType eventType = AuditEventType.NOC_WORKFLOW_TRANSITION;
        if (action == WorkflowAction.APPROVE) {
            eventType = AuditEventType.NOC_APPROVED;
        } else if (action == WorkflowAction.REJECT) {
            eventType = AuditEventType.NOC_REJECTED;
        } else if (action == WorkflowAction.REQUEST_REINSPECTION) {
            eventType = AuditEventType.REINSPECTION_REQUESTED;
        }

        auditLogger.log(eventType, noc.getUuid(), "Transitioned from " + previousState + " to " + nextState + " via " + action + (reason != null ? " Reason: " + reason : ""));

        return new WorkflowTransitionResponse(noc.getUuid(), previousState, action, nextState);
    }

    private NocStatus calculateNextState(NocApplication noc, WorkflowAction action, UUID currentUser) {
        NocStatus current = noc.getStatus();
        
        switch (action) {
            case SUBMIT:
                if (current != NocStatus.DRAFT) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only DRAFT applications can be submitted");
                }
                if (noc.getApplicantId() != null && !noc.getApplicantId().equals(currentUser)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the applicant can submit the draft");
                }
                return NocStatus.SUBMITTED;

            case START_DOCUMENT_VERIFICATION:
                if (current != NocStatus.SUBMITTED && current != NocStatus.PAYMENT_COMPLETED) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can only start verification on SUBMITTED or PAYMENT_COMPLETED applications");
                }
                if (!TenantContext.hasRole(Role.OFFICER.name()) && !TenantContext.hasRole(Role.ADMIN.name())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only officers can start document verification");
                }
                return NocStatus.DOCUMENT_VERIFICATION;
                
            case COMPLETE_DOCUMENT_VERIFICATION:
                if (current != NocStatus.DOCUMENT_VERIFICATION) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can only complete verification from DOCUMENT_VERIFICATION state");
                }
                if (!TenantContext.hasRole(Role.OFFICER.name()) && !TenantContext.hasRole(Role.ADMIN.name())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only officers can complete document verification");
                }
                return NocStatus.DOCUMENTS_VERIFIED;

            case REQUEST_CORRECTION:
                if (current != NocStatus.DOCUMENT_VERIFICATION) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Corrections can only be requested during DOCUMENT_VERIFICATION");
                }
                if (!TenantContext.hasRole(Role.OFFICER.name()) && !TenantContext.hasRole(Role.ADMIN.name())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only officers can request corrections");
                }
                return NocStatus.CORRECTION_REQUIRED;

            case RESUBMIT:
                if (current != NocStatus.CORRECTION_REQUIRED) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only applications requiring correction can be resubmitted");
                }
                if (noc.getApplicantId() != null && !noc.getApplicantId().equals(currentUser)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the applicant can resubmit the application");
                }
                return NocStatus.DOCUMENT_VERIFICATION;

            case GENERATE_CHECKLIST:
                if (current != NocStatus.DOCUMENTS_VERIFIED) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checklist can only be generated after DOCUMENTS_VERIFIED");
                }
                if (!TenantContext.hasRole(Role.OFFICER.name()) && !TenantContext.hasRole(Role.ADMIN.name())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only officers can generate checklists");
                }
                return NocStatus.CHECKLIST_GENERATED;

            case SCHEDULE_INSPECTION:
                if (current != NocStatus.CHECKLIST_GENERATED && current != NocStatus.REINSPECTION_REQUIRED) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inspection can only be scheduled after checklist generation or reinspection request");
                }
                if (!TenantContext.hasRole(Role.OFFICER.name()) && !TenantContext.hasRole(Role.ADMIN.name())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only officers can schedule inspections");
                }
                return NocStatus.INSPECTION_SCHEDULED;
                
            case START_INSPECTION:
                if (current != NocStatus.INSPECTION_SCHEDULED) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can only start inspection from INSPECTION_SCHEDULED state");
                }
                if (!TenantContext.hasRole(Role.OFFICER.name()) && !TenantContext.hasRole(Role.ADMIN.name())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only officers can start inspections");
                }
                return NocStatus.INSPECTION_IN_PROGRESS;
                
            case COMPLETE_INSPECTION:
                if (current != NocStatus.INSPECTION_IN_PROGRESS) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can only complete inspection from INSPECTION_IN_PROGRESS state");
                }
                if (!TenantContext.hasRole(Role.OFFICER.name()) && !TenantContext.hasRole(Role.ADMIN.name())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only officers can complete inspections");
                }
                return NocStatus.INSPECTION_COMPLETED;

            case REQUEST_REINSPECTION:
                if (current != NocStatus.INSPECTION_COMPLETED) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can only request reinspection from INSPECTION_COMPLETED state");
                }
                if (!TenantContext.hasRole(Role.OFFICER.name()) && !TenantContext.hasRole(Role.ADMIN.name())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only officers can request reinspection");
                }
                return NocStatus.REINSPECTION_REQUIRED;

            case SUBMIT_FOR_APPROVAL:
                if (current != NocStatus.INSPECTION_COMPLETED) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can only submit for approval from INSPECTION_COMPLETED state");
                }
                if (!TenantContext.hasRole(Role.OFFICER.name()) && !TenantContext.hasRole(Role.ADMIN.name())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only officers can submit for approval");
                }
                return NocStatus.PENDING_APPROVAL;

            case APPROVE:
                if (current != NocStatus.PENDING_APPROVAL) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can only approve applications in PENDING_APPROVAL state");
                }
                if (!TenantContext.hasPermission("noc:approve")) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User lacks noc:approve permission");
                }
                return NocStatus.APPROVED;

            case REJECT:
                if (current != NocStatus.PENDING_APPROVAL) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can only reject applications in PENDING_APPROVAL state");
                }
                if (!TenantContext.hasPermission("noc:reject")) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User lacks noc:reject permission");
                }
                // No permanent rejection state in the workflow contract currently, map to CORRECTION_REQUIRED
                return NocStatus.CORRECTION_REQUIRED;

            case GENERATE_CERTIFICATE:
                if (current != NocStatus.APPROVED && current != NocStatus.CERTIFICATE_GENERATED) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can only generate certificate for APPROVED applications");
                }
                if (currentUser != null && !TenantContext.hasPermission("noc:certificate:generate")) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User lacks noc:certificate:generate permission");
                }
                return NocStatus.CERTIFICATE_GENERATED;

            case INITIATE_PAYMENT:
                if (current != NocStatus.SUBMITTED) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment can only be initiated from SUBMITTED");
                }
                if (noc.getApplicantId() != null && !noc.getApplicantId().equals(currentUser)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the applicant can initiate payment");
                }
                return NocStatus.PAYMENT_PENDING;

            case COMPLETE_PAYMENT:
                if (current != NocStatus.PAYMENT_PENDING) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can only complete payment from PAYMENT_PENDING");
                }
                if (currentUser != null) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "COMPLETE_PAYMENT is a system-only transition");
                }
                return NocStatus.PAYMENT_COMPLETED;

            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported workflow action: " + action);
        }
    }
}
