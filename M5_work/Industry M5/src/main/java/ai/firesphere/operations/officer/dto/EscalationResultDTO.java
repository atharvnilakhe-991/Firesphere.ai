package ai.firesphere.operations.officer.dto;

import ai.firesphere.operations.officer.model.EscalationRecord;

import java.time.Instant;
import java.util.UUID;

public record EscalationResultDTO(
        String escalationId,
        UUID taskId,
        String taskReference,
        String previousOfficerBadge,
        String escalatedToRole,
        String reason,
        Instant escalatedAt
) {
    public static EscalationResultDTO fromDomain(EscalationRecord record) {
        if (record == null) {
            return null;
        }
        return new EscalationResultDTO(
                record.escalationId(),
                record.taskId(),
                record.taskReference(),
                record.previousOfficerBadge(),
                record.escalatedToRole(),
                record.reason(),
                record.escalatedAt()
        );
    }
}
