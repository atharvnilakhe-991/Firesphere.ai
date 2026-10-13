package ai.firesphere.operations.officer.alert;

import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.notification.channel.NotificationChannel;
import ai.firesphere.notification.event.NotificationEvent;
import ai.firesphere.notification.event.NotificationEventType;
import ai.firesphere.notification.model.NotificationPriority;
import ai.firesphere.notification.service.NotificationService;
import ai.firesphere.operations.officer.config.OfficerOperationsProperties;
import ai.firesphere.operations.officer.model.EscalationRecord;
import ai.firesphere.operations.officer.model.OfficerTask;
import ai.firesphere.operations.officer.model.OfficerTaskStatus;
import ai.firesphere.operations.officer.model.OperationalAlert;
import ai.firesphere.operations.officer.repository.OfficerTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service evaluating and executing operational task escalations.
 * Escalates overdue tasks exceeding threshold limits or stale high-priority tasks
 * to supervisory roles (e.g. STATION_CAPTAIN) and prevents duplicate escalations.
 */
@Service
public class OfficerEscalationService {

    private static final Logger log = LoggerFactory.getLogger(OfficerEscalationService.class);

    private final OfficerTaskRepository taskRepository;
    private final OfficerOperationsProperties properties;
    private final Clock clock;
    private final NotificationService notificationService;

    // In-memory audit of escalation records: tenantId -> list of EscalationRecord
    private final Map<UUID, List<EscalationRecord>> escalationHistory = new ConcurrentHashMap<>();

    public OfficerEscalationService(
            OfficerTaskRepository taskRepository,
            OfficerOperationsProperties properties,
            Clock clock,
            @Autowired(required = false) NotificationService notificationService
    ) {
        this.taskRepository = taskRepository;
        this.properties = properties;
        this.clock = clock;
        this.notificationService = notificationService;
    }

    /**
     * Evaluates tasks for a tenant and escalates any eligible tasks.
     *
     * @param tasks the list of tasks to evaluate
     * @return list of newly created EscalationRecords
     */
    public List<EscalationRecord> evaluateAndEscalate(List<OfficerTask> tasks) {
        if (tasks == null || tasks.isEmpty() || !properties.isAutoEscalationEnabled()) {
            return Collections.emptyList();
        }

        Instant now = clock.instant();
        List<EscalationRecord> newEscalations = new ArrayList<>();

        for (OfficerTask task : tasks) {
            if (shouldEscalate(task, now)) {
                String reason = determineEscalationReason(task, now);
                EscalationRecord record = escalateTask(task, reason, now);
                newEscalations.add(record);
            }
        }

        return newEscalations;
    }

    /**
     * Determines if a task is eligible for escalation:
     * - Must not already be escalated
     * - Must not be in terminal state (COMPLETED or CANCELLED)
     * - Either:
     *    a) High-priority (HIGH/CRITICAL) and overdue by >= highPriorityEscalationMinutes
     *    b) Overdue by >= overdueEscalationThresholdMinutes
     *    c) High-priority unassigned and created >= highPriorityEscalationMinutes ago
     */
    public boolean shouldEscalate(OfficerTask task, Instant now) {
        if (task == null || task.escalated() || task.status() == OfficerTaskStatus.ESCALATED) {
            return false;
        }
        if (task.status() != null && task.status().isTerminal()) {
            return false;
        }

        boolean isHighPriority = task.priority() == DispatchPriority.HIGH || task.priority() == DispatchPriority.CRITICAL;

        // Check overdue conditions
        if (task.deadlineAt() != null && now.isAfter(task.deadlineAt())) {
            long overdueMinutes = Duration.between(task.deadlineAt(), now).toMinutes();
            if (isHighPriority && overdueMinutes >= properties.getHighPriorityEscalationMinutes()) {
                return true;
            }
            if (overdueMinutes >= properties.getOverdueEscalationThresholdMinutes()) {
                return true;
            }
        }

        // Check high priority unassigned staleness
        if (isHighPriority && task.status() == OfficerTaskStatus.UNASSIGNED && task.createdAt() != null) {
            long unassignedMinutes = Duration.between(task.createdAt(), now).toMinutes();
            if (unassignedMinutes >= properties.getHighPriorityEscalationMinutes()) {
                return true;
            }
        }

        return false;
    }

