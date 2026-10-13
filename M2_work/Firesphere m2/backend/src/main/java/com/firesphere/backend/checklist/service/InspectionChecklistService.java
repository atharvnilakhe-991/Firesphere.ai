package com.firesphere.backend.checklist.service;

import com.firesphere.backend.ai.entity.AiAnalysisFinding;
import com.firesphere.backend.ai.entity.AiAnalysisRun;
import com.firesphere.backend.ai.repository.AiFindingRepository;
import com.firesphere.backend.checklist.dto.*;
import com.firesphere.backend.checklist.entity.*;
import com.firesphere.backend.checklist.repository.*;
import com.firesphere.backend.common.audit.AuditEventType;
import com.firesphere.backend.common.audit.AuditLogger;
import com.firesphere.backend.common.security.Role;
import com.firesphere.backend.common.security.TenantContext;
import com.firesphere.backend.inspection.entity.NocInspection;
import com.firesphere.backend.inspection.entity.NocInspectionEvidence;
import com.firesphere.backend.inspection.repository.NocInspectionEvidenceRepository;
import com.firesphere.backend.inspection.repository.NocInspectionRepository;
import com.firesphere.backend.noc.entity.NocApplication;
import com.firesphere.backend.noc.repository.NocApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class InspectionChecklistService {

    private final ChecklistTemplateRepository templateRepository;
    private final ChecklistTemplateItemRepository templateItemRepository;
    private final InspectionChecklistRepository checklistRepository;
    private final InspectionChecklistResponseRepository responseRepository;
    private final ChecklistFindingLinkRepository findingLinkRepository;
    private final ChecklistResponseEvidenceRepository responseEvidenceRepository;
    private final NocInspectionRepository inspectionRepository;
    private final NocInspectionEvidenceRepository evidenceRepository;
    private final AiFindingRepository aiFindingRepository;
    private final NocApplicationRepository nocRepository;
    private final AuditLogger auditLogger;

    public InspectionChecklistService(
            ChecklistTemplateRepository templateRepository,
            ChecklistTemplateItemRepository templateItemRepository,
            InspectionChecklistRepository checklistRepository,
            InspectionChecklistResponseRepository responseRepository,
            ChecklistFindingLinkRepository findingLinkRepository,
            ChecklistResponseEvidenceRepository responseEvidenceRepository,
            NocInspectionRepository inspectionRepository,
            NocInspectionEvidenceRepository evidenceRepository,
            AiFindingRepository aiFindingRepository,
            NocApplicationRepository nocRepository,
            AuditLogger auditLogger) {
        this.templateRepository = templateRepository;
        this.templateItemRepository = templateItemRepository;
        this.checklistRepository = checklistRepository;
        this.responseRepository = responseRepository;
        this.findingLinkRepository = findingLinkRepository;
        this.responseEvidenceRepository = responseEvidenceRepository;
        this.inspectionRepository = inspectionRepository;
        this.evidenceRepository = evidenceRepository;
        this.aiFindingRepository = aiFindingRepository;
        this.nocRepository = nocRepository;
        this.auditLogger = auditLogger;
    }

    public List<ChecklistTemplateResponse> getAvailableTemplates(String occupancyType, String inspectionType) {
        List<ChecklistTemplate> templates;
        if (occupancyType != null && !occupancyType.isBlank() && inspectionType != null && !inspectionType.isBlank()) {
            templates = templateRepository.findByOccupancyTypeAndInspectionTypeAndIsActiveTrue(occupancyType, inspectionType);
        } else if (occupancyType != null && !occupancyType.isBlank()) {
            templates = templateRepository.findByOccupancyTypeAndIsActiveTrue(occupancyType);
        } else {
            templates = templateRepository.findAllByIsActiveTrue();
        }
        return templates.stream().map(this::toTemplateDto).collect(Collectors.toList());
    }

    public ChecklistTemplateResponse getTemplateByUuid(UUID templateId) {
        ChecklistTemplate template = templateRepository.findByUuid(templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checklist template not found"));
        return toTemplateDto(template);
    }

    @Transactional
    public InspectionChecklistResponseDto createChecklistForInspection(UUID inspectionId, CreateInspectionChecklistRequest request) {
        NocInspection inspection = resolveInspection(inspectionId);
        verifyOfficerModificationPermission(inspection);
        Long orgId = inspection.getOrgId();

        // Idempotency: return existing checklist if already initialized
        Optional<InspectionChecklist> existing = checklistRepository.findByInspectionIdAndOrgId(inspection.getId(), orgId);
        if (existing.isPresent()) {
            return toChecklistDto(existing.get(), inspection);
        }

        ChecklistTemplate template;
        if (request != null && request.templateId() != null) {
            template = templateRepository.findByUuid(request.templateId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Specified checklist template not found"));
        } else {
            // Default: pick matching active template or fallback
            List<ChecklistTemplate> matching = templateRepository.findByOccupancyTypeAndIsActiveTrue(
                    inspection.getInspectionType() != null ? inspection.getInspectionType() : "COMMERCIAL"
            );
            if (!matching.isEmpty()) {
                template = matching.get(0);
            } else {
                List<ChecklistTemplate> allActive = templateRepository.findAllByIsActiveTrue();
                if (allActive.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No active checklist templates configured in the system");
                }
                template = allActive.get(0);
            }
        }

        InspectionChecklist checklist = new InspectionChecklist();
        checklist.setOrgId(orgId);
        checklist.setInspectionId(inspection.getId());
        checklist.setNocApplicationId(inspection.getNocApplicationId());
        checklist.setInspectionUuid(inspection.getUuid());
        checklist.setTemplate(template);
        checklist.setTemplateVersion(template.getVersion());
        checklist.setLifecycleStatus(ChecklistLifecycleStatus.DRAFT);

        List<ChecklistTemplateItem> items = templateItemRepository.findByTemplateIdOrderByItemSequenceAsc(template.getId());
        for (ChecklistTemplateItem item : items) {
            InspectionChecklistResponse response = new InspectionChecklistResponse();
            response.setChecklistInstance(checklist);
            response.setTemplateItem(item);
            response.setItemCode(item.getItemCode());
            response.setResponseStatus(ChecklistResponseStatus.NOT_REVIEWED);
            response.setRevisionNumber(1);
            checklist.addResponse(response);
        }

        checklist = checklistRepository.save(checklist);
        auditLogger.log(AuditEventType.CHECKLIST_GENERATED, checklist.getUuid(), "Inspection checklist initialized from template " + template.getChecklistCode() + " v" + template.getVersion());

        return toChecklistDto(checklist, inspection);
    }

    public InspectionChecklistResponseDto getChecklistForInspection(UUID inspectionId) {
        NocInspection inspection = resolveInspection(inspectionId);
        InspectionChecklist checklist = checklistRepository.findByInspectionIdAndOrgId(inspection.getId(), inspection.getOrgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No checklist generated for this inspection"));
        return toChecklistDto(checklist, inspection);
    }

    public InspectionChecklistItemDetailDto getChecklistItemDetail(UUID inspectionId, UUID itemId) {
        NocInspection inspection = resolveInspection(inspectionId);
        InspectionChecklist checklist = checklistRepository.findByInspectionIdAndOrgId(inspection.getId(), inspection.getOrgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checklist not found for inspection"));

        InspectionChecklistResponse response = findResponseInChecklist(checklist, itemId);
        return toItemDetailDto(response);
    }

    @Transactional
    public InspectionChecklistResponseDto recordOfficerResponse(UUID inspectionId, UUID itemId, RecordOfficerResponseRequest request) {
        NocInspection inspection = resolveInspection(inspectionId);
        verifyOfficerModificationPermission(inspection);

        InspectionChecklist checklist = checklistRepository.findByInspectionIdAndOrgId(inspection.getId(), inspection.getOrgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checklist not found for inspection"));

        if (checklist.getLifecycleStatus() == ChecklistLifecycleStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checklist has already been finalized and completed");
        }

        InspectionChecklistResponse response = findResponseInChecklist(checklist, itemId);

        if (request.responseStatus() == null || request.responseStatus().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "responseStatus is mandatory");
        }

        ChecklistResponseStatus status;
        try {
            status = ChecklistResponseStatus.valueOf(request.responseStatus().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid response status: " + request.responseStatus());
        }

        if (status == ChecklistResponseStatus.NOT_REVIEWED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot explicitly set response status to NOT_REVIEWED");
        }

        // Validation Rule 1: NON_COMPLIANT must provide violation remarks
        if (status == ChecklistResponseStatus.NON_COMPLIANT) {
            if (request.officerRemarks() == null || request.officerRemarks().trim().length() < 5) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Officer remarks explaining the deficiency are mandatory when recording a NON_COMPLIANT response");
            }
        }

        // Validation Rule 2: NOT_APPLICABLE must provide justification remarks
        if (status == ChecklistResponseStatus.NOT_APPLICABLE) {
            if (request.officerRemarks() == null || request.officerRemarks().trim().length() < 5) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Officer remarks providing statutory justification are mandatory for NOT_APPLICABLE items");
            }
        }

        // Validation Rule 3: NEEDS_FURTHER_INSPECTION must describe required investigation
        if (status == ChecklistResponseStatus.NEEDS_FURTHER_INSPECTION) {
            if (request.officerRemarks() == null || request.officerRemarks().trim().length() < 5) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Officer remarks detailing required testing or inspection are mandatory for NEEDS_FURTHER_INSPECTION");
            }
        }

        // Validation Rule 4: Mandatory evidence check
        boolean hasExistingEvidence = !response.getLinkedEvidence().isEmpty();
        boolean hasIncomingEvidence = request.linkedEvidenceIds() != null && !request.linkedEvidenceIds().isEmpty();
        if (Boolean.TRUE.equals(response.getTemplateItem().getEvidenceRequired())
                && (status == ChecklistResponseStatus.COMPLIANT || status == ChecklistResponseStatus.NON_COMPLIANT)) {
            if (!hasExistingEvidence && !hasIncomingEvidence) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Photographic evidence is mandatory for checklist item " + response.getItemCode() + " (" + response.getTemplateItem().getItemTitle() + ")");
            }
        }

        // Validation Rule 5: Response modification authorization
        Long currentInternalUserId = TenantContext.getCurrentInternalUserId();
        UUID currentUserId = TenantContext.getCurrentUserId();
        boolean isAdmin = TenantContext.hasRole(Role.ADMIN.name());
        if (response.getOfficerId() != null && currentInternalUserId != null
                && !response.getOfficerId().equals(currentInternalUserId) && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Response was recorded by officer ID " + response.getOfficerId() + "; only that officer or an administrator may update it");
        }

        response.setResponseStatus(status);
        response.setOfficerRemarks(request.officerRemarks() != null ? request.officerRemarks().trim() : null);
        response.setOfficerId(currentInternalUserId != null ? currentInternalUserId : 1L);
        response.setOfficerUuid(currentUserId);
        response.setRespondedAt(OffsetDateTime.now());
        response.setRevisionNumber(response.getRevisionNumber() != null ? response.getRevisionNumber() + 1 : 1);

        // Attach requested evidence
        if (request.linkedEvidenceIds() != null) {
            for (UUID evUuid : request.linkedEvidenceIds()) {
                NocInspectionEvidence evidence = evidenceRepository.findByUuid(evUuid)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evidence not found: " + evUuid));
                if (!evidence.getInspectionId().equals(inspection.getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evidence " + evUuid + " does not belong to this inspection");
                }
                if (!"ACTIVE".equalsIgnoreCase(evidence.getStatus())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evidence is not active or has been invalidated: " + evidence.getUuid());
                }
                boolean alreadyLinked = response.getLinkedEvidence().stream()
                        .anyMatch(link -> link.getEvidenceId().equals(evidence.getId()));
                if (!alreadyLinked) {
                    ChecklistResponseEvidence cre = new ChecklistResponseEvidence();
                    cre.setResponse(response);
                    cre.setEvidenceId(evidence.getId());
                    cre.setEvidenceUuid(evidence.getUuid());
                    response.addLinkedEvidence(cre);
                }
            }
        }

        if (checklist.getLifecycleStatus() == ChecklistLifecycleStatus.DRAFT) {
            checklist.setLifecycleStatus(ChecklistLifecycleStatus.IN_PROGRESS);
        }

        responseRepository.save(response);
        checklistRepository.save(checklist);

        auditLogger.log(AuditEventType.CHECKLIST_ITEM_UPDATED, response.getUuid(),
                "Officer updated item " + response.getItemCode() + " to " + status);

        return toChecklistDto(checklist, inspection);
    }

    @Transactional
    public InspectionChecklistItemDetailDto linkAiFindingToChecklist(UUID inspectionId, UUID itemId, LinkAiFindingRequest request) {
        NocInspection inspection = resolveInspection(inspectionId);
        verifyOfficerModificationPermission(inspection);
        InspectionChecklist checklist = checklistRepository.findByInspectionIdAndOrgId(inspection.getId(), inspection.getOrgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checklist not found for inspection"));

        if (checklist.getLifecycleStatus() == ChecklistLifecycleStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checklist has already been finalized and completed");
        }

        InspectionChecklistResponse response = findResponseInChecklist(checklist, itemId);

        AiAnalysisFinding finding = aiFindingRepository.findByUuid(request.findingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI Finding not found"));

        // Guard: Verify finding belongs to this inspection and organization (Invariant B)
        if (finding.getRun() == null || !inspection.getId().equals(finding.getRun().getInspectionId())
                || !inspection.getOrgId().equals(finding.getRun().getOrgId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI Finding does not belong to this inspection");
        }

        // Guard: Check if link already exists
        Optional<ChecklistFindingLink> existing = findingLinkRepository.findByChecklistResponseIdAndFindingId(response.getId(), finding.getId());
        if (existing.isPresent()) {
            return toItemDetailDto(response);
        }

        ChecklistFindingLink link = new ChecklistFindingLink();
        link.setChecklistResponse(response);
        link.setFindingId(finding.getId());
        link.setFindingUuid(finding.getUuid());
        link.setSuggestedCompliance(request.suggestedCompliance() != null ? request.suggestedCompliance() : "NEEDS_FURTHER_INSPECTION");
        link.setConfidence(request.confidence() != null ? request.confidence() : (finding.getConfidence() != null ? finding.getConfidence() : 0.0));
        if (request.linkProvenance() != null) {
            try {
                link.setLinkProvenance(LinkProvenance.valueOf(request.linkProvenance()));
            } catch (Exception ignored) {}
        }
        if (request.sourceProvenance() != null && !request.sourceProvenance().isBlank()) {
            link.setSourceProvenance(request.sourceProvenance());
        } else {
            link.setSourceProvenance(deriveSourceProvenance(finding.getRun()));
        }
        if (request.mappingReason() != null && !request.mappingReason().isBlank()) {
            link.setMappingReason(request.mappingReason());
        }
        link.setLinkStatus(FindingLinkStatus.SUGGESTED);
        link.setReviewStatus("PENDING_REVIEW");

        // CRITICAL INVARIANT: Creating an AI finding link NEVER alters the officer's recorded response.
        response.addAiFindingLink(link);
        findingLinkRepository.save(link);

        return toItemDetailDto(response);
    }

    @Transactional
    public InspectionChecklistItemDetailDto reviewAiFindingLink(UUID inspectionId, UUID itemId, UUID linkUuid, ReviewAiLinkRequest request) {
        NocInspection inspection = resolveInspection(inspectionId);
        verifyOfficerModificationPermission(inspection);

        InspectionChecklist checklist = checklistRepository.findByInspectionIdAndOrgId(inspection.getId(), inspection.getOrgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checklist not found for inspection"));

        if (checklist.getLifecycleStatus() == ChecklistLifecycleStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checklist has already been finalized and completed");
        }

        InspectionChecklistResponse response = findResponseInChecklist(checklist, itemId);

        ChecklistFindingLink link = findingLinkRepository.findByUuid(linkUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI finding link not found"));

        if (!link.getChecklistResponse().getId().equals(response.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link does not belong to specified checklist item");
        }

        link.setReviewStatus(request.reviewStatus());
        if ("CONFIRMED".equalsIgnoreCase(request.reviewStatus())) {
            link.setLinkStatus(FindingLinkStatus.ACCEPTED);
        } else {
            link.setLinkStatus(FindingLinkStatus.DISMISSED);
        }
        if (request.officerComment() != null) {
            link.setOfficerComment(request.officerComment().trim());
        }

        findingLinkRepository.save(link);
        auditLogger.log(AuditEventType.CHECKLIST_ITEM_UPDATED, link.getUuid(),
                "Officer reviewed AI suggestion link to status " + link.getLinkStatus());
        return toItemDetailDto(response);
    }

    @Transactional
    public AiChecklistMappingResult mapAiFindingsToChecklist(UUID inspectionId) {
        NocInspection inspection = resolveInspection(inspectionId);
        verifyOfficerModificationPermission(inspection);

        InspectionChecklist checklist = checklistRepository.findByInspectionIdAndOrgId(inspection.getId(), inspection.getOrgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checklist not found for inspection"));

        if (checklist.getLifecycleStatus() == ChecklistLifecycleStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checklist has already been finalized and completed; AI finding mappings cannot be modified");
        }

        return evaluateAndApplyMappings(inspection, checklist, true);
    }

    @Transactional(readOnly = true)
    public AiChecklistMappingResult getAiChecklistMappings(UUID inspectionId) {
        if (!TenantContext.hasRole(Role.OFFICER.name()) && !TenantContext.hasRole(Role.ADMIN.name())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only officers or administrators may access AI findings mapping");
        }
        NocInspection inspection = resolveInspection(inspectionId);
        InspectionChecklist checklist = checklistRepository.findByInspectionIdAndOrgId(inspection.getId(), inspection.getOrgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checklist not found for inspection"));

        return evaluateAndApplyMappings(inspection, checklist, false);
    }

    private AiChecklistMappingResult evaluateAndApplyMappings(NocInspection inspection, InspectionChecklist checklist, boolean persistNewLinks) {
        List<AiAnalysisFinding> findings = aiFindingRepository.findAllByInspectionIdAndOrgId(inspection.getId(), inspection.getOrgId());
        List<ChecklistFindingLink> existingLinks = findingLinkRepository.findAllByChecklistId(checklist.getId());

        // Map existing links by response ID + finding ID
        Map<String, ChecklistFindingLink> existingLinkMap = new HashMap<>();
        for (ChecklistFindingLink l : existingLinks) {
            existingLinkMap.put(l.getChecklistResponse().getId() + "_" + l.getFindingId(), l);
        }

        List<AiFindingMappingDto> mappedList = new ArrayList<>();
        List<UnmappedFindingDto> unmappedList = new ArrayList<>();
        int ambiguousCount = 0;
        int unmappedCount = 0;
        int newLinksCreated = 0;

        // Pre-detect conflicts: check if multiple findings for the same target category offer conflicting compliance suggestions
        Map<String, Set<String>> complianceByTargetTag = new HashMap<>();
        for (AiAnalysisFinding f : findings) {
            String fType = f.getFindingType() != null ? f.getFindingType().trim().toUpperCase() : "";
            String tTag = null;
            String comp = null;
            if (fType.startsWith("FIRE_EXTINGUISHER")) {
                tTag = "FIRE_EXTINGUISHER";
                comp = (fType.contains("MISSING") || fType.contains("BLOCKED") || fType.contains("EXPIRED")) ? "NON_COMPLIANT" : "NEEDS_FURTHER_INSPECTION";
            } else if (fType.startsWith("EXIT_SIGN")) {
                tTag = "EXIT_SIGN";
                comp = fType.contains("MISSING") ? "NON_COMPLIANT" : "NEEDS_FURTHER_INSPECTION";
            } else if ("EXIT_DOOR".equals(fType)) {
                tTag = "EXIT_DOOR";
                comp = "NEEDS_FURTHER_INSPECTION";
            } else if ("OBSTACLE".equals(fType)) {
                tTag = "OBSTACLE";
                comp = "NON_COMPLIANT";
            }
            if (tTag != null && comp != null) {
                complianceByTargetTag.computeIfAbsent(tTag, k -> new HashSet<>()).add(comp);
            }
        }

        for (AiAnalysisFinding finding : findings) {
            // Guard: Finding must belong to this inspection and organization
            if (finding.getRun() == null || !inspection.getId().equals(finding.getRun().getInspectionId())
                    || !inspection.getOrgId().equals(finding.getRun().getOrgId())) {
                continue;
            }

            String sourceProv = deriveSourceProvenance(finding.getRun());
            String modelName = finding.getRun().getModelName();
            String modelVersion = finding.getRun().getModelVersion();
            UUID evidenceUuid = resolveEvidenceUuid(finding);
            String rawType = finding.getFindingType() != null ? finding.getFindingType().trim().toUpperCase() : "UNKNOWN";

            // 1. Check for Ambiguous Categories
            if ("EMERGENCY_EXIT_OBSTRUCTED".equals(rawType)) {
                unmappedList.add(new UnmappedFindingDto(
                        finding.getUuid(),
                        inspection.getUuid(),
                        evidenceUuid,
                        finding.getFindingType(),
                        finding.getSeverity(),
                        finding.getConfidence(),
                        "AMBIGUOUS",
                        "Ambiguous between exit door swing clearance and corridor egress pathway; requires officer disambiguation.",
                        sourceProv,
                        modelName,
                        modelVersion
                ));
                ambiguousCount++;
                continue;
            }
            if ("COMBUSTIBLE_MATERIAL_HAZARD".equals(rawType)) {
                unmappedList.add(new UnmappedFindingDto(
                        finding.getUuid(),
                        inspection.getUuid(),
                        evidenceUuid,
                        finding.getFindingType(),
                        finding.getSeverity(),
                        finding.getConfidence(),
                        "AMBIGUOUS",
                        "Combustible hazard lacks designated statutory zone context; cannot be deterministically mapped to a specific checklist item.",
                        sourceProv,
                        modelName,
                        modelVersion
                ));
                ambiguousCount++;
                continue;
            }
            if ("GENERIC_FIRE_SAFETY_DEFECT".equals(rawType)) {
                unmappedList.add(new UnmappedFindingDto(
                        finding.getUuid(),
                        inspection.getUuid(),
                        evidenceUuid,
                        finding.getFindingType(),
                        finding.getSeverity(),
                        finding.getConfidence(),
                        "AMBIGUOUS",
                        "Generic defect lacks NBC clause specificity; requires manual officer review.",
                        sourceProv,
                        modelName,
                        modelVersion
                ));
                ambiguousCount++;
                continue;
            }

            // 2. Check for Unsupported Categories
            if ("SPRINKLER_HEAD_DEFECT".equals(rawType)) {
                unmappedList.add(new UnmappedFindingDto(
                        finding.getUuid(),
                        inspection.getUuid(),
                        evidenceUuid,
                        finding.getFindingType(),
                        finding.getSeverity(),
                        finding.getConfidence(),
                        "UNMAPPED",
                        "Active statutory checklist templates do not contain an approved sprinkler inspection clause.",
                        sourceProv,
                        modelName,
                        modelVersion
                ));
                unmappedCount++;
                continue;
            }
            if ("ELECTRICAL_PANEL_HAZARD".equals(rawType)) {
                unmappedList.add(new UnmappedFindingDto(
                        finding.getUuid(),
                        inspection.getUuid(),
                        evidenceUuid,
                        finding.getFindingType(),
                        finding.getSeverity(),
                        finding.getConfidence(),
                        "UNMAPPED",
                        "Active statutory checklist templates do not contain an approved electrical panel clause.",
                        sourceProv,
                        modelName,
                        modelVersion
                ));
                unmappedCount++;
                continue;
            }

            // 3. Evaluate Defensible Mappings
            String targetCategoryTag;
            String mappingReason;
            String suggestedCompliance;

            if (rawType.startsWith("FIRE_EXTINGUISHER")) {
                targetCategoryTag = "FIRE_EXTINGUISHER";
                mappingReason = "Statutory match for fire extinguisher availability, mounting, and maintenance per NBC 2016 Part 4.";
                suggestedCompliance = (rawType.contains("MISSING") || rawType.contains("BLOCKED") || rawType.contains("EXPIRED"))
                        ? "NON_COMPLIANT" : "NEEDS_FURTHER_INSPECTION";
            } else if (rawType.startsWith("EXIT_SIGN")) {
                targetCategoryTag = "EXIT_SIGN";
                mappingReason = "Statutory match for emergency exit signage and visibility per NBC 2016 Part 4.";
                suggestedCompliance = rawType.contains("MISSING") ? "NON_COMPLIANT" : "NEEDS_FURTHER_INSPECTION";
            } else if ("EXIT_DOOR".equals(rawType)) {
                targetCategoryTag = "EXIT_DOOR";
                mappingReason = "Statutory match for exit doorway specifications and swing direction per NBC 2016 Part 4.";
                suggestedCompliance = "NEEDS_FURTHER_INSPECTION";
            } else if ("OBSTACLE".equals(rawType)) {
                targetCategoryTag = "OBSTACLE";
                mappingReason = "Statutory match for corridor and egress pathway clearance per NBC 2016 Part 4.";
                suggestedCompliance = "NON_COMPLIANT";
            } else {
                // Any other unrecognized category
                unmappedList.add(new UnmappedFindingDto(
                        finding.getUuid(),
                        inspection.getUuid(),
                        evidenceUuid,
                        finding.getFindingType(),
                        finding.getSeverity(),
                        finding.getConfidence(),
                        "UNMAPPED",
                        "Category '" + rawType + "' does not correspond to any approved statutory fire safety requirement tag.",
                        sourceProv,
                        modelName,
                        modelVersion
                ));
                unmappedCount++;
                continue;
            }

            // Find matching response in checklist instance by aiCategoryTag
            Optional<InspectionChecklistResponse> targetResponseOpt = checklist.getResponses().stream()
                    .filter(r -> r.getTemplateItem().getAiCategoryTag() != null
                            && r.getTemplateItem().getAiCategoryTag().equalsIgnoreCase(targetCategoryTag))
                    .findFirst();

            if (targetResponseOpt.isEmpty()) {
                unmappedList.add(new UnmappedFindingDto(
                        finding.getUuid(),
                        inspection.getUuid(),
                        evidenceUuid,
                        finding.getFindingType(),
                        finding.getSeverity(),
                        finding.getConfidence(),
                        "UNMAPPED",
                        "No checklist item with tag '" + targetCategoryTag + "' found in template " + checklist.getTemplate().getChecklistCode(),
                        sourceProv,
                        modelName,
                        modelVersion
                ));
                unmappedCount++;
                continue;
            }

            InspectionChecklistResponse targetResponse = targetResponseOpt.get();
            String linkKey = targetResponse.getId() + "_" + finding.getId();
            ChecklistFindingLink link = existingLinkMap.get(linkKey);

            if (link == null) {
                if (persistNewLinks) {
                    link = new ChecklistFindingLink();
                    link.setChecklistResponse(targetResponse);
                    link.setFindingId(finding.getId());
                    link.setFindingUuid(finding.getUuid());
                    link.setSuggestedCompliance(suggestedCompliance);
                    link.setConfidence(finding.getConfidence() != null ? finding.getConfidence() : 0.0);
                    link.setLinkProvenance(LinkProvenance.AI_SUGGESTION);
                    link.setSourceProvenance(sourceProv);
                    link.setMappingReason(mappingReason);
                    link.setLinkStatus(FindingLinkStatus.SUGGESTED);
                    link.setReviewStatus("PENDING_REVIEW");

                    link = findingLinkRepository.save(link);
                    targetResponse.addAiFindingLink(link);
                    existingLinkMap.put(linkKey, link);
                    newLinksCreated++;
                } else {
                    // In read-only mode, create ephemeral representation if not yet linked
                    Set<String> compSet = complianceByTargetTag.get(targetCategoryTag);
                    String conflict = (compSet != null && compSet.size() > 1) ? "CV_VLM_CONFLICT" : null;
                    mappedList.add(new AiFindingMappingDto(
                            null,
                            finding.getUuid(),
                            inspection.getUuid(),
                            checklist.getUuid(),
                            targetResponse.getTemplateItem().getUuid(),
                            targetResponse.getItemCode(),
                            targetResponse.getTemplateItem().getItemTitle(),
                            sourceProv,
                            finding.getFindingType(),
                            finding.getSeverity(),
                            finding.getConfidence(),
                            evidenceUuid,
                            "MAPPED",
                            suggestedCompliance,
                            FindingLinkStatus.SUGGESTED.name(),
                            "PENDING_REVIEW",
                            mappingReason,
                            null,
                            modelName,
                            modelVersion,
                            conflict,
                            finding.getCreatedAt(),
                            finding.getCreatedAt()
                    ));
                    continue;
                }
            }

            Set<String> compSet = complianceByTargetTag.get(targetCategoryTag);
            String conflict = (compSet != null && compSet.size() > 1) ? "CV_VLM_CONFLICT" : null;

            mappedList.add(new AiFindingMappingDto(
                    link.getUuid(),
                    finding.getUuid(),
                    inspection.getUuid(),
                    checklist.getUuid(),
                    targetResponse.getTemplateItem().getUuid(),
                    targetResponse.getItemCode(),
                    targetResponse.getTemplateItem().getItemTitle(),
                    link.getSourceProvenance(),
                    finding.getFindingType(),
                    finding.getSeverity(),
                    link.getConfidence(),
                    evidenceUuid,
                    "MAPPED",
                    link.getSuggestedCompliance(),
                    link.getLinkStatus() != null ? link.getLinkStatus().name() : FindingLinkStatus.SUGGESTED.name(),
                    link.getReviewStatus(),
                    link.getMappingReason(),
                    link.getOfficerComment(),
                    modelName,
                    modelVersion,
                    conflict,
                    link.getCreatedAt(),
                    link.getUpdatedAt()
            ));
        }

        if (persistNewLinks && newLinksCreated > 0) {
            auditLogger.log(AuditEventType.CHECKLIST_MAPPINGS_APPLIED, checklist.getUuid(),
                    "Mapped " + mappedList.size() + " AI findings to checklist questions (" + unmappedList.size() + " unmapped, " + newLinksCreated + " new links created)");
        }

        return new AiChecklistMappingResult(
                inspection.getUuid(),
                checklist.getUuid(),
                checklist.getTemplate().getChecklistCode(),
                findings.size(),
                mappedList.size(),
                unmappedCount,
                ambiguousCount,
                mappedList,
                unmappedList
        );
    }

    private String deriveSourceProvenance(AiAnalysisRun run) {
        if (run == null) {
            return "CV";
        }
        String modelName = run.getModelName() != null ? run.getModelName().toUpperCase() : "";
        String provider = run.getModelProvider() != null ? run.getModelProvider().toUpperCase() : "";

        if (modelName.contains("RECONCIL") || modelName.contains("CROSS") || modelName.contains("ENSEMBLE")) {
            return "CROSS_VALIDATED";
        } else if (modelName.contains("VLM") || modelName.contains("QWEN") || provider.contains("VLM") || provider.contains("OLLAMA")) {
            return "VLM";
        } else if (modelName.contains("CV") || modelName.contains("YOLO") || provider.contains("CV")) {
            return "CV";
        }
        return "CV";
    }

    private UUID resolveEvidenceUuid(AiAnalysisFinding finding) {
        if (finding.getEvidenceUuid() != null) {
            return finding.getEvidenceUuid();
        }
        if (finding.getEvidenceId() != null) {
            return evidenceRepository.findById(finding.getEvidenceId())
                    .map(NocInspectionEvidence::getUuid)
                    .orElse(null);
        }
        return null;
    }

    @Transactional
    public InspectionChecklistItemDetailDto attachEvidenceToItem(UUID inspectionId, UUID itemId, AttachEvidenceRequest request) {
        NocInspection inspection = resolveInspection(inspectionId);
        verifyOfficerModificationPermission(inspection);
        InspectionChecklist checklist = checklistRepository.findByInspectionIdAndOrgId(inspection.getId(), inspection.getOrgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checklist not found for inspection"));

        if (checklist.getLifecycleStatus() == ChecklistLifecycleStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checklist has already been finalized and completed");
        }

        InspectionChecklistResponse response = findResponseInChecklist(checklist, itemId);

        NocInspectionEvidence evidence = evidenceRepository.findByUuid(request.evidenceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found"));

        if (!evidence.getInspectionId().equals(inspection.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evidence does not belong to this inspection");
        }
        if (!"ACTIVE".equalsIgnoreCase(evidence.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evidence is not active or has been invalidated: " + evidence.getUuid());
        }

        boolean alreadyLinked = response.getLinkedEvidence().stream()
                .anyMatch(link -> link.getEvidenceId().equals(evidence.getId()));
        if (!alreadyLinked) {
            ChecklistResponseEvidence cre = new ChecklistResponseEvidence();
            cre.setResponse(response);
            cre.setEvidenceId(evidence.getId());
            cre.setEvidenceUuid(evidence.getUuid());
            cre.setNotes(request.notes());
            response.addLinkedEvidence(cre);
            responseEvidenceRepository.save(cre);
        }

        return toItemDetailDto(response);
    }

    @Transactional
    public InspectionChecklistResponseDto submitChecklistCompletion(UUID inspectionId, CompleteChecklistRequest request) {
        NocInspection inspection = resolveInspection(inspectionId);
        verifyOfficerModificationPermission(inspection);

        InspectionChecklist checklist = checklistRepository.findByInspectionIdAndOrgId(inspection.getId(), inspection.getOrgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checklist not found for inspection"));

        if (checklist.getLifecycleStatus() == ChecklistLifecycleStatus.COMPLETED) {
            return toChecklistDto(checklist, inspection);
        }

        // Completion Gate 1: All mandatory items must be answered (not NOT_REVIEWED)
        List<String> unreviewedMandatory = new ArrayList<>();
        for (InspectionChecklistResponse r : checklist.getResponses()) {
            if (r.getTemplateItem().isMandatory() && r.getResponseStatus() == ChecklistResponseStatus.NOT_REVIEWED) {
                unreviewedMandatory.add(r.getItemCode() + " (" + r.getTemplateItem().getItemTitle() + ")");
            }
        }
        if (!unreviewedMandatory.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Checklist cannot be completed: required item(s) remain NOT_REVIEWED: " + String.join(", ", unreviewedMandatory));
        }

        // Completion Gate 2: Mandatory evidence items must have at least one attached evidence
        List<String> missingEvidence = new ArrayList<>();
        for (InspectionChecklistResponse r : checklist.getResponses()) {
            if (Boolean.TRUE.equals(r.getTemplateItem().getEvidenceRequired())
                    && (r.getResponseStatus() == ChecklistResponseStatus.COMPLIANT || r.getResponseStatus() == ChecklistResponseStatus.NON_COMPLIANT)
                    && r.getLinkedEvidence().isEmpty()) {
                missingEvidence.add(r.getItemCode());
            }
        }
        if (!missingEvidence.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Checklist cannot be completed: required photographic evidence missing for items: " + String.join(", ", missingEvidence));
        }

        checklist.setLifecycleStatus(ChecklistLifecycleStatus.COMPLETED);
        checklist.setCompletedAt(OffsetDateTime.now());
        checklist.setCompletedByOfficerId(TenantContext.getCurrentInternalUserId());
        checklist.setCompletedByOfficerUuid(TenantContext.getCurrentUserId());
        if (request != null && request.summaryNotes() != null) {
            checklist.setSummaryNotes(request.summaryNotes().trim());
        }

        checklist = checklistRepository.save(checklist);
        auditLogger.log(AuditEventType.CHECKLIST_COMPLETED, checklist.getUuid(), "Inspection checklist finalized and marked COMPLETED");

        return toChecklistDto(checklist, inspection);
    }

    private void verifyOfficerModificationPermission(NocInspection inspection) {
        if (!TenantContext.hasRole(Role.OFFICER.name()) && !TenantContext.hasRole(Role.ADMIN.name())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only officers or administrators may modify checklist data");
        }

        if (TenantContext.hasRole(Role.ADMIN.name())) {
            return;
        }

        UUID currentUserId = TenantContext.getCurrentUserId();
        if (inspection.getAssignedOfficer() != null && currentUserId != null) {
            if (!inspection.getAssignedOfficer().equals(currentUserId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Inspection is assigned to officer " + inspection.getAssignedOfficer() + "; only the assigned officer or an administrator may modify it");
            }
        }
    }

    private NocInspection resolveInspection(UUID inspectionId) {
        Long internalOrgId = TenantContext.getCurrentInternalOrgId();
        UUID orgUuid = TenantContext.getCurrentOrganizationId();
        NocInspection inspection = inspectionRepository.findByUuid(inspectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inspection not found"));

        if (internalOrgId != null && !inspection.getOrgId().equals(internalOrgId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant access prohibited");
        }
        if (orgUuid != null && inspection.getOrganizationId() != null && !inspection.getOrganizationId().equals(orgUuid)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant access prohibited");
        }

        // Citizen applicant ownership check: citizen may only access inspection checklists for their own applications
        if (TenantContext.hasRole(Role.CITIZEN.name())
                && !TenantContext.hasRole(Role.OFFICER.name())
                && !TenantContext.hasRole(Role.ADMIN.name())) {
            UUID currentUserId = TenantContext.getCurrentUserId();
            NocApplication noc = null;
            if (inspection.getNocApplicationId() != null) {
                noc = nocRepository.findById(inspection.getNocApplicationId()).orElse(null);
            }
            if (noc == null && inspection.getApplicationId() != null) {
                noc = nocRepository.findByUuid(inspection.getApplicationId()).orElse(null);
            }
            if (noc == null || noc.getApplicantId() == null || !noc.getApplicantId().equals(currentUserId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Citizens may only access checklist data for their own applications");
            }
        }

        return inspection;
    }

    private InspectionChecklistResponse findResponseInChecklist(InspectionChecklist checklist, UUID itemId) {
        return checklist.getResponses().stream()
                .filter(r -> r.getUuid().equals(itemId) || r.getTemplateItem().getUuid().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checklist item not found in this inspection checklist"));
    }

    private ChecklistTemplateResponse toTemplateDto(ChecklistTemplate template) {
        List<ChecklistTemplateItemResponse> itemDtos = template.getItems().stream()
                .sorted(Comparator.comparingInt(ChecklistTemplateItem::getItemSequence))
                .map(i -> new ChecklistTemplateItemResponse(
                        i.getUuid(),
                        i.getItemSequence(),
                        i.getItemCode(),
                        i.getItemTitle(),
                        i.getParameterDescription(),
                        i.getRequirementType(),
                        i.getVerificationMethod(),
                        i.getEvidenceRequired(),
                        i.getAiCategoryTag(),
                        i.getPenaltyPoints(),
                        i.getHelpGuidance()
                ))
                .collect(Collectors.toList());

        return new ChecklistTemplateResponse(
                template.getUuid(),
                template.getChecklistCode(),
                template.getTitle(),
                template.getOccupancyType(),
                template.getInspectionType(),
                template.getNbcReferenceClause(),
                template.getSourceAuthority(),
                template.getVersion(),
                template.getIsActive(),
                template.getDescription(),
                template.getItems().size(),
                template.getCreatedAt(),
                template.getUpdatedAt(),
                itemDtos
        );
    }

    private InspectionChecklistResponseDto toChecklistDto(InspectionChecklist checklist, NocInspection inspection) {
        List<InspectionChecklistItemDetailDto> itemDtos = checklist.getResponses().stream()
                .sorted(Comparator.comparingInt(r -> r.getTemplateItem().getItemSequence()))
                .map(this::toItemDetailDto)
                .collect(Collectors.toList());

        int total = checklist.getResponses().size();
        int compliant = 0;
        int nonCompliant = 0;
        int notApplicable = 0;
        int needsFurther = 0;
        int pending = 0;

        for (InspectionChecklistResponse r : checklist.getResponses()) {
            switch (r.getResponseStatus()) {
                case COMPLIANT -> compliant++;
                case NON_COMPLIANT -> nonCompliant++;
                case NOT_APPLICABLE -> notApplicable++;
                case NEEDS_FURTHER_INSPECTION -> needsFurther++;
                case NOT_REVIEWED -> pending++;
            }
        }
        int reviewed = total - pending;

        return new InspectionChecklistResponseDto(
                checklist.getUuid(),
                inspection.getUuid(),
                inspection.getApplicationId(),
                checklist.getTemplate().getUuid(),
                checklist.getTemplate().getChecklistCode(),
                checklist.getTemplate().getTitle(),
                checklist.getTemplateVersion(),
                checklist.getLifecycleStatus(),
                total,
                reviewed,
                compliant,
                nonCompliant,
                notApplicable,
                needsFurther,
                pending,
                checklist.getCreatedAt(),
                checklist.getUpdatedAt(),
                checklist.getCompletedAt(),
                checklist.getCompletedByOfficerUuid(),
                checklist.getSummaryNotes(),
                itemDtos
        );
    }

    private InspectionChecklistItemDetailDto toItemDetailDto(InspectionChecklistResponse response) {
        List<LinkedEvidenceDto> linkedEv = response.getLinkedEvidence().stream()
                .map(le -> {
                    Optional<NocInspectionEvidence> evOpt = evidenceRepository.findById(le.getEvidenceId());
                    return new LinkedEvidenceDto(
                            evOpt.map(NocInspectionEvidence::getUuid).orElse(le.getEvidenceUuid()),
                            evOpt.map(NocInspectionEvidence::getFileName).orElse("evidence.jpg"),
                            evOpt.map(NocInspectionEvidence::getContentType).orElse("image/jpeg"),
                            evOpt.map(NocInspectionEvidence::getStorageKey).orElse(""),
                            le.getNotes(),
                            le.getAttachedAt()
                    );
                })
                .collect(Collectors.toList());

        boolean isOfficerOrAdmin = TenantContext.hasRole(Role.OFFICER.name()) || TenantContext.hasRole(Role.ADMIN.name());

        List<AiSuggestionLinkDto> aiLinks;
        if (!isOfficerOrAdmin) {
            // A citizen cannot access internal officer-only findings or AI suggestions
            aiLinks = Collections.emptyList();
        } else {
            aiLinks = response.getAiFindingLinks().stream()
                    .map(link -> {
                        Optional<AiAnalysisFinding> fOpt = aiFindingRepository.findById(link.getFindingId());
                        String modelName = fOpt.map(f -> f.getRun() != null ? f.getRun().getModelName() : null).orElse(null);
                        String modelVersion = fOpt.map(f -> f.getRun() != null ? f.getRun().getModelVersion() : null).orElse(null);
                        return new AiSuggestionLinkDto(
                                link.getUuid(),
                                fOpt.map(AiAnalysisFinding::getUuid).orElse(link.getFindingUuid()),
                                fOpt.map(AiAnalysisFinding::getFindingType).orElse("OBSERVATION"),
                                fOpt.map(AiAnalysisFinding::getSeverity).orElse("MEDIUM"),
                                link.getConfidence(),
                                link.getSuggestedCompliance(),
                                link.getLinkProvenance() != null ? link.getLinkProvenance().name() : "MANUAL",
                                link.getSourceProvenance(),
                                link.getMappingReason(),
                                modelName,
                                modelVersion,
                                link.getLinkStatus() != null ? link.getLinkStatus().name() : "SUGGESTED",
                                link.getReviewStatus(),
                                link.getOfficerComment(),
                                fOpt.map(AiAnalysisFinding::getDescription).orElse(null),
                                fOpt.map(AiAnalysisFinding::getBoundingBoxJson).orElse(null),
                                link.getCreatedAt()
                        );
                    })
                    .collect(Collectors.toList());
        }

        return new InspectionChecklistItemDetailDto(
                response.getUuid(),
                response.getTemplateItem().getUuid(),
                response.getTemplateItem().getItemSequence(),
                response.getItemCode(),
                response.getTemplateItem().getItemTitle(),
                response.getTemplateItem().getParameterDescription(),
                response.getTemplateItem().getRequirementType(),
                response.getTemplateItem().getVerificationMethod(),
                response.getTemplateItem().getEvidenceRequired(),
                response.getTemplateItem().getAiCategoryTag(),
                response.getTemplateItem().getHelpGuidance(),
                response.getResponseStatus(),
                response.getOfficerRemarks(),
                response.getOfficerUuid(),
                response.getRespondedAt(),
                response.getRevisionNumber(),
                linkedEv,
                aiLinks
        );
    }
}
