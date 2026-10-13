package ai.firesphere.operations.officer.service;

import ai.firesphere.common.exception.ConflictException;
import ai.firesphere.common.exception.ResourceNotFoundException;
import ai.firesphere.common.tenant.TenantContext;
import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.operations.officer.alert.OfficerEscalationService;
import ai.firesphere.operations.officer.alert.OperationalAlertService;
import ai.firesphere.operations.officer.config.OfficerOperationsProperties;
import ai.firesphere.operations.officer.dto.*;
import ai.firesphere.operations.officer.model.*;
import ai.firesphere.operations.officer.repository.OfficerTaskRepository;
import ai.firesphere.operations.officer.security.OfficerSecurityValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class OfficerOperationsServiceImpl implements OfficerOperationsService {

    private static final Logger log = LoggerFactory.getLogger(OfficerOperationsServiceImpl.class);

    private final OfficerTaskRepository taskRepository;
    private final OperationalAlertService alertService;
    private final OfficerEscalationService escalationService;
    private final OfficerSecurityValidator securityValidator;
    private final OfficerOperationsProperties properties;
    private final Clock clock;
    private final ai.firesphere.notification.service.NotificationService notificationService;

    public OfficerOperationsServiceImpl(
            OfficerTaskRepository taskRepository,
            OperationalAlertService alertService,
            OfficerEscalationService escalationService,
            OfficerSecurityValidator securityValidator,
            OfficerOperationsProperties properties,
            Clock clock
    ) {
        this(taskRepository, alertService, escalationService, securityValidator, properties, clock, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OfficerOperationsServiceImpl(
            OfficerTaskRepository taskRepository,
            OperationalAlertService alertService,
            OfficerEscalationService escalationService,
            OfficerSecurityValidator securityValidator,
            OfficerOperationsProperties properties,
            Clock clock,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ai.firesphere.notification.service.NotificationService notificationService
    ) {
        this.taskRepository = taskRepository;
        this.alertService = alertService;
        this.escalationService = escalationService;
        this.securityValidator = securityValidator;
        this.properties = properties;
        this.clock = clock;
        this.notificationService = notificationService;
    }

    @Override
    public OfficerTaskResponseDTO createTask(OfficerTaskCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Task creation request cannot be null");
        }

        UUID tenantId = TenantContext.getRequiredTenantId();
        Instant now = clock.instant();

        if (taskRepository.findByReference(request.taskReference(), tenantId).isPresent()) {
            throw new ConflictException("Task with reference '" + request.taskReference() + "' already exists for this tenant");
        }

        if (request.scheduledAt() != null && request.deadlineAt() != null
                && request.scheduledAt().isAfter(request.deadlineAt())) {
            throw new IllegalArgumentException("scheduledAt (" + request.scheduledAt() + ") cannot be after deadlineAt (" + request.deadlineAt() + ")");
        }

        OfficerTaskStatus initialStatus = (request.assignedOfficerBadge() != null && !request.assignedOfficerBadge().isBlank())
                ? OfficerTaskStatus.ASSIGNED
                : OfficerTaskStatus.UNASSIGNED;

        OfficerTask task = new OfficerTask(
                UUID.randomUUID(),
                tenantId,
                request.taskReference().trim(),
                request.taskType() != null ? request.taskType() : OfficerTaskType.INSPECTION,
                request.title(),
                request.description(),
                request.assignedOfficerBadge(),
                request.assignedOfficerId(),
                initialStatus,
                request.priority() != null ? request.priority() : DispatchPriority.MEDIUM,
                request.scheduledAt(),
                request.deadlineAt(),
                null,
                now,
                now,
                false,
                null,
                null,
                request.checklistItems() != null ? request.checklistItems() : List.of(),
                List.of(),
                request.inspectionNotes()
        );

        OfficerTask saved = taskRepository.save(task);
        log.info("[OFFICER OPS] Created officer task: reference={}, id={}, tenant={}",
                saved.taskReference(), saved.taskId(), tenantId);

        if (notificationService != null && saved.assignedOfficerBadge() != null && !saved.assignedOfficerBadge().isBlank()) {
            try {
                notificationService.processEvent(ai.firesphere.notification.event.NotificationEvent.builder()
                        .eventType(ai.firesphere.notification.event.NotificationEventType.OFFICER_ASSIGNMENT)
                        .referenceId(saved.taskId().toString())
                        .tenantId(tenantId)
                        .channels(Set.of(ai.firesphere.notification.channel.NotificationChannel.PUSH, ai.firesphere.notification.channel.NotificationChannel.SMS))
                        .recipients(Map.of(
                                ai.firesphere.notification.channel.NotificationChannel.PUSH, "device_" + saved.assignedOfficerBadge(),
                                ai.firesphere.notification.channel.NotificationChannel.SMS, "+919876543210"
                        ))
                        .payload(Map.of(
                                "officerBadge", saved.assignedOfficerBadge(),
                                "taskId", saved.taskReference(),
                                "facilityName", saved.title()
                        ))
                        .build());
            } catch (Exception e) {
                log.warn("[OFFICER OPS] Failed to dispatch assignment notification: {}", e.getMessage());
            }
        }

        return OfficerTaskResponseDTO.fromDomain(saved, now);
    }

    @Override
    public OfficerTaskResponseDTO getTask(UUID taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("TaskId must not be null");
        }
        UUID tenantId = TenantContext.getRequiredTenantId();
        OfficerTask task = taskRepository.findById(taskId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Officer task not found: " + taskId));

        securityValidator.validateOfficerAccess(task.assignedOfficerBadge());
        return OfficerTaskResponseDTO.fromDomain(task, clock.instant());
    }

    @Override
    public OfficerTaskResponseDTO getTaskByReference(String taskReference) {
        if (taskReference == null || taskReference.isBlank()) {
            throw new IllegalArgumentException("TaskReference must not be blank");
        }
        UUID tenantId = TenantContext.getRequiredTenantId();
        OfficerTask task = taskRepository.findByReference(taskReference, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Officer task not found with reference: " + taskReference));

        securityValidator.validateOfficerAccess(task.assignedOfficerBadge());
        return OfficerTaskResponseDTO.fromDomain(task, clock.instant());
    }

    @Override
    public List<OfficerTaskResponseDTO> getTasks(
            String officerBadge,
            OfficerTaskStatus status,
            DispatchPriority priority,
            Instant fromDate,
            Instant toDate
    ) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate (" + fromDate + ") cannot be after toDate (" + toDate + ")");
        }

        if (officerBadge != null && !officerBadge.isBlank()) {
            securityValidator.validateOfficerAccess(officerBadge);
        }

        UUID tenantId = TenantContext.getRequiredTenantId();
        Instant now = clock.instant();

        List<OfficerTask> all = taskRepository.findAll(tenantId);

        // Sorting comparator: priority descending (CRITICAL -> HIGH -> MEDIUM -> LOW), then deadline ascending
        Comparator<OfficerTask> comparator = Comparator
                .comparingInt((OfficerTask t) -> priorityOrdinal(t.priority())).reversed()
                .thenComparing(t -> t.deadlineAt() != null ? t.deadlineAt() : Instant.MAX);

        return all.stream()
                .filter(t -> {
                    if (officerBadge != null && !officerBadge.isBlank()) {
                        if (t.assignedOfficerBadge() == null || !officerBadge.equalsIgnoreCase(t.assignedOfficerBadge())) {
                            return false;
                        }
                    }
                    if (status != null && t.status() != status) {
                        return false;
                    }
                    if (priority != null && t.priority() != priority) {
                        return false;
                    }
                    Instant taskTime = t.scheduledAt() != null ? t.scheduledAt() : t.createdAt();
                    if (fromDate != null && taskTime.isBefore(fromDate)) {
                        return false;
                    }
                    if (toDate != null && taskTime.isAfter(toDate)) {
                        return false;
                    }
                    return true;
                })
                .sorted(comparator)
                .map(t -> OfficerTaskResponseDTO.fromDomain(t, now))
                .toList();
    }

    @Override
    public OfficerTaskResponseDTO updateTaskStatus(UUID taskId, OfficerTaskStatusUpdateRequest request) {
        if (taskId == null || request == null || request.status() == null) {
            throw new IllegalArgumentException("TaskId and valid status are required");
        }
        UUID tenantId = TenantContext.getRequiredTenantId();
        OfficerTask task = taskRepository.findById(taskId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Officer task not found: " + taskId));

        securityValidator.validateOfficerAccess(task.assignedOfficerBadge());

        if (task.status().isTerminal()) {
            throw new IllegalStateException("Cannot update status of terminal task with status: " + task.status());
        }

        Instant now = clock.instant();
        OfficerTask updated = task.withStatus(request.status(), now);
        OfficerTask saved = taskRepository.save(updated);

        log.info("[OFFICER OPS] Task {} status transitioned from {} to {}",
                saved.taskReference(), task.status(), saved.status());

        if (notificationService != null && saved.status() == OfficerTaskStatus.COMPLETED) {
            try {
                notificationService.processEvent(ai.firesphere.notification.event.NotificationEvent.builder()
                        .eventType(ai.firesphere.notification.event.NotificationEventType.INSPECTION_REPORT_READY)
                        .referenceId(saved.taskId().toString())
                        .tenantId(tenantId)
                        .channels(Set.of(ai.firesphere.notification.channel.NotificationChannel.PUSH, ai.firesphere.notification.channel.NotificationChannel.EMAIL))
                        .recipients(Map.of(
                                ai.firesphere.notification.channel.NotificationChannel.PUSH, "device_" + (saved.assignedOfficerBadge() != null ? saved.assignedOfficerBadge() : "lead"),
                                ai.firesphere.notification.channel.NotificationChannel.EMAIL, "officer@firesphere.ai"
                        ))
                        .payload(Map.of(
                                "reportId", "RPT-" + saved.taskReference(),
                                "facilityName", saved.title(),
                                "outcome", "COMPLIANT"
                        ))
                        .build());
            } catch (Exception e) {
                log.warn("[OFFICER OPS] Failed to dispatch report ready notification: {}", e.getMessage());
            }
        }

        return OfficerTaskResponseDTO.fromDomain(saved, now);
    }

    @Override
    public OfficerTaskResponseDTO updateTaskChecklist(UUID taskId, OfficerTaskChecklistUpdateRequest request) {
        if (taskId == null || request == null) {
            throw new IllegalArgumentException("TaskId and request must not be null");
        }
        UUID tenantId = TenantContext.getRequiredTenantId();
        OfficerTask task = taskRepository.findById(taskId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Officer task not found: " + taskId));

        securityValidator.validateOfficerAccess(task.assignedOfficerBadge());

        Instant now = clock.instant();
        OfficerTask updated = task.withChecklist(request.completedChecklistItems(), request.inspectionNotes(), now);
        OfficerTask saved = taskRepository.save(updated);

        log.info("[OFFICER OPS] Updated checklist for task {}: {} completed items",
                saved.taskReference(), saved.completedChecklistItems().size());

        return OfficerTaskResponseDTO.fromDomain(saved, now);
    }

    @Override
    public List<OfficerTaskResponseDTO> getOverdueTasks() {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Instant now = clock.instant();

        return taskRepository.findAll(tenantId).stream()
                .filter(t -> t.isOverdue(now))
                .sorted(Comparator.comparing(OfficerTask::deadlineAt))
                .map(t -> OfficerTaskResponseDTO.fromDomain(t, now))
                .toList();
    }

    @Override
    public OfficerWorkloadSummaryDTO getOfficerWorkload(String officerBadge) {
        if (officerBadge == null || officerBadge.isBlank()) {
            throw new IllegalArgumentException("officerBadge must not be blank");
        }

        securityValidator.validateOfficerAccess(officerBadge);

        UUID tenantId = TenantContext.getRequiredTenantId();
        Instant now = clock.instant();

        List<OfficerTask> tasks = taskRepository.findByOfficer(officerBadge, tenantId);

        long total = tasks.size();
        long pending = tasks.stream().filter(t -> t.status() == OfficerTaskStatus.ASSIGNED).count();
        long inProgress = tasks.stream().filter(t -> t.status() == OfficerTaskStatus.IN_PROGRESS).count();
        long completed = tasks.stream().filter(t -> t.status() == OfficerTaskStatus.COMPLETED).count();
        long overdue = tasks.stream().filter(t -> t.isOverdue(now)).count();
        long highPriority = tasks.stream()
                .filter(t -> t.priority() == DispatchPriority.HIGH || t.priority() == DispatchPriority.CRITICAL)
                .count();

        OfficerWorkloadSummary summary = new OfficerWorkloadSummary(
                officerBadge, (int) total, (int) pending, (int) inProgress, (int) completed, (int) overdue, (int) highPriority, now
        );

        return OfficerWorkloadSummaryDTO.fromDomain(summary, properties.getMaxActiveTasksPerOfficer());
    }

    @Override
    public List<OperationalAlertDTO> evaluateOperationalAlerts() {
        UUID tenantId = TenantContext.getRequiredTenantId();
        List<OfficerTask> tasks = taskRepository.findAll(tenantId);
        List<OperationalAlert> alerts = alertService.evaluateAlerts(tasks);
        return alerts.stream()
                .map(OperationalAlertDTO::fromDomain)
                .toList();
    }

    @Override
    public List<EscalationResultDTO> evaluateEscalations() {
        securityValidator.validateSupervisorAccess("evaluate escalations");
        UUID tenantId = TenantContext.getRequiredTenantId();
        List<OfficerTask> tasks = taskRepository.findAll(tenantId);
        List<EscalationRecord> records = escalationService.evaluateAndEscalate(tasks);
        return records.stream()
                .map(EscalationResultDTO::fromDomain)
                .toList();
    }

    @Override
    public EscalationResultDTO manualEscalateTask(UUID taskId, String reason) {
        if (taskId == null) {
            throw new IllegalArgumentException("TaskId must not be null");
        }
        securityValidator.validateSupervisorAccess("manual task escalation");
        UUID tenantId = TenantContext.getRequiredTenantId();
        OfficerTask task = taskRepository.findById(taskId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Officer task not found: " + taskId));

        EscalationRecord record = escalationService.manualEscalate(task, reason);
        return EscalationResultDTO.fromDomain(record);
    }

    @Override
    public List<EscalationResultDTO> getEscalationHistory() {
        UUID tenantId = TenantContext.getRequiredTenantId();
        return escalationService.getEscalationHistory(tenantId).stream()
                .map(EscalationResultDTO::fromDomain)
                .toList();
    }

    private int priorityOrdinal(DispatchPriority priority) {
        if (priority == null) {
            return 0;
        }
        return switch (priority) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
            case CRITICAL -> 4;
        };
    }
}
