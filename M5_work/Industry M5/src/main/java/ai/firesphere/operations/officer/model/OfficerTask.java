package ai.firesphere.operations.officer.model;

import ai.firesphere.dispatch.model.DispatchPriority;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model representing an assigned operational task or fire safety inspection.
 */
public record OfficerTask(
        UUID taskId,
        UUID tenantId,
        String taskReference,
        OfficerTaskType taskType,
        String title,
        String description,
        String assignedOfficerBadge,
        UUID assignedOfficerId,
        OfficerTaskStatus status,
        DispatchPriority priority,
        Instant scheduledAt,
        Instant deadlineAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt,
        boolean escalated,
        String escalationReason,
        Instant escalatedAt,
        List<String> checklistItems,
        List<String> completedChecklistItems,
        String inspectionNotes
) {

    public OfficerTask {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(taskReference, "taskReference must not be null");
        if (taskType == null) {
            taskType = OfficerTaskType.INSPECTION;
        }
        if (status == null) {
            status = (assignedOfficerBadge != null && !assignedOfficerBadge.isBlank())
                    ? OfficerTaskStatus.ASSIGNED
                    : OfficerTaskStatus.UNASSIGNED;
        }
        if (priority == null) {
            priority = DispatchPriority.MEDIUM;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        checklistItems = checklistItems != null ? Collections.unmodifiableList(checklistItems) : List.of();
        completedChecklistItems = completedChecklistItems != null ? Collections.unmodifiableList(completedChecklistItems) : List.of();
    }

    public OfficerTask(
            UUID taskId,
            UUID tenantId,
            String taskReference,
            OfficerTaskType taskType,
            String title,
            String description,
            String assignedOfficerBadge,
            UUID assignedOfficerId,
            OfficerTaskStatus status,
            DispatchPriority priority,
            Instant scheduledAt,
            Instant deadlineAt,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt,
            boolean escalated,
            String escalationReason,
            Instant escalatedAt
    ) {
        this(
                taskId, tenantId, taskReference, taskType, title, description,
                assignedOfficerBadge, assignedOfficerId, status, priority,
                scheduledAt, deadlineAt, completedAt, createdAt, updatedAt,
                escalated, escalationReason, escalatedAt, List.of(), List.of(), null
        );
    }

    public boolean isOverdue(Instant currentInstant) {
        if (status != null && status.isTerminal()) {
            return false;
        }
        return deadlineAt != null && currentInstant.isAfter(deadlineAt);
    }

    public boolean isApproachingDeadline(Instant currentInstant, long thresholdMinutes) {
        if (status != null && status.isTerminal()) {
            return false;
        }
        if (deadlineAt == null) {
            return false;
        }
        if (currentInstant.isAfter(deadlineAt)) {
            return false; // already overdue
        }
        Instant thresholdInstant = currentInstant.plusSeconds(thresholdMinutes * 60);
        return deadlineAt.isBefore(thresholdInstant);
    }

    public OfficerTask withStatus(OfficerTaskStatus newStatus, Instant timestamp) {
        Instant completed = (newStatus == OfficerTaskStatus.COMPLETED)
                ? (timestamp != null ? timestamp : Instant.now())
                : this.completedAt;
        return new OfficerTask(
                taskId, tenantId, taskReference, taskType, title, description,
                assignedOfficerBadge, assignedOfficerId, newStatus, priority,
                scheduledAt, deadlineAt, completed, createdAt,
                timestamp != null ? timestamp : Instant.now(),
                escalated, escalationReason, escalatedAt,
                checklistItems, completedChecklistItems, inspectionNotes
        );
    }

    public OfficerTask withEscalation(String reason, Instant timestamp) {
        return new OfficerTask(
                taskId, tenantId, taskReference, taskType, title, description,
                assignedOfficerBadge, assignedOfficerId, OfficerTaskStatus.ESCALATED, priority,
                scheduledAt, deadlineAt, completedAt, createdAt,
                timestamp != null ? timestamp : Instant.now(),
                true, reason, timestamp != null ? timestamp : Instant.now(),
                checklistItems, completedChecklistItems, inspectionNotes
        );
    }

    public OfficerTask withAssignment(String officerBadge, UUID officerId, Instant timestamp) {
        OfficerTaskStatus newStatus = (status == OfficerTaskStatus.UNASSIGNED)
                ? OfficerTaskStatus.ASSIGNED
                : status;
        return new OfficerTask(
                taskId, tenantId, taskReference, taskType, title, description,
                officerBadge, officerId, newStatus, priority,
                scheduledAt, deadlineAt, completedAt, createdAt,
                timestamp != null ? timestamp : Instant.now(),
                escalated, escalationReason, escalatedAt,
                checklistItems, completedChecklistItems, inspectionNotes
        );
    }

    public OfficerTask withChecklist(List<String> completedItems, String notes, Instant timestamp) {
        return new OfficerTask(
                taskId, tenantId, taskReference, taskType, title, description,
                assignedOfficerBadge, assignedOfficerId, status, priority,
                scheduledAt, deadlineAt, completedAt, createdAt,
                timestamp != null ? timestamp : Instant.now(),
                escalated, escalationReason, escalatedAt,
                checklistItems,
                completedItems != null ? List.copyOf(completedItems) : completedChecklistItems,
                notes != null ? notes : inspectionNotes
        );
    }
}
