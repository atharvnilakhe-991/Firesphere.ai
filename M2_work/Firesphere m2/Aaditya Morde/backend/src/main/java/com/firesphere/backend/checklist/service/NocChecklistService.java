package com.firesphere.backend.checklist.service;

import com.firesphere.backend.checklist.dto.ChecklistItemResponse;
import com.firesphere.backend.checklist.dto.ChecklistResponse;
import com.firesphere.backend.checklist.dto.GenerateChecklistRequest;
import com.firesphere.backend.checklist.dto.UpdateChecklistItemRequest;
import com.firesphere.backend.checklist.entity.ChecklistItem;
import com.firesphere.backend.checklist.entity.ChecklistStatus;
import com.firesphere.backend.checklist.entity.NocChecklist;
import com.firesphere.backend.checklist.entity.ChecklistRule;
import com.firesphere.backend.checklist.repository.NocChecklistRepository;
import com.firesphere.backend.checklist.repository.ChecklistRuleRepository;
import com.firesphere.backend.common.audit.AuditEventType;
import com.firesphere.backend.common.audit.AuditLogger;
import com.firesphere.backend.common.security.Role;
import com.firesphere.backend.common.security.TenantContext;
import com.firesphere.backend.noc.entity.NocApplication;
import com.firesphere.backend.noc.entity.NocStatus;
import com.firesphere.backend.noc.repository.NocApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NocChecklistService {

    private final NocChecklistRepository checklistRepository;
    private final ChecklistRuleRepository ruleRepository;
    private final NocApplicationRepository nocRepository;
    private final AuditLogger auditLogger;

    public NocChecklistService(NocChecklistRepository checklistRepository, ChecklistRuleRepository ruleRepository, NocApplicationRepository nocRepository, AuditLogger auditLogger) {
        this.checklistRepository = checklistRepository;
        this.ruleRepository = ruleRepository;
        this.nocRepository = nocRepository;
        this.auditLogger = auditLogger;
    }

    public ChecklistResponse generateChecklist(UUID applicationId, GenerateChecklistRequest request) {
        UUID orgId = TenantContext.getCurrentOrganizationId();
        NocApplication noc = nocRepository.findByIdAndOrganizationId(applicationId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (noc.getStatus() != NocStatus.DOCUMENTS_VERIFIED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checklist can only be generated after DOCUMENTS_VERIFIED");
        }

        // Idempotency: Return existing if already generated
        List<NocChecklist> existing = checklistRepository.findAllByApplicationIdAndOrganizationId(applicationId, orgId);
        for (NocChecklist c : existing) {
            if (c.getChecklistType().equals(request.checklistType())) {
                return toDto(c);
            }
        }

        NocChecklist checklist = new NocChecklist();
        checklist.setApplicationId(applicationId);
        checklist.setOrganizationId(orgId);
        checklist.setChecklistType(request.checklistType());

        List<ChecklistRule> rules = ruleRepository.findByChecklistTypeAndIsActiveTrue(request.checklistType());
        if (rules.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active rules found for checklist type: " + request.checklistType());
        }

        for (ChecklistRule rule : rules) {
            ChecklistItem item = new ChecklistItem();
            item.setChecklist(checklist);
            item.setCode(rule.getCode());
            item.setDescription(rule.getDescription());
            item.setRequired(rule.getRequired());
            checklist.getItems().add(item);
        }

        checklistRepository.save(checklist);
        auditLogger.log(AuditEventType.CHECKLIST_GENERATED, checklist.getId(), "Checklist generated");

        return toDto(checklist);
    }

    public List<ChecklistResponse> listChecklists(UUID applicationId) {
        UUID orgId = TenantContext.getCurrentOrganizationId();
        NocApplication noc = nocRepository.findByIdAndOrganizationId(applicationId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (!noc.getApplicantId().equals(TenantContext.getCurrentUserId()) && !TenantContext.hasRole(Role.OFFICER.name()) && !TenantContext.hasRole(Role.ADMIN.name())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to view checklists");
        }

        return checklistRepository.findAllByApplicationIdAndOrganizationId(applicationId, orgId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public ChecklistResponse getChecklist(UUID applicationId, UUID checklistId) {
        UUID orgId = TenantContext.getCurrentOrganizationId();
        NocApplication noc = nocRepository.findByIdAndOrganizationId(applicationId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (!noc.getApplicantId().equals(TenantContext.getCurrentUserId()) && !TenantContext.hasRole(Role.OFFICER.name()) && !TenantContext.hasRole(Role.ADMIN.name())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to view checklist");
        }

        return checklistRepository.findByIdAndApplicationIdAndOrganizationId(checklistId, applicationId, orgId)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checklist not found"));
    }

    public ChecklistResponse updateChecklistItem(UUID applicationId, UUID checklistId, UUID itemId, UpdateChecklistItemRequest request) {
        UUID orgId = TenantContext.getCurrentOrganizationId();
        NocChecklist checklist = checklistRepository.findByIdAndApplicationIdAndOrganizationId(checklistId, applicationId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checklist not found"));

        if (checklist.getStatus() == ChecklistStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checklist is already completed");
        }

        ChecklistItem itemToUpdate = checklist.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checklist item not found"));

        itemToUpdate.setStatus(request.status());
        itemToUpdate.setRemarks(request.remarks());
        
        checklist.setStatus(ChecklistStatus.IN_PROGRESS);

        checklistRepository.save(checklist);
        return toDto(checklist);
    }

    private ChecklistResponse toDto(NocChecklist checklist) {
        return new ChecklistResponse(
                checklist.getId(), checklist.getApplicationId(), checklist.getChecklistType(),
                checklist.getStatus(), checklist.getCreatedAt(), checklist.getUpdatedAt(),
                checklist.getItems().stream().map(this::toItemDto).collect(Collectors.toList())
        );
    }

    private ChecklistItemResponse toItemDto(ChecklistItem item) {
        return new ChecklistItemResponse(
                item.getId(), item.getCode(), item.getDescription(), item.getRequired(),
                item.getStatus(), item.getRemarks()
        );
    }
}
