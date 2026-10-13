package ai.firesphere.operations.officer.model;

import ai.firesphere.dispatch.model.DispatchPriority;

import java.time.Instant;
import java.util.UUID;

public record OperationalAlert(
        String alertId,
        String alertType, // OVERDUE_INSPECTION, HIGH_PRIORITY_UNASSIGNED, DEADLINE_APPROACHING, TASK_ESCALATION
        UUID taskId,
        String taskReference,
        String officerBadge,
        DispatchPriority priority,
        String title,
        String message,
        Instant generatedAt,
        boolean acknowledged
) {}
