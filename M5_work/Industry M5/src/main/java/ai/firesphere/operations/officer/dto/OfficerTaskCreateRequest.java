package ai.firesphere.operations.officer.dto;

import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.operations.officer.model.OfficerTaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record OfficerTaskCreateRequest(
        @NotBlank(message = "taskReference is mandatory")
        @Size(max = 64, message = "taskReference cannot exceed 64 characters")
        String taskReference,

        OfficerTaskType taskType,

        @NotBlank(message = "title is mandatory")
        @Size(max = 200, message = "title cannot exceed 200 characters")
        String title,

        @Size(max = 2000, message = "description cannot exceed 2000 characters")
        String description,

        @Size(max = 64, message = "assignedOfficerBadge cannot exceed 64 characters")
        String assignedOfficerBadge,

        UUID assignedOfficerId,

        DispatchPriority priority,

        Instant scheduledAt,

        Instant deadlineAt,

        List<String> checklistItems,

        String inspectionNotes
) {

    public OfficerTaskCreateRequest {
        checklistItems = checklistItems != null ? Collections.unmodifiableList(checklistItems) : List.of();
    }

    public OfficerTaskCreateRequest(
            String taskReference,
            OfficerTaskType taskType,
            String title,
            String description,
            String assignedOfficerBadge,
            UUID assignedOfficerId,
            DispatchPriority priority,
            Instant scheduledAt,
            Instant deadlineAt
    ) {
        this(taskReference, taskType, title, description, assignedOfficerBadge, assignedOfficerId, priority, scheduledAt, deadlineAt, List.of(), null);
    }
}
