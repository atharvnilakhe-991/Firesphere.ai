package ai.firesphere.operations.officer.model;

import java.time.Instant;

/**
 * Summary metric container for an individual officer's operational load.
 */
public record OfficerWorkloadSummary(
        String officerBadge,
        int totalAssigned,
        int pendingCount,
        int inProgressCount,
        int completedCount,
        int overdueCount,
        int highPriorityCount,
        Instant evaluatedAt
) {}
