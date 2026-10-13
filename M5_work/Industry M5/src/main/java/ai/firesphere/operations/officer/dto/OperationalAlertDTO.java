package ai.firesphere.operations.officer.dto;

import ai.firesphere.dispatch.model.DispatchPriority;
import ai.firesphere.operations.officer.model.OperationalAlert;

import java.time.Instant;
import java.util.UUID;

public record OperationalAlertDTO(
        String alertId,
        String alertType,
        UUID taskId,
        String taskReference,
        String officerBadge,
        DispatchPriority priority,
        String title,
        String message,
        Instant generatedAt,
        boolean acknowledged
) {
    public static OperationalAlertDTO fromDomain(OperationalAlert alert) {
        if (alert == null) {
            return null;
        }
        return new OperationalAlertDTO(
                alert.alertId(),
                alert.alertType(),
                alert.taskId(),
                alert.taskReference(),
                alert.officerBadge(),
                alert.priority(),
                alert.title(),
                alert.message(),
                alert.generatedAt(),
                alert.acknowledged()
        );
    }
}
