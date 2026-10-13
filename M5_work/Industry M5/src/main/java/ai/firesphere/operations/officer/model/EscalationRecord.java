package ai.firesphere.operations.officer.model;

import java.time.Instant;
import java.util.UUID;

public record EscalationRecord(
        String escalationId,
        UUID taskId,
        String taskReference,
        String previousOfficerBadge,
        String escalatedToRole,
        String reason,
        Instant escalatedAt
) {}
