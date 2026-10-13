package ai.firesphere.operations.officer.dto;

import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.operations.officer.model.OfficerTask;
import ai.firesphere.operations.officer.model.OfficerTaskStatus;
import ai.firesphere.operations.officer.model.OfficerTaskType;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record OfficerTaskResponseDTO(
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
        boolean isOverdue,
        boolean isEscalated,
        String escalationReason,
        Instant escalatedAt,
        List<String> checklistItems,
        List<String> completedChecklistItems,
        String inspectionNotes
) {

    public OfficerTaskResponseDTO {
        checklistItems = checklistItems != null ? Collections.unmodifiableList(checklistItems) : List.of();
        completedChecklistItems = completedChecklistItems != null ? Collections.unmodifiableList(completedChecklistItems) : List.of();
    }

    public OfficerTaskResponseDTO(
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
            boolean isOverdue,
            boolean isEscalated,
            String escalationReason,
            Instant escalatedAt
    ) {
        this(
                taskId, tenantId, taskReference, taskType, title, description,
                assignedOfficerBadge, assignedOfficerId, status, priority,
                scheduledAt, deadlineAt, completedAt, createdAt, updatedAt,
                isOverdue, isEscalated, escalationReason, escalatedAt,
                List.of(), List.of(), null
        );
    }

    public static OfficerTaskResponseDTO fromDomain(OfficerTask task, Instant now) {
        if (task == null) {
            return null;
        }
        Instant current = (now != null) ? now : Instant.now();
        return new OfficerTaskResponseDTO(
                task.taskId(),
                task.tenantId(),
                task.taskReference(),
                task.taskType(),
                task.title(),
                task.description(),
                task.assignedOfficerBadge(),
                task.assignedOfficerId(),
                task.status(),
                task.priority(),
                task.scheduledAt(),
                task.deadlineAt(),
                task.completedAt(),
                task.createdAt(),
                task.updatedAt(),
                task.isOverdue(current),
                task.escalated(),
                task.escalationReason(),
                task.escalatedAt(),
                task.checklistItems() != null ? task.checklistItems() : List.of(),
                task.completedChecklistItems() != null ? task.completedChecklistItems() : List.of(),
                task.inspectionNotes()
        );
    }
}