    public EscalationRecord manualEscalate(OfficerTask task, String customReason) {
        if (task.escalated() || task.status() == OfficerTaskStatus.ESCALATED) {
            throw new IllegalArgumentException("Task " + task.taskReference() + " is already escalated.");
        }
        if (task.status() != null && task.status().isTerminal()) {
            throw new IllegalStateException("Cannot escalate task in terminal state: " + task.status());
        }
        Instant now = clock.instant();
        String reason = (customReason != null && !customReason.isBlank())
                ? customReason
                : "Manual operational escalation";
        return escalateTask(task, reason, now);
    }

    private EscalationRecord escalateTask(OfficerTask task, String reason, Instant now) {
        String supervisorRole = properties.getDefaultEscalatedRole();
        EscalationRecord record = new EscalationRecord(
                "ESC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                task.taskId(),
                task.taskReference(),
                task.assignedOfficerBadge(),
                supervisorRole,
                reason,
                now
        );

        // Update task state and persist
        OfficerTask updatedTask = task.withEscalation(reason, now);
        taskRepository.save(updatedTask);

        // Record escalation history
        escalationHistory.computeIfAbsent(task.tenantId(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(record);

        log.warn("[OFFICER ESCALATION] Task {} escalated to {} (reason: {})",
                task.taskReference(), supervisorRole, reason);

        // Dispatch alert / notification
        dispatchEscalationNotification(updatedTask, record);

        return record;
    }

    private String determineEscalationReason(OfficerTask task, Instant now) {
        if (task.deadlineAt() != null && now.isAfter(task.deadlineAt())) {
            long overdueMinutes = Duration.between(task.deadlineAt(), now).toMinutes();
            return String.format("Task overdue by %d minutes (threshold: %d min)",
                    overdueMinutes,
                    (task.priority() == DispatchPriority.HIGH || task.priority() == DispatchPriority.CRITICAL)
                            ? properties.getHighPriorityEscalationMinutes()
                            : properties.getOverdueEscalationThresholdMinutes());
        }
        if (task.status() == OfficerTaskStatus.UNASSIGNED) {
            return String.format("High-priority task remained unassigned for more than %d minutes",
                    properties.getHighPriorityEscalationMinutes());
        }
        return "Operational SLA threshold breach";
    }

    private void dispatchEscalationNotification(OfficerTask task, EscalationRecord record) {
        if (notificationService == null) {
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("escalationId", record.escalationId());
            payload.put("taskReference", record.taskReference());
            payload.put("escalatedToRole", record.escalatedToRole());
            payload.put("alertTitle", "Task Escalation: " + record.taskReference());
            payload.put("severity", "CRITICAL");
            payload.put("message", record.reason());

            Map<NotificationChannel, String> recipients = new HashMap<>();
            recipients.put(NotificationChannel.PUSH, "role-" + record.escalatedToRole().toLowerCase());

            NotificationEvent event = NotificationEvent.builder()
                    .eventType(NotificationEventType.SYSTEM_ALERT)
                    .referenceId(record.escalationId())
                    .tenantId(task.tenantId())
                    .channels(Set.of(NotificationChannel.PUSH))
                    .recipients(recipients)
                    .payload(payload)
                    .priority(NotificationPriority.CRITICAL)
                    .build();

            notificationService.processEvent(event);
        } catch (Exception e) {
            log.warn("[OFFICER ESCALATION] Failed to dispatch notification for escalation {}: {}",
                    record.escalationId(), e.getMessage());
        }
    }

    public List<EscalationRecord> getEscalationHistory(UUID tenantId) {
        if (tenantId == null) {
            return Collections.emptyList();
        }
        var list = escalationHistory.get(tenantId);
        return list == null ? Collections.emptyList() : new ArrayList<>(list);
    }

    public void clearHistory() {
        escalationHistory.clear();
    }
}
